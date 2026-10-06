package com.nexusbattles.ms_chatbot.chat.preferencias;

// ms-chatbot.yaml 1.3.6 (7.4.5 «nivel de detalle de las respuestas»).
public enum NivelDeDetalle {

    /** Solo la primera oracion. Los temas paso a paso conservan sus pasos. */
    BREVE,

    /** La respuesta completa del tema, como hasta 1.3.5. */
    NORMAL,

    /** La respuesta completa y, al final, los temas relacionados. */
    DETALLADO
}
