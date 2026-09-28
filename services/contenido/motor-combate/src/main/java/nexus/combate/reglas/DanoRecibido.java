package nexus.combate.reglas;

/**
 * El ultimo golpe que recibio un combatiente, y de quien. Lo necesita «Pare de
 * fuego»: «retorna el (0dx) dano causado por el oponente en el turno
 * anterior».
 *
 * @param de       {@code id} de quien golpeo
 * @param cantidad cuanta vida le quito
 */
public record DanoRecibido(String de, int cantidad) {

    public DanoRecibido {
        if (de == null || de.isBlank()) {
            throw new IllegalArgumentException("Un golpe recibido viene de alguien.");
        }
        if (cantidad < 0) {
            throw new IllegalArgumentException("El dano recibido no puede ser negativo.");
        }
    }
}
