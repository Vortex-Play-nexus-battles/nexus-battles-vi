package nexus.misiones.aplicacion;

/**
 * El libro de creditos de ms-finanzas ({@code POST /creditos/acreditar},
 * creditos.yaml 1.4.0): idempotente por {@code refId}, asi que reintentar el
 * mismo {@code refId} nunca suma dos veces.
 */
public interface LibroDeCreditos {

    /** @throws RechazoDelServicio si el libro lo rechaza de forma definitiva */
    void acreditar(String jugadorUid, int monto, String refId, String concepto);
}
