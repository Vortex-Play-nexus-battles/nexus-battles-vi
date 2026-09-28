package nexus.misiones.aplicacion;

import nexus.misiones.dominio.Ejecucion;

/**
 * @param heroeLiberado falso si el inventario no contesto: la liberacion la
 *                      termina el trabajo en segundo plano
 * @param penalizacion  lo que costo cancelar, dicho para el jugador
 */
public record Cancelacion(Ejecucion ejecucion, boolean heroeLiberado, String penalizacion) {
}
