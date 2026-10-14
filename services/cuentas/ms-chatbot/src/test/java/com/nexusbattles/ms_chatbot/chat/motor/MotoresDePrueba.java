package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;

/**
 * El motor de respuestas para las pruebas de su lógica: lee los temas de
 * producción del repositorio (simulado) en cada consulta, como antes de
 * 7.4.9, y no guarda nada. Sin versión en producción ({@code versionId}
 * null), {@link CacheDeRespuestas} no interviene. La caché y la lectura por
 * versión tienen sus propias pruebas.
 */
public final class MotoresDePrueba {

    private MotoresDePrueba() {
    }

    public static MotorRespuestas sobre(TemaConocimientoRepository temas) {
        return new MotorRespuestas(
            () -> new BaseEnProduccion(null, temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)),
            new CacheDeRespuestas(0, 0, Clock.systemUTC(), new SimpleMeterRegistry()));
    }
}
