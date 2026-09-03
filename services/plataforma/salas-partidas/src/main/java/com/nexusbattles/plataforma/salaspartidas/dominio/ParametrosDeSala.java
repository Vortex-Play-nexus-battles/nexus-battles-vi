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
 * @param maximoParticipantes  cuantos jugadores participaran (RF-JUE-001)
 * @param modalidad            modalidad de partida (RF-JUE-004)
 * @param recompensaCreditos   creditos puestos en juego (RF-JUE-001, RF-JUE-014)
 * @param incluirHeroeIA       incluir un heroe aleatorio de la IA (RF-JUE-001)
 * @param privada              si es privada no aparece en el listado publico
 * @param tamanoEquipo         integrantes por equipo; solo en modalidad HASTA_SEIS
 */
public record ParametrosDeSala(
        int maximoParticipantes,
        Modalidad modalidad,
        int recompensaCreditos,
        boolean incluirHeroeIA,
        boolean privada,
        Integer tamanoEquipo) {
}
