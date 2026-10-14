package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;

import java.util.List;
import java.util.UUID;

/**
 * 7.4.9 (ms-chatbot.yaml 1.9.0): la versión de la base de conocimiento que
 * está en producción y sus temas activos, tal como los usa el motor.
 *
 * <p>{@code versionId} es lo que distingue una base de otra: un despliegue o
 * una reversión lo cambian, y con él deja de valer todo lo que se guardó de
 * la anterior ({@link CacheDeRespuestas}). Es {@code null} cuando no hay
 * versión en producción, y entonces nada se guarda.
 *
 * @param versionId id de la versión en producción, o {@code null} si no hay
 * @param temas     sus temas activos; la lista no se puede modificar
 */
public record BaseEnProduccion(UUID versionId, List<TemaConocimiento> temas) {

    public BaseEnProduccion {
        temas = temas == null ? List.of() : List.copyOf(temas);
    }

    /** Sin versión en producción: el motor escala todo y nada se guarda. */
    public static BaseEnProduccion vacia() {
        return new BaseEnProduccion(null, List.of());
    }
}
