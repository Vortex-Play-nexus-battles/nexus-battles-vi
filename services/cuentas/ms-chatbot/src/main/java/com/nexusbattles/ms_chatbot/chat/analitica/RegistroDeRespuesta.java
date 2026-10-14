package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;

import java.time.Instant;

// HU-CHA-012: una respuesta del bot, reducida a lo que las analiticas usan.
// Solo existen para respuestas generadas desde V4 (antes no se median).
// 1.7.0: con su resultado, que solo tienen las respuestas desde V13 (null en
// las anteriores: no cuentan en la tasa de resolucion).
public record RegistroDeRespuesta(Instant fechaEnvio, Boolean escalado, Integer tiempoRespuestaMs,
                                  ResultadoDeRespuesta resultado) {

    /** Una respuesta medida sin resultado (anterior a V13). */
    public RegistroDeRespuesta(Instant fechaEnvio, Boolean escalado, Integer tiempoRespuestaMs) {
        this(fechaEnvio, escalado, tiempoRespuestaMs, null);
    }
}
