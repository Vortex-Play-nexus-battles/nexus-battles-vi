package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

/**
 * Lo que le pasa a una sancion y hay que contar fuera: el disparador de las
 * salidas de {@link SalidasDeSancion}.
 */
public enum EventoDeSancion {

    /** Se emite (advertencia, suspension o baneo). */
    EMISION,

    /** El panel resuelve una apelacion (mantenida, reducida o revertida). */
    APELACION_RESUELTA,

    /** Un administrador levanta una sancion vigente sin apelacion (1.1.0). */
    LEVANTAMIENTO
}
