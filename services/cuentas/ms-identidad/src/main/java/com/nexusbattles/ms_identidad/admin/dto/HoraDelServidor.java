package com.nexusbattles.ms_identidad.admin.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Una fecha y hora que llega de la consola, como hora de pared del servidor —
 * SA-21.
 *
 * <p>Las columnas de cuentas guardan la hora del servidor sin zona. La consola
 * enviaba la hora de un campo {@code datetime-local}, que es la de quien la
 * escribe: con el servidor en UTC y la consola en Colombia, «suspendida hasta
 * mañana a las 10:00» terminaba a las 5:00. Ahora la consola manda el
 * instante con su zona («2026-10-15T15:00:00.000Z», lo que pide el
 * {@code format: date-time} del contrato) y aqui se pasa a la hora del
 * servidor. Una hora sin zona («2026-10-15T10:00») se sigue leyendo como antes,
 * hora del servidor: asi la escriben las pruebas y cualquier cliente anterior.
 */
public class HoraDelServidor extends StdScalarDeserializer<LocalDateTime> {

    private final ZoneId zonaDelServidor;

    public HoraDelServidor() {
        this(ZoneId.systemDefault());
    }

    HoraDelServidor(ZoneId zonaDelServidor) {
        super(LocalDateTime.class);
        this.zonaDelServidor = zonaDelServidor;
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext contexto) {
        String texto = p.getValueAsString();
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return leer(texto.trim(), contexto);
    }

    LocalDateTime leer(String texto, DeserializationContext contexto) {
        try {
            return OffsetDateTime.parse(texto).atZoneSameInstant(zonaDelServidor).toLocalDateTime();
        } catch (DateTimeParseException sinZona) {
            try {
                return LocalDateTime.parse(texto);
            } catch (DateTimeParseException ninguna) {
                return (LocalDateTime) contexto.handleWeirdStringValue(LocalDateTime.class, texto,
                        "no es una fecha y hora ISO-8601 (por ejemplo, 2026-10-15T15:00:00Z)");
            }
        }
    }
}
