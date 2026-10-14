package com.nexusbattles.ms_chatbot.chat.model;

/**
 * ms-chatbot.yaml 1.7.0 (7.4.5, V13): en qué está una pregunta sin respuesta.
 */
public enum EstadoBrecha {

    /** Nadie la ha atendido, o se volvió a preguntar después de marcarla atendida. */
    PENDIENTE,

    /** Un tema de la base ya la cubre. */
    ATENDIDA,

    /** Es ruido: una prueba, algo que no es del juego. */
    DESCARTADA
}
