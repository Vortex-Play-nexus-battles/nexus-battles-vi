package nexus.combate.reglas;

/**
 * Que clase de cosa hace una accion. §6.1.2: las acciones especiales «mejoran
 * su ataque o defensa»; los sanadores sanan (Tabla 6, «Sanar»).
 */
public enum TipoDeAccion {
    /** Golpea a un rival: tirada contra su defensa y tabla de efectos. */
    ATAQUE,
    /** Se protege a si mismo hasta su proximo turno. */
    DEFENSA,
    /** Sana a uno mismo o a un companero. */
    SANACION,
    /** Sana a todo su grupo. */
    SANACION_GRUPAL,
    /** Devuelve toda la vida a un companero, caido o no. */
    REANIMACION,
    /** Ni golpea ni sana: quita poder, se vincula, se protege sin tirada. */
    APOYO
}
