package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Estado del ciclo de vida de una sala.
 *
 * <p>Se corresponde uno a uno con las variantes del componente {@code Insignia}
 * del sistema de diseno y con el enumerado {@code EstadoSala} de
 * {@code contracts/openapi/salas-partidas.yaml}. Si aqui aparece un valor nuevo,
 * el contrato y el componente tienen que cambiar tambien.
 */
public enum EstadoSala {

    /** Admite jugadores y aparece en el listado publico (RF-JUE-002). */
    ABIERTA,

    /** Alcanzo su maximo de participantes y ya no admite mas. */
    LLENA,

    /** La partida comenzo. */
    EN_JUEGO,

    /** No aparece en el listado: solo se entra con codigo de invitacion. */
    PRIVADA,

    /** El anfitrion la cancelo antes de empezar; los creditos se devuelven. */
    CANCELADA,

    /** La partida termino. */
    FINALIZADA
}
