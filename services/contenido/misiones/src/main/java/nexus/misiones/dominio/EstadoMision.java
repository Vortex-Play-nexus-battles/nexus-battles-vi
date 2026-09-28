package nexus.misiones.dominio;

/**
 * Los seis estados de una mision de la seccion 7.8.7, vistos por UN jugador:
 * la misma mision puede estar bloqueada para uno y completada para otro.
 */
public enum EstadoMision {
    DISPONIBLE,
    BLOQUEADA,
    EN_PROGRESO,
    COMPLETADA,
    FALLIDA,
    ABANDONADA
}
