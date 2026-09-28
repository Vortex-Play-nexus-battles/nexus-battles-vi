package nexus.combate;

/**
 * Lo que {@code POST /combate/ataques} necesita del catalogo de heroes: la
 * defensa, la formula de ataque y, desde 1.2.0, la de DANO (Tabla 6: «Daño: la
 * cantidad de daño que el héroe inflige a un enemigo con un ataque exitoso»).
 *
 * @param danoDetalle nula en un sanador, que no ataca
 */
public record EstadisticasHeroeRespuesta(int defensa, DetalleAtaque ataqueDetalle, DetalleAtaque danoDetalle) {

    /** Sin formula de dano: un catalogo anterior, o una prueba que no la necesita. */
    public EstadisticasHeroeRespuesta(int defensa, DetalleAtaque ataqueDetalle) {
        this(defensa, ataqueDetalle, null);
    }
}
