package com.nexusbattles.ms_identidad.onboarding.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Pone a procesar el alta de un jugador sin que quien la pide espere.
 *
 * <p>Tres modos ({@code app.onboarding.ejecucion}):
 * <ul>
 *   <li>{@code segundo-plano} (por omision): un grupo pequeno de hilos
 *       propio. El registro responde enseguida y la pantalla «Preparando tu
 *       cuenta» consulta el avance. Si el grupo esta lleno, la tarea no se
 *       pierde: el alta sigue PENDIENTE y la recoge el reintento programado.</li>
 *   <li>{@code sincrona}: en el mismo hilo. Solo para pruebas que quieren
 *       afirmar el resultado sin esperar.</li>
 *   <li>{@code manual}: no lanza nada; solo el reintento programado o una
 *       llamada explicita procesan. Es el modo de las pruebas que no son del
 *       alta, para que registrar un usuario no salga a buscar servicios.</li>
 * </ul>
 */
@Component
public class LanzadorOnboarding {

    private static final Logger log = LoggerFactory.getLogger(LanzadorOnboarding.class);

    public enum Modo { SEGUNDO_PLANO, SINCRONA, MANUAL }

    private final ProcesadorOnboarding procesador;
    private final Modo modo;
    private final ThreadPoolTaskExecutor grupo;

    public LanzadorOnboarding(ProcesadorOnboarding procesador,
                              @Value("${app.onboarding.ejecucion:segundo-plano}") String modo) {
        this.procesador = procesador;
        this.modo = modoDe(modo);
        if (this.modo == Modo.SEGUNDO_PLANO) {
            ThreadPoolTaskExecutor hilos = new ThreadPoolTaskExecutor();
            hilos.setCorePoolSize(2);
            hilos.setMaxPoolSize(4);
            hilos.setQueueCapacity(100);
            hilos.setThreadNamePrefix("alta-jugador-");
            hilos.setWaitForTasksToCompleteOnShutdown(true);
            hilos.setAwaitTerminationSeconds(10);
            hilos.initialize();
            this.grupo = hilos;
        } else {
            this.grupo = null;
        }
    }

    static Modo modoDe(String texto) {
        if (texto == null || texto.isBlank()) {
            return Modo.SEGUNDO_PLANO;
        }
        try {
            return Modo.valueOf(texto.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException desconocido) {
            log.warn("app.onboarding.ejecucion='{}' no es un modo conocido; se usa segundo-plano", texto);
            return Modo.SEGUNDO_PLANO;
        }
    }

    public Modo modo() {
        return modo;
    }

    public void lanzar(UUID uid) {
        switch (modo) {
            case MANUAL -> log.debug("Alta de {} sin lanzar (modo manual)", uid);
            case SINCRONA -> procesarSinPropagar(uid);
            case SEGUNDO_PLANO -> {
                try {
                    grupo.execute(() -> procesarSinPropagar(uid));
                } catch (TaskRejectedException lleno) {
                    log.warn("Alta de {} no se pudo lanzar ahora ({}); la recoge el reintento programado",
                            uid, lleno.getMessage());
                }
            }
        }
    }

    private void procesarSinPropagar(UUID uid) {
        try {
            procesador.procesar(uid);
        } catch (RuntimeException fallo) {
            // Un fallo aqui no es de un paso (esos ya se anotan): es de la base
            // de datos de identidad. El alta queda como estaba y se reintenta.
            log.error("Alta de {} interrumpida", uid, fallo);
        }
    }

    @PreDestroy
    void cerrar() {
        if (grupo != null) {
            grupo.shutdown();
        }
    }
}
