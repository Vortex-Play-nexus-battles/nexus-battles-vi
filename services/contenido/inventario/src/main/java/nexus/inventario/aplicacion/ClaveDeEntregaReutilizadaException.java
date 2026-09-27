package nexus.inventario.aplicacion;

/**
 * La misma {@code Idempotency-Key} llego con otro cuerpo — B4. Es 409: la clave
 * ya nombra otra entrega, y reintentar con ella va a fallar igual.
 */
public class ClaveDeEntregaReutilizadaException extends RuntimeException {

    public ClaveDeEntregaReutilizadaException() {
        super("La clave de idempotencia ya se uso para otra entrega.");
    }
}
