package com.nexusbattles.ms_finanzas.transacciones;

/**
 * Se lanza cuando {@link TransaccionRegistroService#registrar} recibe un
 * {@code refId} que ya existe en la tabla {@code transacciones}. Garantiza la
 * idempotencia del contrato: un reintento por timeout no cobra dos veces.
 *
 * <p>La capa REST (PR posterior) la traduce a un {@code ProblemDetail} 409
 * Conflict; para HU-PAG-001, Juan Diego puede resolverla llamando
 * {@link TransaccionRegistroService#buscarPorRefId} para recuperar el estado
 * ya persistido y devolverlo al llamador original.
 */
public class TransaccionYaRegistradaException extends RuntimeException {

    private final String refId;

    public TransaccionYaRegistradaException(String refId) {
        super("Ya existe una transacción con refId=" + refId);
        this.refId = refId;
    }

    public String getRefId() {
        return refId;
    }
}
