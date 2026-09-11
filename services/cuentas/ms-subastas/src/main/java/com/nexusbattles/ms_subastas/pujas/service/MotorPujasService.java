package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.ReservaCredito;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
@RequiredArgsConstructor
public class MotorPujasService {

    private final CreditoClient creditoClient;
    private final Clock clock;
    private final ParametrosPuja parametros;

    /**
     * @param idempotencyKey clave que debe venir del cliente (cabecera
     *                       Idempotency-Key). NO se genera aqui a proposito: si
     *                       se derivara del reloj, un reintento por timeout
     *                       produciria una clave distinta y reservaria los
     *                       creditos dos veces, que es justo lo que la clave
     *                       debe evitar.
     */
    public Puja pujar(Subasta subasta, Puja pujaVigente, UUID jugadorId, BigDecimal monto,
                      ContextoParticipacion contexto, String idempotencyKey) {
        validarReglasDeParticipacion(subasta, jugadorId, monto, contexto);

        ReservaCredito reserva = creditoClient.reservar(jugadorId, monto, subasta.getId(), idempotencyKey);

        if (pujaVigente != null) {
            creditoClient.liberar(UUID.fromString(pujaVigente.getReservaCreditoId()));
            pujaVigente.setEstado(EstadoPuja.SUPERADA);
        }

        subasta.setOfertaVigente(monto);
        subasta.setMejorPostorId(jugadorId);

        // id nulo a proposito: lo genera la base de datos (@GeneratedValue). Si
        // el dominio lo asignara, Spring Data veria una entidad con id y haria
        // merge (UPDATE de una fila inexistente) en vez de persist.
        return new Puja(null, subasta.getId(), jugadorId, monto, TipoPuja.MANUAL,
                EstadoPuja.ACTIVA, clock.instant(), reserva.id().toString());
    }

    public Puja comprarAhora(Subasta subasta, Puja pujaVigente, UUID jugadorId) {
        if (!subasta.estaActiva()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta " + subasta.getId() + " no esta activa");
        }
        if (subasta.esVendedor(jugadorId)) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.PUJA_PROPIA,
                    "El jugador " + jugadorId + " no puede comprar en su propia subasta");
        }
        if (subasta.getPrecioCompraInmediata() == null) {
            throw new IllegalStateException("La subasta " + subasta.getId() + " no ofrece compra inmediata");
        }

        BigDecimal precio = subasta.getPrecioCompraInmediata();
        String idempotencyKey = "compra-inmediata:%s:%s".formatted(jugadorId, subasta.getId());
        ReservaCredito reserva = creditoClient.reservar(jugadorId, precio, subasta.getId(), idempotencyKey);
        creditoClient.consumir(reserva.id());

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
        subasta.setEstado(EstadoSubasta.ADJUDICADA);

        // TODO(Dia 2+): publicar evento SubastaCerrada para que notificaciones
        // avise a los demas postores e inventario desbloquee/transfiera el
        // producto. Ninguno de los dos microservicios esta en el Sprint 2.
        return new Puja(null, subasta.getId(), jugadorId, precio, TipoPuja.MANUAL,
                EstadoPuja.GANADORA, clock.instant(), reserva.id().toString());
    }

    /**
     * Cierra una subasta vencida. Si llego con una puja vigente, esa puja gana
     * y su reserva se convierte en debito; si nadie pujo, la subasta cierra sin
     * adjudicacion. Cubre el criterio 3 de HU-SUB-004: los creditos reservados
     * se restituyen si la subasta cierra sin adjudicacion.
     *
     * Quien dispara este cierre (un job programado) es una costura con
     * HU-SUB-001, pendiente de acordar con Edwin: el contador de la subasta lo
     * inicia el. La restitucion de creditos, en cambio, es de esta HU.
     */
    public void cerrarPorVencimiento(Subasta subasta, Puja pujaVigente) {
        if (!subasta.estaActiva()) {
            throw new PujaRechazadaException(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    "La subasta " + subasta.getId() + " ya no esta activa");
        }

        if (pujaVigente == null) {
            subasta.setEstado(EstadoSubasta.SIN_ADJUDICACION);
            return;
        }

        creditoClient.consumir(UUID.fromString(pujaVigente.getReservaCreditoId()));
        pujaVigente.setEstado(EstadoPuja.GANADORA);
        subasta.setEstado(EstadoSubasta.ADJUDICADA);
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
