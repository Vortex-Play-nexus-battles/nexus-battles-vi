package nexus.misiones.dominio;

/**
 * PENDIENTE: por hacer o reintentandose. HECHO: el otro servicio lo confirmo.
 * FALLIDO: el otro servicio lo rechazo de forma definitiva (un 4xx que no va a
 * cambiar reintentando); queda anotado con su motivo y no se reintenta.
 */
public enum EstadoDePaso {
    PENDIENTE,
    HECHO,
    FALLIDO
}
