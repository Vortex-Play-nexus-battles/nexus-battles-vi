package nexus.dominio;

/**
 * Estadisticas base de nivel 1 segun la Tabla 6 (seccion 6.1.1, p. 26).
 * ataque y dano son null en los sanadores; sanar es null en el resto.
 */
public record Estadisticas(int poder, int vida, int defensa, Formula ataque, Formula dano, Formula sanar) {

    /**
     * Escalado por nivel (HU-HER-008): multiplica poder, vida, defensa y las
     * bases de las formulas; los dados no se escalan (el ejemplo del cliente
     * multiplica el 10 base del mago de fuego, no su 1d8).
     */
    public Estadisticas escaladaPor(int nivel) {
        return new Estadisticas(
                poder * nivel, vida * nivel, defensa * nivel,
                ataque == null ? null : ataque.conBaseMultiplicadaPor(nivel),
                dano == null ? null : dano.conBaseMultiplicadaPor(nivel),
                sanar == null ? null : sanar.conBaseMultiplicadaPor(nivel));
    }
}
