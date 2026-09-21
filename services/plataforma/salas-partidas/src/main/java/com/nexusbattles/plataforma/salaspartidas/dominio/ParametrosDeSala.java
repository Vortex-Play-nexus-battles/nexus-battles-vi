package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Parametros con los que un jugador crea una sala.
 *
 * <p><b>RF-JUE-001 · Entradas:</b> «Numero de jugadores; recompensa
 * comprometida; inclusion de heroe controlado por inteligencia artificial».
 * Esas tres, y ninguna mas.
 *
 * <p><b>Las salas no tienen nombre.</b> No lo pide RF-JUE-001, ni ninguna regla
 * de negocio, ni la seccion 7.6 del Proyecto Integrador. RF-JUE-002 selecciona
 * la sala por su <i>identificador</i>. Y cuando el SRS quiere un nombre lo dice
 * con todas las letras: RF-TOR-003 exige que los equipos de torneo tengan «un
 * nombre y un avatar identificativos».
 *
 * <p>{@code privada} sale del flujo alternativo de RF-JUE-001 —«creacion de una
 * sala privada por invitacion»— y {@code tamanoEquipo} de RF-JUE-004.
 *
 * <p>Es un objeto de entrada sin comportamiento: no se valida a si mismo. La
 * validacion vive en {@link Sala#crear}, porque depende de reglas que cruzan
 * varios campos.
 *
 * <p><b>Heroes de la IA (HU-SAL-004).</b> RF-JUE-001 habla de «inclusion de
 * heroe controlado por IA» en singular, y RF-JUE-004 de partidas de hasta
 * seis «en las que cualquiera puede ser controlado por la inteligencia
 * artificial». Las dos cosas caben en un solo numero: cuantos de los cupos
 * son de la maquina. El booleano de RF-JUE-001 sigue aceptandose
 * ({@link #ParametrosDeSala(int, Modalidad, int, boolean, boolean, Integer)})
 * y vale por uno.
 *
 * @param maximoParticipantes  cuantos jugadores participaran (RF-JUE-001)
 * @param modalidad            modalidad de partida (RF-JUE-004)
 * @param recompensaCreditos   creditos puestos en juego (RF-JUE-001, RF-JUE-014)
 * @param heroesIA             cuantos cupos ocupa la IA (RF-JUE-001, RF-JUE-004)
 * @param privada              si es privada no aparece en el listado publico
 * @param tamanoEquipo         integrantes por equipo; solo en modalidad HASTA_SEIS
 */
public record ParametrosDeSala(
        int maximoParticipantes,
        Modalidad modalidad,
        int recompensaCreditos,
        int heroesIA,
        boolean privada,
        Integer tamanoEquipo) {

    /** La forma de RF-JUE-001: un heroe de la IA, o ninguno. */
    public ParametrosDeSala(int maximoParticipantes, Modalidad modalidad, int recompensaCreditos,
                            boolean incluirHeroeIA, boolean privada, Integer tamanoEquipo) {
        this(maximoParticipantes, modalidad, recompensaCreditos, incluirHeroeIA ? 1 : 0,
                privada, tamanoEquipo);
    }

    /** Si hay al menos un heroe de la IA. */
    public boolean incluirHeroeIA() {
        return heroesIA > 0;
    }
}
