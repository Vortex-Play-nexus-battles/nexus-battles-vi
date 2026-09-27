package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingJugadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Recoge las altas que se quedaron a medias y las vuelve a intentar.
 *
 * <p>Tres casos: las aplazadas cuyo siguiente intento ya llego (con espera
 * creciente, ver {@link ProcesadorOnboarding#espera}), las pendientes que
 * nadie tomo en {@link #PENDIENTE_OLVIDADA} (se perdio el aviso del registro,
 * por ejemplo por un reinicio) y las que se quedaron en proceso con el turno
 * caducado. Nadie tiene que acordarse de nada ni tocar la base de datos.
 */
@Component
@ConditionalOnProperty(name = "app.onboarding.reintentos-automaticos", havingValue = "true", matchIfMissing = true)
public class ReintentosDeAlta {

    private static final Logger log = LoggerFactory.getLogger(ReintentosDeAlta.class);

    static final Duration PENDIENTE_OLVIDADA = Duration.ofMinutes(1);
    static final int LOTE = 20;

    private final OnboardingJugadorRepository jugadores;
    private final ProcesadorOnboarding procesador;
    private final Clock reloj;

    @Autowired
    public ReintentosDeAlta(OnboardingJugadorRepository jugadores, ProcesadorOnboarding procesador) {
        this(jugadores, procesador, Clock.systemUTC());
    }

    ReintentosDeAlta(OnboardingJugadorRepository jugadores, ProcesadorOnboarding procesador, Clock reloj) {
        this.jugadores = jugadores;
        this.procesador = procesador;
        this.reloj = reloj;
    }

    @Scheduled(initialDelayString = "${app.onboarding.retraso-inicial-ms:30000}",
            fixedDelayString = "${app.onboarding.intervalo-reintentos-ms:30000}")
    public void reintentar() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<UUID> porReintentar = jugadores.porReintentar(
                EstadoOnboarding.ERROR_REINTENTABLE, EstadoOnboarding.PENDIENTE, EstadoOnboarding.EN_PROCESO,
                ahora, ahora.minus(PENDIENTE_OLVIDADA), PageRequest.of(0, LOTE));
        for (UUID uid : porReintentar) {
            try {
                procesador.procesar(uid);
            } catch (RuntimeException fallo) {
                log.error("Reintento del alta de {} interrumpido", uid, fallo);
            }
        }
    }
}
