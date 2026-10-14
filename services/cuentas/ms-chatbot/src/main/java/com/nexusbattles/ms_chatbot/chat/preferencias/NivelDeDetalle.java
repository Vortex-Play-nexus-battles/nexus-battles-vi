package com.nexusbattles.ms_chatbot.chat.preferencias;

// ms-chatbot.yaml 1.3.6 (7.4.5 «nivel de detalle de las respuestas»).
//
// 1.15.0 (7.4.5 «adaptar el nivel de detalle segun la experiencia del
// usuario», RF-CHA-010): AUTOMATICO, el de quien nunca eligio. No es un nivel
// de respuesta: ExperienciaDelChat lo traduce, con lo que la persona ya
// pregunto, a uno de los otros tres antes de responder (D-CHA-13).
public enum NivelDeDetalle {

    /** Segun su experiencia con el asistente (D-CHA-13). Nunca llega al motor. */
    AUTOMATICO,

    /** Solo la primera oracion. Los temas paso a paso conservan sus pasos. */
    BREVE,

    /** La respuesta completa del tema, como hasta 1.3.5. */
    NORMAL,

    /** La respuesta completa y, al final, los temas relacionados. */
    DETALLADO
}
