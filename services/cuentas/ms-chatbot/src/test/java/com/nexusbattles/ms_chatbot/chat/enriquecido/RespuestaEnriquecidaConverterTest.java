package com.nexusbattles.ms_chatbot.chat.enriquecido;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.3.4 (V7): la respuesta enriquecida va y vuelve de la base.
class RespuestaEnriquecidaConverterTest {

    private final RespuestaEnriquecidaConverter conversor = new RespuestaEnriquecidaConverter();

    @Test
    void vaYVuelveIgual() {
        RespuestaEnriquecida original = new RespuestaEnriquecida(List.of("uno", "dos"),
            List.of(new EnlaceInterno("Ir a Subastas", "subastas")),
            List.of(new TarjetaInformativa("Título", "Texto", new EnlaceInterno("Ir", "misiones"))),
            List.of("Otra pregunta"), true);

        String json = conversor.convertToDatabaseColumn(original);

        assertThat(json).contains("\"destino\":\"subastas\"");
        assertThat(conversor.convertToEntityAttribute(json)).isEqualTo(original);
    }

    @Test
    void nullOVaciaSeGuardanComoNull() {
        assertThat(conversor.convertToDatabaseColumn(null)).isNull();
        assertThat(conversor.convertToDatabaseColumn(new RespuestaEnriquecida(null, null, null, null, false)))
            .isNull();
    }

    @Test
    void nullBlancoOIlegibleSeLeenComoNull() {
        assertThat(conversor.convertToEntityAttribute(null)).isNull();
        assertThat(conversor.convertToEntityAttribute("  ")).isNull();
        assertThat(conversor.convertToEntityAttribute("{no es json")).isNull();
    }

    @Test
    void unCampoQueFaltaQuedaComoListaVacia() {
        RespuestaEnriquecida leida = conversor.convertToEntityAttribute("{\"pasos\":[\"a\"]}");

        assertThat(leida.pasos()).containsExactly("a");
        assertThat(leida.enlaces()).isEmpty();
        assertThat(leida.ofrecerSoporteHumano()).isFalse();
    }
}
