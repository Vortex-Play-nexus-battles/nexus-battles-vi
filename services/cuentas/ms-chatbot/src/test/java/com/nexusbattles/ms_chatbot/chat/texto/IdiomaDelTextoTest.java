package com.nexusbattles.ms_chatbot.chat.texto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 7.4.2 «comprender consultas en español/english»: el idioma de la pregunta.
class IdiomaDelTextoTest {

    private static boolean esIngles(String texto) {
        return IdiomaDelTexto.esIngles(NormalizadorTexto.normalizar(texto));
    }

    @Test
    void reconoceElInglesPorSusPalabrasGramaticales() {
        assertThat(esIngles("How do I equip my hero?")).isTrue();
        assertThat(esIngles("what does Espada de una mano do")).isTrue();
        assertThat(esIngles("show me my missions")).isTrue();
    }

    @Test
    void anteLaDudaEsEspanolQueEsElIdiomaDeLaInterfaz() {
        assertThat(esIngles("¿Cómo equipo a mi héroe?")).isFalse();
        assertThat(esIngles("torneo")).isFalse();
        assertThat(esIngles("mago fuego")).isFalse();
        assertThat(esIngles("")).isFalse();
        assertThat(IdiomaDelTexto.esIngles(null)).isFalse();
        // Empate: una palabra de cada idioma.
        assertThat(esIngles("my inventario es")).isFalse();
    }
}
