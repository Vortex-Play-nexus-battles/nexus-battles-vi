package nexus.misiones.dominio.simulacion;

/**
 * Las estadisticas con las que un combatiente entra al motor: en su nivel y,
 * en el heroe, con el equipamiento plano ya aplicado (las publica inventario).
 * Las formulas son las del motor ({@code EstadisticasDeCombate} de
 * motor-combate.yaml): sin la de ataque un combatiente no puede golpear, asi
 * que nunca se manda una parte sola.
 *
 * @param poder   poder maximo
 * @param vida    vida maxima
 * @param defensa defensa
 * @param ataque  formula de ataque; nula en los sanadores
 * @param dano    formula de dano; nula en los sanadores
 * @param sanar   formula de sanacion; solo en los sanadores
 */
public record EstadisticasDeCombate(int poder, int vida, int defensa, Formula ataque, Formula dano, Formula sanar) {
}
