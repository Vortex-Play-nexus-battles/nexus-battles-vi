package nexus.misiones.dominio;

/**
 * Como se comprueba un objetivo con el resultado de la simulacion. El texto
 * del objetivo es el del documento; el tipo es lo que permite decir si se
 * cumplio sin interpretar ese texto (seccion 7.8.8, «lista de objetivos con
 * estado»).
 */
public enum TipoDeObjetivo {
    /** «Derrotar al Guardian del Templo (Jefe final)». */
    DERROTAR_JEFE,
    /** «Explorar las 5 camaras del templo»: ganar todos los encuentros regulares. */
    COMPLETAR_ENCUENTROS,
    /** «Sin que la vida del heroe baje del 50%»: {@code valor} es el porcentaje. */
    VIDA_MINIMA,
    /** «Derrotar al Master si aparece». */
    DERROTAR_MASTER,
    /** «Encontrar los 3 fragmentos»: {@code botin} y {@code valor} unidades. */
    OBTENER_BOTIN
}
