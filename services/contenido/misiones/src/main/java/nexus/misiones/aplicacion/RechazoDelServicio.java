package nexus.misiones.aplicacion;

/**
 * Otro servicio contesto, y contesto que no (un 4xx): reintentar la misma
 * peticion va a dar lo mismo. Distinto de
 * {@code DependenciaDegradada}, que es no contestar y si se reintenta.
 */
public class RechazoDelServicio extends RuntimeException {

    private final int estado;

    public RechazoDelServicio(String servicio, int estado, String detalle) {
        super(servicio + " respondio " + estado + (detalle == null || detalle.isBlank() ? "" : ": " + detalle));
        this.estado = estado;
    }

    public int estado() {
        return estado;
    }
}
