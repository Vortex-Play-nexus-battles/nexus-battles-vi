package com.nexusbattles.ms_subastas.subastas.model;

public enum EstadoSubasta {
    ACTIVA,
    ADJUDICADA,
    SIN_ADJUDICACION,

    /** El vendedor la cancelo (7.7.10): sin pujas, fuera de las ultimas 6 h y con penalizacion. */
    CANCELADA
}
