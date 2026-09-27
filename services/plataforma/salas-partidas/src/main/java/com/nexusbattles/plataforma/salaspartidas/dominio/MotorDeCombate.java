package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.UUID;

/**
 * Puerto hacia el motor de combate — {@code motor-combate.yaml} 1.2.0 (B7).
 *
 * <p>El motor es el dueno de las reglas de la seccion 6: tiradas, tabla de
 * efectos, poder, cargas, acciones de la Tabla 7, epicas y equipo. No guarda
 * nada: recibe el estado de todos los combatientes de la partida y devuelve el
 * nuevo. Este servicio lleva ese estado entre llamadas y decide solo lo que es
 * suyo: de quien es el turno y cuando termina la partida.
 *
 * <p>Los combatientes que se le mandan salen de la {@link Partida}: todos los
 * participantes con heroe y prototipo conocidos.
 */
public interface MotorDeCombate {

    /** Accion con la que la maquina juega su turno: decide el motor (D-B7-12). */
    String DECISION_DE_LA_MAQUINA = "DECISION_DE_LA_MAQUINA";

    /** La accion basica de un heroe que ataca. */
    String ATAQUE_BASICO = "ATAQUE_BASICO";

    /** La accion basica de un sanador (Tabla 6, «Sanar»). */
    String SANACION_BASICA = "SANACION_BASICA";

    /**
     * {@code POST /api/v1/combate/acciones}: el ejecutor juega su accion.
     *
     * @param accion   codigo de la accion ({@code ATAQUE_BASICO}, una de la Tabla 7,
     *                 una epica o {@link #DECISION_DE_LA_MAQUINA})
     * @param ejecutor quien actua
     * @param objetivo a quien, o {@code null} si solo hay uno posible
     * @param partida  el estado de todos
     * @throws AccionNoPermitida  si el motor rechaza la accion (409)
     * @throws MotorNoDisponible  si el motor respondio algo que no sirve
     */
    ResolucionDeAccion resolverAccion(String accion, UUID ejecutor, UUID objetivo, Partida partida);

    /**
     * {@code POST /api/v1/combate/turnos}: empieza el turno de un combatiente.
     *
     * @param combatiente   quien empieza
     * @param partida       el estado de todos
     * @param aVidaCompleta al empezar la partida: todos a su vida maxima en su
     *                      nivel, que calcula el motor
     * @throws MotorNoDisponible si el motor respondio algo que no sirve
     */
    InicioDeTurno iniciarTurno(UUID combatiente, Partida partida, boolean aVidaCompleta);
}
