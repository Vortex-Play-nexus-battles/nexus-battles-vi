package nexus.misiones.dominio.simulacion;

import java.util.List;

/**
 * El motor de combate ({@code motor-combate.yaml} 1.2.0), el MISMO que usan
 * las batallas en linea (salas-partidas): tiradas, tabla de efectos de 8.000
 * filas, poder, cargas, las 24 acciones de la Tabla 7, las epicas de la Tabla
 * 20 y los efectos del equipamiento. Aqui no hay un segundo motor (7.8.12).
 *
 * <p>Sin estado: cada llamada lleva el estado de todos los combatientes y
 * devuelve el estado nuevo de todos.
 */
public interface MotorDeCombate {

    /** El codigo de la accion basica de un heroe que ataca. */
    String ATAQUE_BASICO = "ATAQUE_BASICO";

    /** El codigo de la accion basica de un sanador (Tabla 6, «Sanar»). */
    String SANACION_BASICA = "SANACION_BASICA";

    /**
     * {@code POST /api/v1/combate/turnos}: empieza el turno de un combatiente.
     *
     * @param semilla solo en pruebas reproducibles; nula en juego real
     */
    ResultadoDeTurno iniciarTurno(String combatiente, List<Combatiente> combatientes, Long semilla);

    /**
     * {@code POST /api/v1/combate/acciones}: el ejecutor juega su accion.
     *
     * @param accion   {@link #ATAQUE_BASICO}, {@link #SANACION_BASICA}, una accion de la Tabla 7 por su nombre o una epica
     * @param objetivo a quien apunta; opcional si solo hay uno posible
     * @throws AccionNoPermitida si el motor la rechaza (409); no se aplico nada
     */
    ResultadoDeAccion resolverAccion(String accion, String ejecutor, String objetivo, List<Combatiente> combatientes,
                                     Long semilla);
}
