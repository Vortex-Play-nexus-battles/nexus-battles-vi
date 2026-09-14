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
        return new Puja(null, subasta.getId(), jugadorId, monto, tipo,
                EstadoPuja.ACTIVA, clock.instant(), reserva.id().toString());
    }

    /**
     * @param idempotencyKey clave del cliente (cabecera Idempotency-Key), igual
     *                       que en {@link #pujar}. Antes se derivaba aqui de
     *                       (jugador, subasta), que protegia incluso frente a un
     *                       cliente que reintentara con una clave distinta; el
     *                       contrato la declara obligatoria, asi que manda la
     *                       del cliente. El riesgo queda acotado porque tras la
     *                       primera compra la subasta queda ADJUDICADA y un
     *                       reintento se corta en SUBASTA_NO_ACTIVA antes de
     *                       llegar a reservar creditos.
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
            creditoClient.consumir(reserva.id());
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

        return new Puja(null, subasta.getId(), jugadorId, precio, TipoPuja.MANUAL,
                EstadoPuja.GANADORA, clock.instant(), reserva.id().toString());
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
            creditoClient.consumir(UUID.fromString(pujaVigente.getReservaCreditoId()));
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
    }

    /** Hay un elemento de inventario que mover. */
    private boolean tieneInventario(Subasta subasta) {
        return subasta.getElementoInventarioId() != null
                && !subasta.getElementoInventarioId().isBlank();
    }

    /**
     * Deshace una transferencia ya hecha: devuelve el producto al vendedor y lo
     * vuelve a dejar reservado para la subasta, que es el estado en el que
     * estaba antes de intentar adjudicarla.
     *
     * <p>Es el mejor esfuerzo posible. Si la compensacion tambien falla no se
     * puede hacer nada mas automaticamente, asi que queda en el log con el
     * identificador de la subasta para poder repararlo a mano: tragarsela en
     * silencio dejaria un producto en manos de quien no lo pago sin rastro.
     */
    private void devolverProductoAlVendedor(Subasta subasta, String claveOriginal) {
        try {
            inventarioClient.transferirProducto(subasta.getElementoInventarioId(), subasta.getVendedorId(),
                    subasta.getId(), "compensar-" + claveOriginal);
            inventarioClient.reservar(subasta.getElementoInventarioId(), subasta.getVendedorId(),
                    subasta.getId(), "compensar-reserva-" + claveOriginal);
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
