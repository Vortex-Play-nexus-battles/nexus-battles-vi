package com.nexusbattles.ms_identidad.admin.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SA-21 — la fecha fin de una suspension que manda la consola llega como
 * instante con zona y se guarda en la hora del servidor; una sin zona se sigue
 * leyendo como antes.
 */
@DisplayName("La fecha fin de la suspension, con o sin zona (SA-21)")
class HoraDelServidorTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private LocalDateTime leida(String valor) {
        return json.readValue("{\"suspendidoHasta\":\"" + valor + "\",\"motivo\":\"Prueba\"}",
                SuspenderCuentaRequest.class).getSuspendidoHasta();
    }

    @Test
    @DisplayName("con zona (lo que manda la consola): el mismo instante, en la hora del servidor")
    void conZona() {
        LocalDateTime esperada = OffsetDateTime.parse("2099-10-15T15:00:00Z")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        assertThat(leida("2099-10-15T15:00:00.000Z")).isEqualTo(esperada);
        // Las 10:00 en Colombia son el mismo instante que las 15:00 UTC.
        assertThat(leida("2099-10-15T10:00:00-05:00")).isEqualTo(esperada);
    }

    @Test
    @DisplayName("con la zona de Colombia y el servidor en UTC: termina a la hora que se eligio, no cinco horas antes")
    void desdeColombiaConElServidorEnUtc() {
        HoraDelServidor enUtc = new HoraDelServidor(ZoneId.of("UTC"));

        assertThat(enUtc.leer("2099-10-15T10:00:00-05:00", null)).isEqualTo(LocalDateTime.of(2099, 10, 15, 15, 0));
    }

    @Test
    @DisplayName("sin zona (las pruebas y los clientes anteriores): la hora del servidor, como antes")
    void sinZona() {
        assertThat(leida("2099-10-15T10:00")).isEqualTo(LocalDateTime.of(2099, 10, 15, 10, 0));
        assertThat(leida("2099-10-15T10:00:30")).isEqualTo(LocalDateTime.of(2099, 10, 15, 10, 0, 30));
    }

    @Test
    @DisplayName("lo que no es una fecha es un error de datos, no una fecha inventada")
    void noEsUnaFecha() {
        assertThatThrownBy(() -> leida("mañana")).isInstanceOf(DatabindException.class)
                .hasMessageContaining("no es una fecha y hora ISO-8601");
    }
}
