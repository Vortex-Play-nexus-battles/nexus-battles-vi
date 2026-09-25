package nexus.combate.reglas;

/**
 * Estadisticas de combate de un heroe EN SU NIVEL (Tabla 6 escalada, §6.1.1).
 *
 * <p>Cuando las manda quien lleva la partida vienen de inventario, con el
 * equipamiento plano ya aplicado (+1 al ataque de la Espada de una mano, +2 de
 * vida de una armadura...). Cuando no, el motor las pide al catalogo de heroes
 * en ese nivel, sin equipo.
 *
 * @param poder   poder maximo
 * @param vida    vida maxima
 * @param defensa defensa
 * @param ataque  formula de ataque; nula en los sanadores
 * @param dano    formula de dano; nula en los sanadores
 * @param sanar   formula de sanacion; solo los sanadores
 */
public record Estadisticas(int poder, int vida, int defensa, Formula ataque, Formula dano, Formula sanar) {

    public Estadisticas {
        if (poder < 0 || defensa < 0) {
            throw new IllegalArgumentException("Ni el poder ni la defensa pueden ser negativos.");
        }
        if (vida < 1) {
            throw new IllegalArgumentException("Una vida maxima menor que uno no puede combatir.");
        }
    }

    /** Tiene formula de ataque: puede golpear. */
    public boolean ataca() {
        return ataque != null;
    }

    /** Tiene formula de sanacion (Tabla 6, «Sanar»). */
    public boolean sana() {
        return sanar != null;
    }
}
