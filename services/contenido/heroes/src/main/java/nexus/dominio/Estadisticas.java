package nexus.dominio;

/**
 * Estadisticas base de nivel 1 segun la Tabla 6 (seccion 6.1.1, p. 26).
 * ataque y dano son null en los sanadores; sanar es null en el resto.
 */
public record Estadisticas(int poder, int vida, int defensa, Formula ataque, Formula dano, Formula sanar) {
}
