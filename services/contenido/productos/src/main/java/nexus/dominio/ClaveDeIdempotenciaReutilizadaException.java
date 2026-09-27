package nexus.dominio;

/**
 * La misma {@code Idempotency-Key} llego con otra peticion — B4. Es 409: la
 * clave ya significa otra cosa, y reintentar con ella va a fallar igual.
 */
public class ClaveDeIdempotenciaReutilizadaException extends RuntimeException {

        public ClaveDeIdempotenciaReutilizadaException() {
                super("La clave de idempotencia ya se uso para adquirir otro producto.");
        }
}
