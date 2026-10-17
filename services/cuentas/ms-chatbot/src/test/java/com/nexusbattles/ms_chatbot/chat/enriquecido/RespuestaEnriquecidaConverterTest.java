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

    // 1.12.0 (RF-CHA-007): la solicitud que se abrio sola va y vuelve con la
    // respuesta (el historial la enseña igual), aunque sea lo unico que trae.
    @Test
    void laSolicitudVaYVuelveYBastaParaGuardarla() {
        SolicitudAbierta solicitud = new SolicitudAbierta(
            java.util.UUID.fromString("6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f"), "Sin respuesta del asistente: x",
            "ABIERTO", true);
        RespuestaEnriquecida soloLaSolicitud = new RespuestaEnriquecida(null, null, null, null, false)
            .conSolicitud(solicitud);

        assertThat(soloLaSolicitud.estaVacia()).isFalse();
        String json = conversor.convertToDatabaseColumn(soloLaSolicitud);
        assertThat(json).contains("\"solicitud\":{").contains("\"nueva\":true");
        assertThat(conversor.convertToEntityAttribute(json)).isEqualTo(soloLaSolicitud);
    }

    @Test
    void unaRespuestaGuardadaAntesDe112SeLeeSinSolicitud() {
        RespuestaEnriquecida leida = conversor.convertToEntityAttribute(
            "{\"pasos\":[],\"enlaces\":[],\"tarjetas\":[],\"respuestasRapidas\":[],\"ofrecerSoporteHumano\":true}");

        assertThat(leida.solicitud()).isNull();
        assertThat(leida.ofrecerSoporteHumano()).isTrue();
    }

    // 1.13.0: la imagen de la tarjeta va y vuelve; sin imagen no se escribe,
    // asi que las tarjetas de siempre se guardan igual que antes.
    @Test
    void laImagenDeLaTarjetaVaYVuelveYSinImagenNoSeEscribe() {
        RespuestaEnriquecida conImagen = new RespuestaEnriquecida(null, null,
            List.of(new TarjetaInformativa("Escudo", "Armadura · 60 créditos", null,
                "/api/v1/productos/recursos/6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f")), null, false);
        RespuestaEnriquecida sinImagen = new RespuestaEnriquecida(null, null,
            List.of(new TarjetaInformativa("Mision", "En curso", null)), null, false);

        String json = conversor.convertToDatabaseColumn(conImagen);
        assertThat(json).contains("\"imagen\":\"/api/v1/productos/recursos/");
        assertThat(conversor.convertToEntityAttribute(json)).isEqualTo(conImagen);
        assertThat(conversor.convertToDatabaseColumn(sinImagen)).doesNotContain("imagen");
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

    // 1.8.0: el ancla solo se escribe si la hay, y lo guardado antes (sin
    // ancla) se sigue leyendo igual.
    @Test
    void elAnclaSoloSeGuardaSiLaHayYLoDeAntesSeLeeSinElla() {
        RespuestaEnriquecida conAncla = new RespuestaEnriquecida(null,
            List.of(new EnlaceInterno("Ir a Subastas", "subastas"),
                new EnlaceInterno("Ver el centro de ayuda", "ayuda", "categoria-faq-general")), null, null, true);

        String json = conversor.convertToDatabaseColumn(conAncla);

        assertThat(json).contains("{\"texto\":\"Ir a Subastas\",\"destino\":\"subastas\"}")
            .contains("\"ancla\":\"categoria-faq-general\"");
        assertThat(conversor.convertToEntityAttribute(json)).isEqualTo(conAncla);
        assertThat(conversor.convertToEntityAttribute(
            "{\"enlaces\":[{\"texto\":\"Ir a Torneo\",\"destino\":\"torneos\"}],\"ofrecerSoporteHumano\":false}")
            .enlaces()).containsExactly(new EnlaceInterno("Ir a Torneo", "torneos"));
    }

    @Test
    void unCampoQueFaltaQuedaComoListaVacia() {
        RespuestaEnriquecida leida = conversor.convertToEntityAttribute("{\"pasos\":[\"a\"]}");

        assertThat(leida.pasos()).containsExactly("a");
        assertThat(leida.enlaces()).isEmpty();
        assertThat(leida.ofrecerSoporteHumano()).isFalse();
    }
}
