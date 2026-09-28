package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Estadisticas de un heroe en su nivel y con el equipamiento plano aplicado —
 * esquema {@code EstadisticasDeCombate} de {@code motor-combate.yaml} 1.2.0.
 *
 * <p>Este servicio no las calcula: las compone de lo que publican inventario
 * (el efecto del equipamiento, HU-INV-006) y el catalogo de heroes (el
 * escalado por nivel, HU-HER-008), y se las manda al motor tal cual. Una
 * formula ausente es un heroe que no la tiene (un sanador no ataca).
 *
 * @param poder   poder maximo
 * @param vida    vida maxima
 * @param defensa defensa
 * @param ataque  formula de ataque, o {@code null}
 * @param dano    formula de dano, o {@code null}
 * @param sanar   formula de sanacion, o {@code null}
 */
public record EstadisticasDeCombate(int poder, int vida, int defensa, Formula ataque, Formula dano,
                                    Formula sanar) {

    public EstadisticasDeCombate {
        if (poder < 0 || defensa < 0) {
            throw new IllegalArgumentException("Ni el poder ni la defensa pueden ser negativos.");
        }
        if (vida < 1) {
            throw new IllegalArgumentException("Una vida maxima menor que uno no puede combatir.");
        }
    }

    /** {@code base + cantidadDados d caras}. */
    public record Formula(int base, int cantidadDados, int caras) {

        public Formula {
            if (base < 0 || cantidadDados < 0 || caras < 0) {
                throw new IllegalArgumentException("Una formula no admite valores negativos.");
            }
        }
    }
}
