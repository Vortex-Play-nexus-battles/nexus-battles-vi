package nexus.combate.reglas;

/**
 * Un combatiente cuya vida cambio.
 *
 * @param id          quien
 * @param vidaAntes   vida al empezar la accion
 * @param vidaDespues vida al terminar
 */
public record Afectado(String id, int vidaAntes, int vidaDespues) {

    /** Negativa si perdio vida, positiva si la recupero. */
    public int diferencia() {
        return vidaDespues - vidaAntes;
    }
}
