package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Modalidades de partida admitidas.
 *
 * <p><b>RF-JUE-004:</b> «partidas uno contra uno, uno contra inteligencia
 * artificial y partidas de hasta seis (6) jugadores en las que cualquiera puede
 * ser controlado por la inteligencia artificial, con equipos de un maximo de
 * tres (3) integrantes en el modo cooperativo».
 *
 * <p>Los limites de participantes viven aqui y no en la validacion del
 * controlador: son una regla del juego, no una regla del formulario.
 */
public enum Modalidad {

    /** Duelo directo entre dos heroes. */
    UNO_CONTRA_UNO(2, 2, false),

    /** El jugador se enfrenta a un rival controlado por la inteligencia artificial. */
    CONTRA_IA(2, 2, false),

    /** Combate multiple. Admite equipos, con el maximo de tres que fija RF-JUE-004. */
    HASTA_SEIS(2, 6, true);

    /** Maximo de integrantes por equipo en modo cooperativo (RF-JUE-004). */
    public static final int MAXIMO_POR_EQUIPO = 3;

    private final int minimoParticipantes;
    private final int maximoParticipantes;
    private final boolean admiteEquipos;

    Modalidad(int minimoParticipantes, int maximoParticipantes, boolean admiteEquipos) {
        this.minimoParticipantes = minimoParticipantes;
        this.maximoParticipantes = maximoParticipantes;
        this.admiteEquipos = admiteEquipos;
    }

    public int minimoParticipantes() {
        return minimoParticipantes;
    }

    public int maximoParticipantes() {
        return maximoParticipantes;
    }

    /** Solo la modalidad de hasta seis admite equipos. */
    public boolean admiteEquipos() {
        return admiteEquipos;
    }

    public boolean admite(int participantes) {
        return participantes >= minimoParticipantes && participantes <= maximoParticipantes;
    }
}
