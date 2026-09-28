package nexus.misiones.dominio;

/**
 * El ciclo de vida de UNA ejecucion (un heroe enviado una vez a una mision).
 *
 * <p>Es un subconjunto de {@link EstadoMision}: Disponible y Bloqueada son
 * estados de la mision para un jugador, no de una ejecucion. Las transiciones
 * permitidas son solo dos, y las dos salen de {@link #EN_PROGRESO}: al vencer
 * el plazo (Completada o Fallida segun la simulacion) o al cancelar
 * (Abandonada). Cualquier otra se rechaza y se conserva el estado (HU-MIS-010).
 */
public enum EstadoEjecucion {
    EN_PROGRESO,
    COMPLETADA,
    FALLIDA,
    ABANDONADA;

    public boolean terminada() {
        return this != EN_PROGRESO;
    }

    /** Solo las terminadas por la simulacion tienen reporte (7.8.8). */
    public boolean tieneReporte() {
        return this == COMPLETADA || this == FALLIDA;
    }

    public EstadoMision comoEstadoDeMision() {
        return EstadoMision.valueOf(name());
    }
}
