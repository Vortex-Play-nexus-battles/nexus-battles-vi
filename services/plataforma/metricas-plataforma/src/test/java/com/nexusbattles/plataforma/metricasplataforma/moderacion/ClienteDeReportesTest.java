package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Fuente de reportes: frecuencia de reportes de comentarios (§7.3.8)")
class ClienteDeReportesTest {

    private static final OffsetDateTime HASTA = OffsetDateTime.of(2026, 10, 14, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DESDE = HASTA.minusDays(30);

    private static final String RESPUESTA = "{\"desde\":\"2026-09-14T15:00:00Z\",\"hasta\":\"2026-10-14T15:00:00Z\","
            + "\"total\":5,\"porDia\":[{\"fecha\":\"2026-10-10\",\"reportes\":4},{\"fecha\":\"2026-10-12\",\"reportes\":1}],"
            + "\"porCategoria\":{\"SPAM\":3,\"CONTENIDO_OFENSIVO\":1,\"ACOSO\":1,\"INFORMACION_FALSA\":0,"
            + "\"CONTENIDO_INAPROPIADO\":0,\"VIOLACION_DE_DERECHOS\":0},"
            + "\"comentariosReportados\":3,\"reportantes\":4,\"campoNuevoDeComentarios\":\"aditivo\"}";

    private MockRestServiceServer servidor;
    private ClienteDeReportes cliente;

    private void preparar() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteDeReportes(constructor.build(), "http://comentarios:8081/");
    }

    @Test
    @DisplayName("lee la frecuencia del periodo en instantes UTC y reenvia el token de quien mira el tablero")
    void leeYReenviaElToken() {
        preparar();
        servidor.expect(requestTo("http://comentarios:8081/api/v1/comentarios/moderacion/reportes/frecuencia"
                        + "?desde=2026-09-14T15%3A00%3A00Z&hasta=2026-10-14T15%3A00%3A00Z"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-del-admin"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        FuenteDeReportes.Frecuencia f = cliente.consultar(DESDE, HASTA, "Bearer token-del-admin");

        assertThat(f.total()).isEqualTo(5);
        assertThat(f.porDia()).hasSize(2);
        assertThat(f.porDia().get(0).fecha()).isEqualTo("2026-10-10");
        assertThat(f.porDia().get(0).reportes()).isEqualTo(4);
        assertThat(f.porCategoria()).containsEntry("SPAM", 3L).containsEntry("VIOLACION_DE_DERECHOS", 0L);
        assertThat(f.comentariosReportados()).isEqualTo(3);
        assertThat(f.reportantes()).isEqualTo(4);
        servidor.verify();
    }

    @Test
    @DisplayName("un periodo con otra zona horaria se manda en UTC")
    void periodoEnUtc() {
        preparar();
        OffsetDateTime enBogota = OffsetDateTime.of(2026, 10, 14, 10, 0, 0, 0, ZoneOffset.ofHours(-5));
        servidor.expect(requestTo("http://comentarios:8081/api/v1/comentarios/moderacion/reportes/frecuencia"
                        + "?desde=2026-10-13T15%3A00%3A00Z&hasta=2026-10-14T15%3A00%3A00Z"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        cliente.consultar(enBogota.minusDays(1), enBogota, "Bearer x");
        servidor.verify();
    }

    @Test
    @DisplayName("sin token no inventa credenciales; el 401 es SIN_PERMISO")
    void sinToken() {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://comentarios:8081/api/v1/comentarios")))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.consultar(DESDE, HASTA, null))
                .isInstanceOfSatisfying(FuenteDeReportes.NoDisponible.class,
                        e -> assertThat(e.causa()).isEqualTo(CausaDeIndisponibilidad.SIN_PERMISO))
                .hasMessageContaining("permiso");
        servidor.verify();
    }

    static Stream<Arguments> fallos() {
        return Stream.of(
                Arguments.of("403", withStatus(HttpStatus.FORBIDDEN), CausaDeIndisponibilidad.SIN_PERMISO, "permiso"),
                Arguments.of("400", withStatus(HttpStatus.BAD_REQUEST), CausaDeIndisponibilidad.RANGO_NO_ADMITIDO,
                        "periodo"),
                Arguments.of("503", withStatus(HttpStatus.SERVICE_UNAVAILABLE), CausaDeIndisponibilidad.NO_RESPONDE,
                        "no responde"),
                Arguments.of("plazo vencido", withException(new SocketTimeoutException("Read timed out")),
                        CausaDeIndisponibilidad.NO_RESPONDE, "Read timed out"),
                Arguments.of("HTML", withSuccess("<html>no</html>", MediaType.TEXT_HTML),
                        CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA, "no es la frecuencia"),
                Arguments.of("sin una categoria", withSuccess(RESPUESTA.replace("\"ACOSO\":1,", ""),
                        MediaType.APPLICATION_JSON), CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA, "porCategoria"),
                Arguments.of("sin total", withSuccess(RESPUESTA.replace("\"total\":5,", ""),
                        MediaType.APPLICATION_JSON), CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA, "total"),
                Arguments.of("sin reportantes", withSuccess(RESPUESTA.replace(",\"reportantes\":4", ""),
                        MediaType.APPLICATION_JSON), CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA, "reportantes"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fallos")
    @DisplayName("cada fallo dice su causa y su motivo, y nunca se publica un 0 que comentarios no dio")
    void fallo(String caso, org.springframework.test.web.client.ResponseCreator respuesta,
               CausaDeIndisponibilidad causa, String motivo) {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://comentarios:8081/api/v1/comentarios")))
                .andRespond(respuesta);

        assertThatThrownBy(() -> cliente.consultar(DESDE, HASTA, "Bearer x"))
                .isInstanceOfSatisfying(FuenteDeReportes.NoDisponible.class,
                        e -> assertThat(e.causa()).isEqualTo(causa))
                .hasMessageContaining(motivo);
    }
}
