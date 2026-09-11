package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.notificaciones.NotificacionOutbox;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Emite las pujas automaticas (criterio 4 de HU-SUB-004).
 *
 * Por que un sondeo programado y no un evento tras cada puja: el intervalo
 * minimo entre pujas del mismo jugador es de 5 s de todas formas, asi que un
 * sondeo corto cubre con un solo mecanismo tanto "responde ya" como "responde
 * cuando se cumpla el enfriamiento". Un evento obligaria a diferir la segunda
 * mitad en una cola en memoria, que no sobrevive a un reinicio; la tabla si.
 *
 * Cada subasta se procesa aparte y en su propia transaccion: si una falla, o si
 * la puja se rechaza por perder la carrera contra un jugador humano, el resto
 * del lote sigue.
 */
@Component
@RequiredArgsConstructor
public class EmisionDePujasAutomaticasJob {

    private static final Logger log = LoggerFactory.getLogger(EmisionDePujasAutomaticasJob.class);

    private final SubastaRepository subastaRepository;
    private final PujaAutomaticaRepository pujaAutomaticaRepository;
    private final PujaRepository pujaRepository;
    private final PujaApplicationService pujaApplicationService;
    private final MotorPujaAutomaticaService motorAutomatico;
    private final NotificacionOutbox outbox;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.pujas.emision-automatica-intervalo-ms:2000}")
    public void emitirPujasAutomaticas() {
        for (UUID subastaId : subastaRepository.findIdsConPujaAutomaticaPendiente(clock.instant())) {
            try {
                procesar(subastaId);
            } catch (PujaRechazadaException rechazada) {
                // Lo normal cuando un humano gana la carrera entre el sondeo y
                // la emision: la oferta vigente ya subio. No es un error.
                log.debug("Puja automatica rechazada en la subasta {}: {}", subastaId, rechazada.getMotivo());
            } catch (RuntimeException e) {
                log.error("Fallo la emision automatica en la subasta {}: {}", subastaId, e.getMessage(), e);
            }
        }
    }

    private void procesar(UUID subastaId) {
        Optional<Subasta> encontrada = subastaRepository.findById(subastaId);
        if (encontrada.isEmpty()) {
            return;
        }
        Subasta subasta = encontrada.get();
        List<PujaAutomatica> candidatas = pujaAutomaticaRepository.findBySubastaIdAndActivaTrue(subastaId);

        Optional<PujaAutomatica> elegida = motorAutomatico.elegirSiguiente(subasta, candidatas);

        // elegirSiguiente desactiva las que ya no alcanzan; hay que persistir esa
        // decision y avisarle a su dueno, que es lo que pide el criterio 4.
        for (PujaAutomatica candidata : candidatas) {
            if (!candidata.isActiva()) {
                pujaAutomaticaRepository.save(candidata);
                outbox.avisarLimiteAutomaticoAlcanzado(subastaId, candidata.getJugadorId(), candidata.getLimite());
            }
        }

        if (elegida.isEmpty()) {
            return;
        }

        PujaAutomatica automatica = elegida.get();
        if (!puedeEmitirYa(automatica.getJugadorId())) {
            return;
        }

        motorAutomatico.calcularRespuesta(subasta, automatica).ifPresent(monto ->
                pujaApplicationService.pujarAutomaticamente(subastaId, automatica.getJugadorId(), monto,
                        claveDeIdempotencia(subastaId, automatica.getJugadorId(), monto)));
    }

    private boolean puedeEmitirYa(UUID jugadorId) {
        Instant ultimaPuja = pujaRepository.findFirstByJugadorIdOrderByCreadaEnDesc(jugadorId)
                .map(Puja::getCreadaEn)
                .orElse(null);
        return !motorAutomatico.disponibleDesde(ultimaPuja).isAfter(clock.instant());
    }

    /**
     * Clave estable por (subasta, jugador, monto): si el sondeo vuelve a elegir
     * la misma oferta porque la anterior quedo en duda, ms-finanzas reconoce la
     * reserva en vez de retener los creditos dos veces.
     */
    private String claveDeIdempotencia(UUID subastaId, UUID jugadorId, BigDecimal monto) {
        return "automatica:%s:%s:%s".formatted(subastaId, jugadorId, monto.toPlainString());
    }
}
