package com.nexusbattles.ms_chatbot.chat.preferencias;

// ms-chatbot.yaml 1.3.6 (7.4.2, 7.4.5): en que idioma responde el asistente.
public enum IdiomaPreferido {

    /** El de la pregunta, como hasta 1.3.5: se detecta por las palabras clave. */
    AUTOMATICO,

    /** Siempre en espanol. */
    ES,

    /** En ingles cuando el tema tiene respuesta en ingles; si no, en espanol. */
    EN
}
