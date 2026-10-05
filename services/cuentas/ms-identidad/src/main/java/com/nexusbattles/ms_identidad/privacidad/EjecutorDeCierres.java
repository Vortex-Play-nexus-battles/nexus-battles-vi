package com.nexusbattles.ms_identidad.privacidad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La tarea programada del derecho al olvido (HU-PRV-005): cada cierto tiempo
 * busca los cierres programados cuyo plazo ya vencio y los ejecuta, uno a uno
 * y cada uno en su transaccion ({@link AnonimizadorDeCuentas}). Un fallo en
 * uno no detiene los demas; el que fallo sigue programado y se reintenta en
 * la siguiente pasada.
 *
 * <p>Cada cuanto pasa es una constante tecnica ({@code
 * identidad.privacidad.intervalo-ms}, 15 minutos por omision): el plazo de 30
 * dias lo fija la solicitud, no esta tarea. Se apaga con {@code
 * identidad.privacidad.ejecucion-automatica=false} (las pruebas).
 */
@Component
@ConditionalOnProperty(name = "identidad.privacidad.ejecucion-automatica", havingValue = "true",
        matchIfMissing = true)
public class EjecutorDeCierres {

    private static final Logger log = LoggerFactory.getLogger(EjecutorDeCierres.class);

    /** Cuantas como mucho en cada pasada; las demas, en la siguiente. */
    static final int LOTE = 20;

    private final SolicitudDeCierreRepository solicitudes;
    private final AnonimizadorDeCuentas anonimizador;
    private final Clock reloj;

    @Autowired
    public EjecutorDeCierres(SolicitudDeCierreRepository solicitudes, AnonimizadorDeCuentas anonimizador) {
        this(solicitudes, anonimizador, Clock.systemDefaultZone());
    }

    EjecutorDeCierres(SolicitudDeCierreRepository solicitudes, AnonimizadorDeCuentas anonimizador, Clock reloj) {
        this.solicitudes = solicitudes;
        this.anonimizador = anonimizador;
        this.reloj = reloj;
    }

    /** @return cuantas cuentas quedaron anonimizadas en esta pasada */
    @Scheduled(initialDelayString = "${identidad.privacidad.retraso-inicial-ms:60000}",
            fixedDelayString = "${identidad.privacidad.intervalo-ms:900000}")
    public int ejecutarVencidas() {
        List<UUID> vencidas = solicitudes.vencidas(SolicitudDeCierre.PROGRAMADA, LocalDateTime.now(reloj),
                PageRequest.of(0, LOTE));
        int anonimizadas = 0;
        for (UUID id : vencidas) {
            try {
                if (anonimizador.ejecutar(id)) {
                    anonimizadas++;
                }
            } catch (RuntimeException fallo) {
                log.error("No se pudo ejecutar el cierre de cuenta {}: se reintentara en la siguiente pasada", id,
                        fallo);
            }
        }
        return anonimizadas;
    }
}
