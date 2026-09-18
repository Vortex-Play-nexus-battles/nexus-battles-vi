package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.Objects;

/**
 * El heroe con el que un jugador entraria a combatir.
 *
 * <p>Espejo del esquema {@code HeroeEnPartida} de
 * {@code contracts/openapi/salas-partidas.yaml}: solo lo que la sala y la vista
 * de batalla pintan. La ficha completa del heroe pertenece al modulo de
 * contenido y no se copia aqui — este servicio no es su dueno y no la guarda.
 *
 * <p>{@code retratoUrl} y {@code nivel} pueden faltar: el inventario no los
 * publica en su vitrina, y el contrato los declara anulables justamente porque
 * quien los tiene es el catalogo de prototipos, no el inventario del jugador.
 *
 * @param id          identificador del heroe en el inventario del jugador
 * @param nombre      nombre propio que le puso su dueno
 * @param retratoUrl  retrato para la vista de batalla, o {@code null}
 * @param nivel       nivel del heroe, o {@code null} si no se conoce
 * @param vidaActual  vida con la que llega a la sala
 * @param vidaMaxima  vida maxima con su equipamiento aplicado
 */
public record HeroeDeCombate(
        String id,
        String nombre,
        String retratoUrl,
        Integer nivel,
        int vidaActual,
        int vidaMaxima) {

    public HeroeDeCombate {
        Objects.requireNonNull(id, "Un heroe sin identificador no se puede llevar a una sala.");
        Objects.requireNonNull(nombre, "El dialogo de verificacion nombra al heroe: hace falta su nombre.");
        if (vidaMaxima < 1) {
            throw new IllegalArgumentException("Un heroe con vida maxima menor que uno no puede combatir.");
        }
        if (vidaActual < 0) {
            throw new IllegalArgumentException("La vida actual no puede ser negativa.");
        }
    }

    /**
     * Heroe a pleno: antes de empezar la partida la vida actual es la maxima.
     *
     * <p>La vida que se muestra en el dialogo previo no es la de un combate en
     * curso —todavia no hay combate—, asi que las dos coinciden. La barra de
     * HU-SAL-005 muestra siempre el numero junto al color (RF-JUE-009), y para
     * eso necesita las dos cifras aunque de momento sean la misma.
     */
    public static HeroeDeCombate aPleno(String id, String nombre, int vidaMaxima) {
        return new HeroeDeCombate(id, nombre, null, null, vidaMaxima, vidaMaxima);
    }
}
