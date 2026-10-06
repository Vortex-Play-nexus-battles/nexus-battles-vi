package com.nexusbattles.ms_chatbot.chat.enriquecido;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

// ms-chatbot.yaml 1.3.4: la respuesta enriquecida se guarda con el mensaje
// del bot, como JSON en una columna TEXT (V7), para que el historial la
// vuelva a pintar igual. Una vacia se guarda como null.
@Converter
public class RespuestaEnriquecidaConverter implements AttributeConverter<RespuestaEnriquecida, String> {

    private static final Logger log = LoggerFactory.getLogger(RespuestaEnriquecidaConverter.class);
    // Jackson 3 rechaza por omision un booleano que falta en el JSON; aqui un
    // campo que falta vale su valor por omision (false), como en Jackson 2,
    // para que un JSON guardado antes o incompleto se siga leyendo.
    private static final JsonMapper JSON = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();

    @Override
    public String convertToDatabaseColumn(RespuestaEnriquecida enriquecida) {
        if (enriquecida == null || enriquecida.estaVacia()) {
            return null;
        }
        return JSON.writeValueAsString(enriquecida);
    }

    // Un valor ilegible no rompe el historial: el mensaje se sigue viendo, sin
    // sus botones.
    @Override
    public RespuestaEnriquecida convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, RespuestaEnriquecida.class);
        } catch (JacksonException ilegible) {
            log.warn("Respuesta enriquecida ilegible en la base; se muestra el mensaje sin ella");
            return null;
        }
    }
}
