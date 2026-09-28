package nexus.misiones.aplicacion;

/**
 * El servicio de correo ({@code POST /correos/mision}, correo.yaml 1.4.0):
 * plantilla corporativa, cola durable y {@code Idempotency-Key}, de modo que
 * reintentar la misma clave no encola dos correos.
 */
public interface CorreoDeMisiones {

    /** @throws RechazoDelServicio si correo lo rechaza de forma definitiva */
    void enviar(DirectorioDeJugadores.Contacto contacto, String asunto, String mensaje, String claveIdempotencia);
}
