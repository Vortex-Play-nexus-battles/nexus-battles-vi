package com.nexusbattles.ms_chatbot.chat.motor.model;

/**
 * Tipo de respuesta que el motor puede construir para un tema de la
 * base de conocimiento, según el criterio de aceptación de HU-CHA-004:
 * "Respuestas directas, enriquecidas, paso a paso, contextuales o
 * sugerencias proactivas según el caso".
 */
public enum TipoRespuesta {

    /** Información concisa y precisa para una consulta específica. */
    DIRECTA,

    /** Instrucciones secuenciales para un proceso de varios pasos (equivale a la "respuesta enriquecida/paso a paso" del documento). */
    PASO_A_PASO,

    /** Recomendación proactiva relacionada con el tema consultado. */
    SUGERENCIA_PROACTIVA,

    /** Respuesta que depende del contexto de la conversación (p. ej. si el usuario está autenticado o no). */
    CONTEXTUAL
}
