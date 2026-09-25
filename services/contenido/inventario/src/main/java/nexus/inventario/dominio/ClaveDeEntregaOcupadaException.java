package nexus.inventario.dominio;

/**
 * Otra peticion registro la misma clave de idempotencia primero (el indice
 * unico de {@code entregas.clave} lo impidio). No es un error para el
 * cliente: quien lo recibe relee la entrega registrada y sigue con ella.
 */
public class ClaveDeEntregaOcupadaException extends RuntimeException {

    public ClaveDeEntregaOcupadaException(Throwable causa) {
        super("La clave de la entrega ya estaba registrada.", causa);
    }
}
