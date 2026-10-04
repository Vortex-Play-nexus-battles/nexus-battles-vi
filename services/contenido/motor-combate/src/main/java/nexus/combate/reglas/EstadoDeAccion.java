package nexus.combate.reglas;

/**
 * Una accion que un combatiente podria jugar ahora, y por que no si no puede.
 * Lo calcula el motor para que la interfaz no reimplemente la regla.
 *
 * @param codigo         lo que se manda como accion
 * @param nombre         nombre del documento
 * @param tipo           clase de accion
 * @param esEpica        epica de la Tabla 20
 * @param costoPoder     coste; nulo si cuesta todo el poder
 * @param todoElPoder    consume todo el poder
 * @param turnosDeCarga  carga tras usarla
 * @param nivelRequerido nivel desde el que se tiene
 * @param disponible     se puede jugar ahora
 * @param motivo         por que no, apto para el jugador
 */
public record EstadoDeAccion(String codigo, String nombre, TipoDeAccion tipo, boolean esEpica,
                             Integer costoPoder, boolean todoElPoder, int turnosDeCarga,
                             int nivelRequerido, boolean disponible, String motivo) {
}
