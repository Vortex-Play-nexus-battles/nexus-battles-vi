package com.nexusbattles.ms_chatbot.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// 1.5.0 — gestion de dialogo por reglas: que mensajes se refieren a la
// respuesta anterior y cuales son una pregunta nueva.
class SeguimientoTest {

    @Test
    void reconoceCadaSeguimientoConSuRelleno() {
        assertThat(Seguimiento.de("Más detalle, por favor")).contains(Seguimiento.MAS_DETALLE);
        assertThat(Seguimiento.de("explícame mejor eso")).contains(Seguimiento.MAS_DETALLE);
        assertThat(Seguimiento.de("Can you tell me more about it?")).contains(Seguimiento.MAS_DETALLE);
        assertThat(Seguimiento.de("dímelo en inglés")).contains(Seguimiento.EN_INGLES);
        assertThat(Seguimiento.de("English please")).contains(Seguimiento.EN_INGLES);
        assertThat(Seguimiento.de("respóndeme en español")).contains(Seguimiento.EN_ESPANOL);
        assertThat(Seguimiento.de("in Spanish")).contains(Seguimiento.EN_ESPANOL);
        assertThat(Seguimiento.de("¿Puedes repetir?")).contains(Seguimiento.REPETIR);
        assertThat(Seguimiento.de("say that again")).contains(Seguimiento.REPETIR);
        assertThat(Seguimiento.de("No entendí nada")).contains(Seguimiento.NO_SIRVIO);
        assertThat(Seguimiento.de("eso no")).contains(Seguimiento.NO_SIRVIO);
    }

    // El normalizador separa el apostrofo; las contracciones se reconocen igual.
    @Test
    void reconoceLasContraccionesInglesas() {
        assertThat(Seguimiento.de("I don't understand")).contains(Seguimiento.NO_SIRVIO);
        assertThat(Seguimiento.de("that didn't help")).contains(Seguimiento.NO_SIRVIO);
        assertThat(Seguimiento.de("that's not it")).contains(Seguimiento.NO_SIRVIO);
        assertThat(Seguimiento.de("i dont understand")).contains(Seguimiento.NO_SIRVIO);
    }

    // 7.4.2: un error de tecleo por palabra larga se tolera.
    @Test
    void toleraUnErrorDeTecleoEnLasPalabrasLargas() {
        assertThat(Seguimiento.de("mas detalel")).contains(Seguimiento.MAS_DETALLE);
        assertThat(Seguimiento.de("en ignles")).contains(Seguimiento.EN_INGLES);
        assertThat(Seguimiento.de("repitleo")).contains(Seguimiento.REPETIR);
    }

    // Con una palabra de tema ya es otra pregunta: sigue el camino normal.
    @ParameterizedTest
    @ValueSource(strings = {
        "explícame mejor cómo publicar una subasta",
        "quiero jugar de nuevo",
        "amplía el inventario",
        "play again",
        "how do I win against mages",
        "useless heroes",
        "no sirve la poción",
        "repite la misión",
        "tell me more about auctions",
        "cómo se dice espada en inglés",
        "más",
        "por favor",
        "no",
        "   ",
        ""
    })
    void unaPreguntaNuevaNoEsSeguimiento(String mensaje) {
        assertThat(Seguimiento.de(mensaje)).isEmpty();
    }

    @Test
    void sinMensajeNoHaySeguimiento() {
        assertThat(Seguimiento.de(null)).isEmpty();
    }
}
