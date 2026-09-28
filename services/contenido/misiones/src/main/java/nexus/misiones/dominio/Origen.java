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
 */
public enum Origen {
    DOCUMENTO,
    PROVISIONAL_DEV
}
