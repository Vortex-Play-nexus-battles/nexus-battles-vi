package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorTerminosProhibidosServiceTest {

    private final DetectorTerminosProhibidosService detector = new DetectorTerminosProhibidosService();

    @Test
    void detectaTerminoProhibidoExacto() {
        boolean resultado = detector.contieneTermino("esto es una malapalabra grave", List.of("malapalabra", "insulto"));

        assertThat(resultado).isTrue();
    }

    @Test
    void noDetectaCuandoElTextoEstaLimpio() {
        boolean resultado = detector.contieneTermino("esto es un texto limpio", List.of("malapalabra", "insulto"));

        assertThat(resultado).isFalse();
    }

    @Test
    void detectaSinImportarMayusculasMinusculas() {
        boolean resultado = detector.contieneTermino("esto es una MALAPALABRA en mayusculas", List.of("malapalabra"));

        assertThat(resultado).isTrue();
    }

    @Test
    void detectaElTerminoProhibidoAunqueEsteEscondidoDentroDeOtraPalabra() {
        // Anti-evasion: el contexto del issue habla de "variaciones inventadas por
        // usuarios malintencionados" -- alguien puede escribir "malapalabrota" o
        // "xmalapalabrax" a proposito para esquivar el filtro. Debe detectarse igual.
        boolean resultado = detector.contieneTermino("esto tiene malapalabrazota adentro", List.of("malapalabra"));

        assertThat(resultado).isTrue();
    }

    @Test
    void detectaElTerminoProhibidoPegadoAOtrosCaracteres() {
        boolean resultado = detector.contieneTermino("xmalapalabrax", List.of("malapalabra"));

        assertThat(resultado).isTrue();
    }

    @Test
    void noDetectaCuandoLaListaDeTerminosEstaVacia() {
        boolean resultado = detector.contieneTermino("cualquier texto aqui", List.of());

        assertThat(resultado).isFalse();
    }

    @Test
    void detectaElTerminoAunqueElTextoNoLlevaLaTildeYElTerminoSi() {
        boolean resultado = detector.contieneTermino("ese camion es horrible", List.of("camión"));

        assertThat(resultado).isTrue();
    }

    @Test
    void detectaElTerminoAunqueElTerminoNoLlevaLaTildeYElTextoSi() {
        boolean resultado = detector.contieneTermino("qué malapalabra dijiste", List.of("malapalabra"));

        assertThat(resultado).isTrue();
    }
}
