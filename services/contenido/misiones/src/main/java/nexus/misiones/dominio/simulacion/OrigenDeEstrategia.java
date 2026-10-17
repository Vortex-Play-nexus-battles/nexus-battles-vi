package nexus.misiones.dominio.simulacion;

/**
 * De donde sale la estrategia con la que juega un enemigo (HU-SIM-004), por orden de precedencia: la rotacion que la
 * mision trae escrita para ese enemigo, la estrategia predefinida de su prototipo y nivel, o la heuristica por defecto.
 * Queda en el evento de combate para saber que estrategia uso cada enemigo en cada turno.
 */
public enum OrigenDeEstrategia {
    MISION,
    PREDEFINIDA,
    HEURISTICA
}
