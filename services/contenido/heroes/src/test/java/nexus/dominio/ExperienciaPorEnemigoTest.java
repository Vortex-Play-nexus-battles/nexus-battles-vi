package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * HU-HER-004 — Experiencia por enemigo derrotado.
 * Fuente textual del documento oficial (heroes.md, seccion 6.1.1, p. 26):
 * "La derrota de un enemigo no jugador otorga 10 x 1,2^(1d8) puntos de
 * experiencia", con la nota del proyecto: si el lanzamiento es 5, la
 * experiencia es 10 x 1,2^5. La funcion recibe el resultado del dado para
 * ser determinista; el lanzamiento aleatorio pertenece a la mecanica del
 * combate. (RG-020 sigue como pregunta abierta al cliente; esta historia
 * implementa la letra del documento y de sus criterios radicados.)
 */
class ExperienciaPorEnemigoTest {

    @ParameterizedTest
    @CsvSource({
            "1, 12.0",
            "2, 14.4",
            "5, 24.883",
            "8, 42.998"})
    @DisplayName("la experiencia por enemigo derrotado sigue la formula 10 x 1,2^(1d8)")
    void formulaTextualDelDocumento(int resultadoDelDado, double esperada) {
        assertEquals(esperada, Heroe.experienciaPorEnemigoDerrotado(resultadoDelDado), 0.001);
    }

    @Test
    @DisplayName("el resultado del dado debe estar en el rango 1 a 8")
    void rangoDelDado() {
        assertThrows(IllegalArgumentException.class, () -> Heroe.experienciaPorEnemigoDerrotado(0));
        assertThrows(IllegalArgumentException.class, () -> Heroe.experienciaPorEnemigoDerrotado(9));
    }

    @Test
    @DisplayName("la experiencia del enemigo derrotado alimenta la progresion del heroe")
    void alimentaLaProgresion() {
        Heroe heroe = Heroe.crear(PrototiposIniciales.LISTA.get(0));
        // Nueve victorias con dado 8 (42.998 c/u) acumulan 386.99 puntos:
        // cruzan los umbrales 100 + 120 + 144 = 364 -> nivel 4, con sobrante
        // insuficiente para el 172.8 del siguiente nivel.
        for (int i = 0; i < 9; i++) {
            heroe = heroe.ganarExperiencia(Heroe.experienciaPorEnemigoDerrotado(8));
        }
        assertEquals(4, heroe.nivel());
    }
}
