package com.nexusbattles.plataforma.correo.cola;

/**
 * Estados de un envio en la cola (contrato 1.4.0).
 *
 * <pre>
 *   PENDIENTE ──reclamar──▶ ENVIANDO ──▶ ENVIADO | DESVIADO | OMITIDO | FALLIDO
 *        ▲                     │
 *        └── ERROR_REINTENTABLE ◀─ fallo transitorio (espera y vuelve a la cola)
 * </pre>
 *
 * <p>Los cuatro de la derecha son terminales: el correo no se vuelve a tocar,
 * sus datos sensibles se borran y la purga de retencion lo elimina pasado el
 * plazo. {@code OMITIDO} puede nacer ya terminal: un correo de misiones o de
 * subastas que el jugador pidio no recibir se guarda como constancia, sin
 * enviarse.
 */
public enum EstadoDeEnvio {

    /** Guardado, esperando su primer intento. */
    PENDIENTE(false),
    /** Un trabajador lo reclamo y lo esta entregando. */
    ENVIANDO(false),
    /** El proveedor lo acepto. */
    ENVIADO(true),
    /** Fallo transitorio: espera a {@code proximo_intento} y vuelve a la cola. */
    ERROR_REINTENTABLE(false),
    /** Rechazo permanente, o se agotaron los intentos. */
    FALLIDO(true),
    /** Lo acepto el buzon de pruebas: era una direccion reservada (RFC 2606). */
    DESVIADO(true),
    /** No se envio a proposito. */
    OMITIDO(true);

    private final boolean terminal;

    EstadoDeEnvio(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean esTerminal() {
        return terminal;
    }
}
