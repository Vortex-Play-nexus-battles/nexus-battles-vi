package com.nexusbattles.ms_chatbot.chat.sugerencias;

// ms-chatbot.yaml 1.15.0 (7.4.3 «sugerencias proactivas», D-CHA-14): por que
// sale una sugerencia. La ventana lo dice con la sugerencia.
public enum MotivoDeSugerencia {

    /** Otro tema de la categoria de uno que ya le respondieron. */
    POR_LO_QUE_PREGUNTASTE,

    /** Un tema de la categoria de la seccion donde esta. */
    POR_DONDE_ESTAS,

    /** Aun no pregunto nada: de lo mas consultado por todos. */
    PARA_EMPEZAR
}
