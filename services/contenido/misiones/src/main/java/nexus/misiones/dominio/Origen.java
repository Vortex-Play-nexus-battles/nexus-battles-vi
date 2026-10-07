package nexus.misiones.dominio;

/**
 * De donde sale una mision.
 *
 * <p>{@link #DOCUMENTO}: contenido del documento del curso (el ejemplo de la
 * seccion 7.8.14). {@link #PROVISIONAL_DEV}: semilla de desarrollo para
 * demostrar el flujo de punta a punta en el banco E2E; solo se carga con
 * {@code MISIONES_SEMILLA_PROVISIONAL=true} y lo dice en su propio nombre. No
 * es contenido del juego: las dos misiones de cada equipo (RF-MIS-017) son
 * diseno del Grupo 2.
 *
 * <p>{@link #PROGRESION} (auditoria del 4-oct, D-42): las misiones de historia
 * de nivel 1 a 7 que llevan a un heroe nuevo hasta el Templo (nivel 8). Son
 * contenido del juego disenado por el equipo 6, no del documento, y se cargan
 * siempre.
 *
 * <p>{@link #EQUIPO} (HU-MIS-012): las misiones que disena el equipo (RF-MIS-56),
 * con el nivel de detalle del ejemplo de la seccion 7.8.14: enemigos regulares,
 * jefe final y al menos un Master con su epica. Se cargan siempre, de su propia
 * semilla, separada de la del documento.
 */
public enum Origen {
    DOCUMENTO,
    PROVISIONAL_DEV,
    PROGRESION,
    EQUIPO
}
