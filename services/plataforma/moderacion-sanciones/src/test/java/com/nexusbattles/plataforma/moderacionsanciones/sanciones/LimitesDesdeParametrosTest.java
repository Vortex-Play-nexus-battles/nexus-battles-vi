package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** HU-ADM-001 CA-04: los limites se leen de admin-parametros con cache y respaldo. */
@DisplayName("Limites de sancion desde admin-parametros")
class LimitesDesdeParametrosTest {

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-01T10:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };
    private final LimitesDesdeParametros limites = new LimitesDesdeParametros(constructor.build(),
            "http://admin/api/v1/", LimitesDeSancion.Fijos.de(1, 30, 30), reloj, Duration.ofSeconds(30));

    private static String valor(String clave, String valor) {
        return "{\"clave\":\"" + clave + "\",\"valor\":" + (valor == null ? "null" : "\"" + valor + "\"")
                + ",\"tipo\":\"ENTERO\",\"version\":2}";
    }

    @Test
    @DisplayName("lee el valor vigente y lo cachea 30 s; sin valor (PO sin decidir) o caido usa el respaldo")
    void lecturaCacheYRespaldo() {
        servidor.expect(requestTo("http://admin/api/v1/parametros/sanciones.suspension.maxima-dias/valor"))
                .andRespond(withSuccess(valor("sanciones.suspension.maxima-dias", "15"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://admin/api/v1/parametros/sanciones.suspension.minima-horas/valor"))
                .andRespond(withSuccess(valor("sanciones.suspension.minima-horas", null), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://admin/api/v1/parametros/sanciones.apelacion.plazo-dias/valor"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo("http://admin/api/v1/parametros/sanciones.suspension.maxima-dias/valor"))
                .andRespond(withSuccess(valor("sanciones.suspension.maxima-dias", "7"), MediaType.APPLICATION_JSON));

        assertThat(limites.suspensionMaxima()).isEqualTo(Duration.ofDays(15));
        assertThat(limites.suspensionMaxima()).as("cacheado, sin segunda peticion").isEqualTo(Duration.ofDays(15));
        assertThat(limites.suspensionMinima()).as("sin valor: respaldo").isEqualTo(Duration.ofHours(1));
        assertThat(limites.plazoDeApelacion()).as("caido: respaldo").isEqualTo(Duration.ofDays(30));
        ahora.set(ahora.get().plusSeconds(31));
        assertThat(limites.suspensionMaxima()).as("cache vencida: vuelve a leer").isEqualTo(Duration.ofDays(7));
        servidor.verify();
    }
}
