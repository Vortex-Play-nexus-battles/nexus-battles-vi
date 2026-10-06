package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// Revision de plataforma (7.4.4): el inventario en vivo solo se consulta
// cuando el jugador pide SUS datos, no cuando pide orientacion.
class IntencionDeConsultaTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "¿Qué tengo en mi inventario?",
        "Muéstrame mis héroes",
        "como esta mi inventario",
        "muestrame mi inventario",
        "¿Cuántos héroes tengo?",
        "cuantos objetos tengo en mi inventario",
        "mis armas",
        "show me my items",
        "what items do i have in my inventory"
    })
    void pideSusDatosDeInventario(String mensaje) {
        assertThat(IntencionDeConsulta.consultaSuInventario(NormalizadorTexto.normalizar(mensaje))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Dame consejos para organizar mi inventario",
        "¿Qué objetos me recomiendas usar?",
        "tienes consejos de inventario",
        "¿Cómo puedo ordenar mi inventario?",
        "¿Cómo equipar a mis héroes?",
        "como equipo a mi heroe",
        "pierdo mis objetos",
        "lose my gear",
        "how should I organize my inventory",
        "how do I equip my heroes",
        "¿Qué es el inventario?",
        "como subo de nivel a mi heroe",
        "como me registro en el juego"
    })
    void noConsultaElInventarioCuandoPideOrientacionOHablaDeOtraCosa(String mensaje) {
        assertThat(IntencionDeConsulta.consultaSuInventario(NormalizadorTexto.normalizar(mensaje))).isFalse();
    }
}
