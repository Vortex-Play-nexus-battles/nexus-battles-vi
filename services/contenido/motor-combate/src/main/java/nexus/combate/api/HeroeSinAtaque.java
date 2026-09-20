package nexus.combate.api;

/**
 * El heroe existe pero no tiene formula de ataque — 422.
 *
 * <p>Es el caso de los sanadores: el catalogo los publica con
 * {@code ataqueDetalle: null}. No es un fallo de integracion ni un heroe
 * inexistente, y por eso no es 404 ni 503: es un heroe que no ataca.
 *
 * <p>Se distingue a proposito en vez de devolver dano cero. Un cero se confunde
 * con un ataque fallido, y quien lleva el combate necesita saber que ese heroe
 * nunca va a hacer dano con esta accion.
 */
public class HeroeSinAtaque extends RuntimeException {

    private final String heroe;

    public HeroeSinAtaque(String heroe) {
        super(heroe + " no tiene formula de ataque: es un heroe sanador.");
        this.heroe = heroe;
    }

    public String heroe() {
        return heroe;
    }
}
