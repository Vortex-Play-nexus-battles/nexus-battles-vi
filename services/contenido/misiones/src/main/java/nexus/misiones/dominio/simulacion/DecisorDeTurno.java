package nexus.misiones.dominio.simulacion;

/**
 * Quien decide la jugada de cada turno: la logica de rotaciones de la seccion
 * 7.8.5 (Rotacion 1, si no es viable la 2, luego la 3, y si ninguna, ataque
 * basico sin gastar poder). NO se reimplementa aqui: la resuelve el servicio de
 * heroes ({@code POST /api/v1/estrategias/decision}, HU-SIM-002), que es su
 * dueno.
 */
public interface DecisorDeTurno {

    DecisionDeTurno decidir(TurnoParaDecidir turno);
}
