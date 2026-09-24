package com.nexusbattles.ms_chatbot.chat.motor.model;

// HU-CHA-012 (RF-CHA-014): ciclo de vida de una version de la base de
// conocimiento.
public enum EstadoVersion {

    /** Candidata en edicion por el administrador. Como mucho una a la vez (indice parcial de V4). */
    BORRADOR,

    /** La que responde a los jugadores. Exactamente una (indice parcial de V4). */
    PRODUCCION,

    /** Estuvo en produccion y fue reemplazada; sirve para revertir. */
    RETIRADA
}
