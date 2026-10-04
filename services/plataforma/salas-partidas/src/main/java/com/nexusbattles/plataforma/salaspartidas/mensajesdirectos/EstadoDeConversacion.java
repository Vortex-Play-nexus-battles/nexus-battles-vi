package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

/**
 * Si en una conversacion privada se puede escribir — {@code estado} de
 * {@code ResumenDeConversacion} en {@code salas-partidas.yaml} 1.8.0.
 *
 * <p>Los nombres son los que ya pintaba la interfaz (UXC-6,
 * {@code mensajes-privados.js}) antes de que el servicio supiera bloquear:
 * {@code BLOQUEADA} para quien bloqueo y {@code NO_ADMITE} para quien fue
 * bloqueado, sin decirle por que (D-40).
 */
public enum EstadoDeConversacion {

    /** Los dos pueden escribirse. */
    ACTIVA,

    /** Quien mira bloqueo al otro: ninguno de los dos escribe hasta que lo desbloquee. */
    BLOQUEADA,

    /** El otro bloqueo a quien mira: no le llegan sus mensajes. */
    NO_ADMITE
}
