package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.ReservaCredito;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Motor de pujas de HU-SUB-004. Deliberadamente no conoce repositorios JPA:
 * recibe la Subasta, la puja vigente (si hay) y el ContextoParticipacion ya
 * resueltos por el caller. Esto permite probar las 5 reglas de negocio y la
 * concurrencia sin depender de que exista la tabla Subastas de Edwin ni el
 * endpoint real de ms-finanzas de Juan Diego.
 *
 * Concurrencia: esta clase no se sincroniza a si misma a proposito. Solo
 * muta los objetos que recibe por parametro, y el guardia de la carrera es
 * el lock pesimista sobre la fila de Subasta que toma
 * PujaApplicationService dentro de su transaccion. Sincronizar aqui
 * serializaria pujas de subastas distintas sin necesidad.
 */
@Service
public class MotorPujasService {

    private static final Logger log = LoggerFactory.getLogger(MotorPujasService.class);

    private final CreditoClient creditoClient;
    private final InventarioClient inventarioClient;
    private final Clock clock;
    private final ParametrosPuja parametros;

    public MotorPujasService(CreditoClient creditoClient, Clock clock, ParametrosPuja parametros) {
        this(creditoClient, new InventarioClientFake(), clock, parametros);
    }

    @Autowired
    public MotorPujasService(CreditoClient creditoClient,
                             InventarioClient inventarioClient,
                             Clock clock,
                             ParametrosPuja parametros) {
        this.creditoClient = Objects.requireNonNull(creditoClient, "creditoClient no puede ser nulo");
        this.inventarioClient = Objects.requireNonNull(inventarioClient, "inventarioClient no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
        this.parametros = Objects.requireNonNull(parametros, "parametros no puede ser nulo");
    }

    /**
     * @param idempotencyKey clave que debe venir del cliente (cabecera
     *                       Idempotency-Key). NO se genera aqui a proposito: si
     *                       se derivara del reloj, un reintento por timeout
     *                       produciria una clave distinta y reservaria los
     *                       creditos dos veces, que es justo lo que la clave
     *                       debe evitar.
     */
    public Puja pujar(Subasta subasta, Puja pujaVigente, UUID jugadorId, BigDecimal monto,
                      ContextoParticipacion contexto, String idempotencyKey, TipoPuja tipo) {
        validarReglasDeParticipacion(subasta, jugadorId, monto, contexto);

        ReservaCredito reserva = creditoClient.reservar(jugadorId, monto, subasta.getId(), idempotencyKey);

        if (pujaVigente != null) {
            creditoClient.liberar(UUID.fromString(pujaVigente.getReservaCreditoId()));
            pujaVigente.setEstado(EstadoPuja.SUPERADA);
        }

        subasta.setOfertaVigente(monto);
        subasta.setMejorPostorId(jugadorId);
        // cantidadPujas es de HU-SUB-011 (Cristian): el listado lo muestra y
        // permite ordenar por el. Este motor es el unico sitio del servicio que
        // crea pujas, asi que si no se incrementa aqui el contador se queda en 0
        // para siempre y "ordenar por pujas" no ordena nada.
        subasta.setCantidadPujas(subasta.getCantidadPujas() + 1);

        // id nulo a proposito: lo genera la base de datos (@GeneratedValue). Si
        // el dominio lo asignara, Spring Data veria una entidad con id y haria
        // merge (UPDATE de una fila inexistente) en vez de persist.
        Puja nueva = new Puja(null, subasta.getId(), jugadorId, monto, tipo,
                EstadoPuja.ACTIVA, clock.instant(), reserva.id().toString());
        nueva.setIdempotencyKey(idempotencyKey);
        return nueva;
    }

    /**
     * @param idempotencyKey clave del cliente (cabecera Idempotency-Key), igual
     *                       que en {@link #pujar}. Antes se derivaba aqui de
     *                       (jugador, subasta), que protegia incluso frente a un
     *                       cliente que reintentara con una clave distinta; el
     *                       contrato la declara obligatoria, asi que manda la
     *                       del cliente. Un reintento con esa misma clave ya
     *                       no llega hasta aqui: PujaApplicationService
     *                       encuentra la compra original por la clave y la
     *                       devuelve tal cual, en vez de dejar que la subasta
     *                       —ya ADJUDICADA— lo rechace con SUBASTA_NO_ACTIVA y
     *                       el comprador se quede sin saber si compro.
     */
    public Puja comprarAhora(Subasta subasta, Puja pujaVigente, UUID jugadorId, String idempotencyKey) {
        if (!subasta.estaActiva()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta " + subasta.getId() + " no esta activa");
        }
        if (subasta.esVendedor(jugadorId)) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.PUJA_PROPIA,
                    "El jugador " + jugadorId + " no puede comprar en su propia subasta");
        }
        if (subasta.getPrecioCompraInmediata() == null) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SIN_COMPRA_INMEDIATA,
                    "La subasta " + subasta.getId() + " no ofrece compra inmediata");
        }

        BigDecimal precio = subasta.getPrecioCompraInmediata();
        ReservaCredito reserva = creditoClient.reservar(jugadorId, precio, subasta.getId(), idempotencyKey);

        boolean transferido = false;
        try {
            if (tieneInventario(subasta)) {
                inventarioClient.transferirProducto(subasta.getElementoInventarioId(), jugadorId,
                        subasta.getId(), idempotencyKey);
                transferido = true;
            }
            creditoClient.consumir(reserva.id(), subasta.getVendedorId());
        } catch (RuntimeException e) {
            if (transferido) {
                devolverProductoAlVendedor(subasta, idempotencyKey);
            }
            try {
                creditoClient.liberar(reserva.id());
            } catch (RuntimeException ignored) {
            }
            throw e;
        }

        // El postor vigente queda superado por la compra inmediata, asi que sus
        // creditos se restituyen igual que en una puja normal. Solo hay una
        // reserva que liberar: a los postores anteriores ya se les libero al
        // ser superados (lo garantiza el unico parcial de una puja ACTIVA).
        if (pujaVigente != null) {
            creditoClient.liberar(UUID.fromString(pujaVigente.getReservaCreditoId()));
            pujaVigente.setEstado(EstadoPuja.SUPERADA);
        }

        subasta.setOfertaVigente(precio);
        subasta.setMejorPostorId(jugadorId);
        subasta.setCantidadPujas(subasta.getCantidadPujas() + 1);
        subasta.setEstado(EstadoSubasta.ADJUDICADA);

        // El dinero ya cambio de manos: desde aqui no se compensa nunca. Lo
        // ultimo que queda es soltarle el bloqueo al objeto para que el
        // comprador pueda usarlo.
        soltarBloqueoDelComprador(subasta, idempotencyKey);

        Puja ganadora = new Puja(null, subasta.getId(), jugadorId, precio, TipoPuja.MANUAL,
                EstadoPuja.GANADORA, clock.instant(), reserva.id().toString());
        ganadora.setIdempotencyKey(idempotencyKey);
        return ganadora;
    }

    /**
     * Cierra una subasta vencida. Si llego con una puja vigente, esa puja gana,
     * el ítem se transfiere formalmente al ganador en el inventario y su reserva
     * de créditos se convierte en débito; si nadie pujó, la subasta cierra sin
     * adjudicación y se libera la reserva del producto para que el vendedor lo recupere.
     * Cubre el criterio 3 de HU-SUB-004 y la transferencia formal de propiedad.
     */
    public void cerrarPorVencimiento(Subasta subasta, Puja pujaVigente) {
        if (!subasta.estaActiva()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta " + subasta.getId() + " ya no esta activa");
        }

        if (pujaVigente == null) {
            subasta.setEstado(EstadoSubasta.SIN_ADJUDICACION);
            if (tieneInventario(subasta)) {
                inventarioClient.liberarReserva(subasta.getElementoInventarioId(), subasta.getId(),
                        "cierre-" + subasta.getId());
            }
            return;
        }

        String claveCierre = "cierre-" + subasta.getId();
        boolean transferido = false;
        if (tieneInventario(subasta)) {
            inventarioClient.transferirProducto(subasta.getElementoInventarioId(), pujaVigente.getJugadorId(),
                    subasta.getId(), claveCierre);
            transferido = true;
        }

        try {
            creditoClient.consumir(UUID.fromString(pujaVigente.getReservaCreditoId()), subasta.getVendedorId());
        } catch (RuntimeException fallo) {
            // El producto ya salio hacia el ganador y esa llamada es HTTP: no la
            // deshace el rollback de la transaccion, que si revierte el estado
            // en la base de datos. Sin esta compensacion el ganador se queda el
            // objeto sin haberlo pagado. comprarAhora ya lo hacia; esto faltaba.
            if (transferido) {
                devolverProductoAlVendedor(subasta, claveCierre);
            }
            throw fallo;
        }

        pujaVigente.setEstado(EstadoPuja.GANADORA);
        subasta.setEstado(EstadoSubasta.ADJUDICADA);

        // Igual que en la compra inmediata: el cobro ya entro, asi que esto va
        // DESPUES del estado y su fallo no revierte nada.
        soltarBloqueoDelComprador(subasta, claveCierre);
    }

    /**
     * Suelta el bloqueo de subasta del objeto ya vendido — FI-TRANSFER-1.
     *
     * <p>La transferencia conserva el bloqueo a proposito: es lo que permite
     * deshacerla si el cobro falla, y lo que impide que el comprador equipe o
     * revenda algo que todavia no ha pagado. Cuando la venta es definitiva, ese
     * bloqueo ya no protege nada y hay que soltarlo o el objeto queda inservible
     * en manos de su dueno nuevo.
     *
     * <p>Va DESPUES de fijar el estado y su fallo no propaga. Si propagara, el
     * reintento del cierre volveria a intentar cobrar una reserva ya consumida,
     * fallaria, y la compensacion devolveria al vendedor un objeto que el
     * comprador ya pago. Entre «objeto bloqueado de mas» y «objeto pagado y
     * devuelto», lo primero es un defecto reparable y lo segundo es un robo.
     *
     * <p>Queda en el registro con el identificador de la subasta. Es deuda
     * conocida: sin un mecanismo de reconciliacion no se arregla solo, y montar
     * uno es una decision del dueno del modulo.
     */
    private void soltarBloqueoDelComprador(Subasta subasta, String claveOriginal) {
        if (!tieneInventario(subasta)) {
            return;
        }
        try {
            inventarioClient.liberarReserva(subasta.getElementoInventarioId(), subasta.getId(),
                    "liberar-" + claveOriginal);
        } catch (RuntimeException fallo) {
            log.error("Subasta {} adjudicada y cobrada, pero el elemento {} sigue bloqueado: {}",
                    subasta.getId(), subasta.getElementoInventarioId(), fallo.getMessage(), fallo);
        }
    }

    /** Hay un elemento de inventario que mover. */
    private boolean tieneInventario(Subasta subasta) {
        return subasta.getElementoInventarioId() != null
                && !subasta.getElementoInventarioId().isBlank();
    }

    /**
     * Deshace una transferencia ya hecha: devuelve el producto al vendedor, que
     * es el estado en el que estaba antes de intentar adjudicarla.
     *
     * <p><b>Esta compensacion no podia funcionar hasta FI-TRANSFER-1.</b> La
     * transferencia soltaba el bloqueo de subasta al mover el elemento, y la
     * operacion de inventario exige que el elemento este bloqueado por ESA
     * subasta para aceptar un cambio de dueno: la llamada de vuelta se rechazaba
     * siempre con 409. La segunda llamada, la de volver a reservar, tampoco
     * podia funcionar: bloquear exige ser el dueno, y en ese momento el dueno
     * era el comprador, no el vendedor. Las dos fallaban, el {@code catch} las
     * escribia en el registro, y el ganador se quedaba un objeto que no habia
     * pagado. La compensacion existia, estaba escrita, y era decorativa.
     *
     * <p>Ahora la transferencia conserva el bloqueo, asi que la vuelta cumple la
     * precondicion y el elemento aterriza en el vendedor <b>ya reservado para la
     * subasta</b> — exactamente el estado previo. La segunda llamada se quita
     * porque sobra y porque no podia hacer nada.
     *
     * <p>Sigue siendo el mejor esfuerzo posible: si esto tambien falla no hay
     * nada mas que hacer automaticamente, y queda en el registro con el
     * identificador de la subasta. Tragarselo en silencio dejaria un producto en
     * manos de quien no lo pago y sin rastro.
     */
    private void devolverProductoAlVendedor(Subasta subasta, String claveOriginal) {
        try {
            inventarioClient.transferirProducto(subasta.getElementoInventarioId(), subasta.getVendedorId(),
                    subasta.getId(), "compensar-" + claveOriginal);
        } catch (RuntimeException falloCompensando) {
            log.error("Fallo al compensar transferencia de inventario para subasta {}: {}",
                    subasta.getId(), falloCompensando.getMessage(), falloCompensando);
        }
    }

    private void validarReglasDeParticipacion(Subasta subasta, UUID jugadorId, BigDecimal monto, ContextoParticipacion contexto) {
        if (!subasta.estaActiva()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta " + subasta.getId() + " no esta activa");
        }
        if (subasta.esVendedor(jugadorId)) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.PUJA_PROPIA,
                    "El jugador " + jugadorId + " no puede pujar en su propia subasta");
        }

        BigDecimal minimoValido = subasta.getOfertaVigente().add(subasta.getIncrementoMinimo());
        if (monto.compareTo(minimoValido) < 0) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE,
                    "La puja de " + monto + " no supera la oferta vigente mas el incremento minimo (" + minimoValido + ")");
        }

        if (contexto.ultimaPujaDelJugador() != null) {
            Duration transcurrido = Duration.between(contexto.ultimaPujaDelJugador(), clock.instant());
            if (transcurrido.getSeconds() < parametros.getIntervaloMinimoSegundos()) {
                throw new PujaRechazadaException(PujaRechazadaException.Motivo.INTERVALO_MINIMO_NO_CUMPLIDO,
                        "El jugador " + jugadorId + " debe esperar " + parametros.getIntervaloMinimoSegundos()
                                + " s entre pujas (transcurrieron " + transcurrido.getSeconds() + " s)");
            }
        }

        if (contexto.subastasActivasDelJugador() >= parametros.getMaxSubastasActivasPorJugador()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.LIMITE_SUBASTAS_ACTIVAS,
                    "El jugador " + jugadorId + " alcanzo el limite de " + parametros.getMaxSubastasActivasPorJugador()
                            + " subastas activas simultaneas");
        }

        if (contexto.pujasActivasDelJugador() >= parametros.getMaxPujasActivasPorJugador()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.LIMITE_PUJAS_ACTIVAS,
                    "El jugador " + jugadorId + " alcanzo el limite de " + parametros.getMaxPujasActivasPorJugador()
                            + " pujas activas simultaneas");
        }
    }
}
