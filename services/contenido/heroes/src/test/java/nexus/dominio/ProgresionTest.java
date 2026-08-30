package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * HU-HER-003 — Subida de nivel por experiencia.
 * Fuente: heroes.md seccion 6.1.1, p. 26: "Experiencia = 100 x 1,2^(Nivel - 1)";
 * "El nivel inicial de todos los personajes es uno (1) y puede incrementarse
 * hasta el nivel 8".
 */
class ProgresionTest {

    private static Heroe nuevoHeroe() {
        return Heroe.crear(PrototiposIniciales.LISTA.get(0));
    }

    // Criterio 1: la formula textual del documento.
    @ParameterizedTest
    @CsvSource({
            "1, 100.0",
            "2, 120.0",
            "3, 144.0",
            "4, 172.8",
            "7, 298.598"})
    @DisplayName("la experiencia requerida sigue la formula 100 x 1,2^(N-1)")
    void experienciaRequeridaSigueLaFormula(int nivel, double esperada) {
        assertEquals(esperada, Heroe.experienciaRequerida(nivel), 0.001);
    }

    @Test
    @DisplayName("al alcanzar la experiencia requerida el heroe sube de nivel")
    void subeDeNivelAlAlcanzarLaExperiencia() {
        Heroe heroe = nuevoHeroe().ganarExperiencia(100);
        assertEquals(2, heroe.nivel());
        assertEquals(0, heroe.experiencia(), 0.001);
    }

    @Test
    @DisplayName("la experiencia sobrante se conserva y puede encadenar niveles")
    void experienciaSobranteSeConserva() {
        // 250 desde nivel 1: 250-100 -> nivel 2 con 150; 150-120 -> nivel 3 con 30.
        Heroe heroe = nuevoHeroe().ganarExperiencia(250);
        assertEquals(3, heroe.nivel());
        assertEquals(30, heroe.experiencia(), 0.001);
    }

    // Criterio 2: parte del nivel 1 y nunca supera el 8.
    @Test
    @DisplayName("el heroe parte del nivel 1")
    void parteDelNivel1() {
        assertEquals(1, nuevoHeroe().nivel());
        assertEquals(0, nuevoHeroe().experiencia(), 0.001);
    }

    @Test
    @DisplayName("el nivel nunca supera el 8 por mucha experiencia que gane")
    void nuncaSuperaElNivel8() {
        Heroe heroe = nuevoHeroe().ganarExperiencia(1_000_000);
        assertEquals(Heroe.NIVEL_MAXIMO, heroe.nivel());
        assertEquals(8, Heroe.NIVEL_MAXIMO);
    }

    @Test
    @DisplayName("la experiencia ganada no puede ser negativa")
    void experienciaNegativaRechazada() {
        assertThrows(IllegalArgumentException.class, () -> nuevoHeroe().ganarExperiencia(-5));
    }
}
