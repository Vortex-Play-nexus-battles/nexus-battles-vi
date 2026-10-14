package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.IntencionDeConsulta.Dominio;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

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

    // 1.11.0: subastas, notificaciones, transacciones y misiones se
    // reconocen igual que el inventario, no solo por frases fijas.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "¿Tengo alguna misión activa? | MISIONES",
        "¿Qué misiones tengo en curso? | MISIONES",
        "what missions do i have | MISIONES",
        "¿Qué subastas tengo? | SUBASTAS",
        "¿Mi puja es la más alta? | SUBASTAS",
        "¿Tengo avisos nuevos? | NOTIFICACIONES",
        "¿Cuáles son mis últimas transacciones? | MOVIMIENTOS",
        "show my transactions | MOVIMIENTOS"
    })
    void pideSusDatosDeLosDemasDominios(String mensaje, Dominio dominio) {
        assertThat(IntencionDeConsulta.consultaSus(NormalizadorTexto.normalizar(mensaje), dominio)).as(mensaje)
            .isTrue();
    }

    // Una pregunta de reglas sobre lo suyo no es una consulta de datos.
    @ParameterizedTest
    @ValueSource(strings = {
        "¿Puedo cancelar mi puja?",
        "¿Qué pasa si abandono mi misión?",
        "¿Tengo que pagar para publicar una subasta?",
        "¿Por qué no veo mis notificaciones?",
        "¿Cómo publico mi subasta?",
        "¿Debo tener créditos para pujar en mis subastas?",
        "can I cancel my bid",
        "what happens if I leave my mission",
        "do I need credits to bid on my auctions",
        "why are my notifications gone",
        "¿Cuál es mi mejor movimiento en combate?",
        "¿Cómo subo de nivel a mi héroe?"
    })
    void unaPreguntaDeReglasSobreLoSuyoNoEsUnaConsulta(String mensaje) {
        String normalizado = NormalizadorTexto.normalizar(mensaje);
        assertThat(Stream.of(Dominio.values()).anyMatch(d -> IntencionDeConsulta.consultaSus(normalizado, d)))
            .as(mensaje).isFalse();
    }

    // «¿Puedo ver mis subastas?» pide verlas, no permiso.
    @ParameterizedTest
    @ValueSource(strings = {"¿Puedo ver mis subastas?", "can i see my notifications", "can I check my missions",
        "¿podría consultar mis transacciones?"})
    void mirarLoSuyoNoEsPedirPermiso(String mensaje) {
        assertThat(IntencionDeConsulta.pideReglas(NormalizadorTexto.normalizar(mensaje))).as(mensaje).isFalse();
    }
}
