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

    /** Duelo directo entre dos personas: sin maquina. Con maquina seria CONTRA_IA. */
    UNO_CONTRA_UNO(2, 2, false, 0, 0),

    /** El jugador se enfrenta a un rival controlado por la inteligencia artificial: exactamente una. */
    CONTRA_IA(2, 2, false, 1, 1),

    /**
     * Combate multiple. Admite equipos, con el maximo de tres que fija
     * RF-JUE-004, y «cualquiera puede ser controlado por la IA»: de cero
     * maquinas a todos los cupos menos el del anfitrion.
     */
    HASTA_SEIS(2, 6, true, 0, 5);

    /** Maximo de integrantes por equipo en modo cooperativo (RF-JUE-004). */
    public static final int MAXIMO_POR_EQUIPO = 3;

    private final int minimoParticipantes;
    private final int maximoParticipantes;
    private final boolean admiteEquipos;
    private final int minimoHeroesIA;
    private final int maximoHeroesIA;

    Modalidad(int minimoParticipantes, int maximoParticipantes, boolean admiteEquipos,
              int minimoHeroesIA, int maximoHeroesIA) {
        this.minimoParticipantes = minimoParticipantes;
        this.maximoParticipantes = maximoParticipantes;
        this.admiteEquipos = admiteEquipos;
        this.minimoHeroesIA = minimoHeroesIA;
        this.maximoHeroesIA = maximoHeroesIA;
    }

    /** Cuantas maquinas lleva como minimo: una en CONTRA_IA, ninguna en las demas. */
    public int minimoHeroesIA() {
        return minimoHeroesIA;
    }

    /**
     * Cuantas maquinas caben, para un aforo dado. El anfitrion siempre juega,
     * asi que nunca son todos los cupos.
     */
    public int maximoHeroesIA(int maximoParticipantes) {
        return Math.min(maximoHeroesIA, maximoParticipantes - 1);
    }

    /** Si la modalidad es, por definicion, contra la maquina. */
    public boolean exigeHeroeIA() {
        return minimoHeroesIA > 0;
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
