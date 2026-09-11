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
 * Concurrencia: el metodo es synchronized como guardia de proceso para las
 * pruebas de carrera entre hilos sobre la MISMA instancia. El guardia real
 * entre procesos/replicas sera un lock pesimista de base de datos
 * (SELECT ... FOR UPDATE / @Lock(PESSIMISTIC_WRITE)) sobre la fila de
 * Subasta una vez exista el repositorio JPA real - pendiente del diseno
 * conjunto del Dia 1.
 */
@Service
@RequiredArgsConstructor
public class MotorPujasService {

    private final CreditoClient creditoClient;
    private final Clock clock;
    private final ParametrosPuja parametros;

    public synchronized Puja pujar(Subasta subasta, Puja pujaVigente, UUID jugadorId, BigDecimal monto,
                                    ContextoParticipacion contexto) {
        validarReglasDeParticipacion(subasta, jugadorId, monto, contexto);

        String idempotencyKey = "%s:%s:%s".formatted(jugadorId, subasta.getId(), clock.instant());
        ReservaCredito reserva = creditoClient.reservar(jugadorId, monto, subasta.getId(), idempotencyKey);

        if (pujaVigente != null) {
            creditoClient.liberar(UUID.fromString(pujaVigente.getReservaCreditoId()));
            pujaVigente.setEstado(EstadoPuja.SUPERADA);
        }

        subasta.setOfertaVigente(monto);
        subasta.setMejorPostorId(jugadorId);

        return new Puja(UUID.randomUUID(), subasta.getId(), jugadorId, monto, TipoPuja.MANUAL,
                EstadoPuja.ACTIVA, clock.instant(), reserva.id().toString());
    }

    public synchronized Puja comprarAhora(Subasta subasta, UUID jugadorId) {
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

        subasta.setOfertaVigente(precio);
        subasta.setMejorPostorId(jugadorId);
        subasta.setEstado(EstadoSubasta.ADJUDICADA);

        // TODO(Dia 2+): publicar evento SubastaCerrada para que notificaciones
        // avise a los demas postores e inventario desbloquee/transfiera el
        // producto. Ninguno de los dos microservicios esta en el Sprint 2.
        return new Puja(UUID.randomUUID(), subasta.getId(), jugadorId, precio, TipoPuja.MANUAL,
                EstadoPuja.GANADORA, clock.instant(), reserva.id().toString());
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
