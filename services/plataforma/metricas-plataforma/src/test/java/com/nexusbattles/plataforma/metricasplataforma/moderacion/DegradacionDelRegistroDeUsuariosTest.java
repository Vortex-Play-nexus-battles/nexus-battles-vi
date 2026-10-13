package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * HU-MET-001, CA-03, de punta a punta dentro del servicio: el controlador con el cliente HTTP REAL de ms-identidad
 * (solo la red es un doble). Identidad sana, sin contestar a tiempo, fuera de servicio o con una respuesta que no
 * son los indicadores: el tablero sale SIEMPRE con las sanciones y sus alertas, y las cuentas o estan completas o no
 * estan (con el motivo en «pendientes»). Nunca un 500 de toda la pagina ni un 0 que identidad no dio.
 */
@DisplayName("Degradacion del registro de usuarios con el cliente real (HU-MET-001, CA-03)")
class DegradacionDelRegistroDeUsuariosTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T10:00:00Z");
    private static final String INDICADORES = "http://identidad:8089/api/v1/admin/jugadores/indicadores";
    private static final String TOKEN = "Bearer token-del-admin";
    private static final String RESPUESTA = "{\"total\":12,"
            + "\"porEstado\":{\"ACTIVO\":8,\"PENDIENTE_VERIFICACION\":1,\"INACTIVO\":0,\"SUSPENDIDO\":2,\"BANEADO\":1},"
            + "\"registros\":{\"desde\":\"2026-09-01\",\"hasta\":\"2026-10-01\",\"total\":3,"
            + "\"porDia\":[{\"fecha\":\"2026-09-30\",\"cuentas\":1},{\"fecha\":\"2026-10-01\",\"cuentas\":2}]},"
            + "\"ocultarPruebas\":false,\"calculadoEn\":\"2026-10-01T10:00:00Z\"}";

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer identidad = MockRestServiceServer.bindTo(constructor).build();
    private final FuenteDeModeracion sanciones = mock(FuenteDeModeracion.class);
    /** Umbral D-25 = 3 «a partir de», como lo deja el despliegue. */
    private final ModeracionController controlador = new ModeracionController(sanciones,
            new ClienteDeUsuarios(constructor.build(), "http://identidad:8089"), 3, Clock.fixed(AHORA, ZoneOffset.UTC));

    @BeforeEach
    void sancionesArriba() {
        OffsetDateTime hasta = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
        // Dias con 2, 3 y 4 sanciones: alertan el de 3 y el de 4.
        when(sanciones.consultar(any(), any())).thenReturn(new FuenteDeModeracion.Agregados(hasta.minusDays(30),
                hasta, 9, Map.of("ADVERTENCIA", 9L),
                List.of(new FuenteDeModeracion.PorDia("2026-09-20", 2), new FuenteDeModeracion.PorDia("2026-09-21", 3),
                        new FuenteDeModeracion.PorDia("2026-09-22", 4)),
                Map.of(), 1, 0));
    }

    @Test
    @DisplayName("identidad 200: cuentas por estado y altas por dia, con el token del administrador reenviado")
    void identidadSana() {
        identidad.expect(requestTo(INDICADORES + "?desde=2026-09-01&hasta=2026-10-01"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        TableroDeModeracion tablero = controlador.tablero(null, null, TOKEN);

        assertThat(tablero.registroDeUsuarios()).isNotNull();
        assertThat(tablero.registroDeUsuarios().porEstado()).containsEntry("ACTIVO", 8L);
        assertThat(tablero.registroDeUsuarios().registros().porDia()).hasSize(2);
        assertThat(tablero.pendientes()).singleElement().asString().startsWith("frecuencia de reportes");
        assertThat(tablero.alertas()).hasSize(2);
        identidad.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("identidadQueNoDaLasCuentas")
    @DisplayName("identidad sin dar las cuentas: el tablero sale igual, sin numeros de cuentas y con el motivo")
    void sinCuentasElTableroSaleIgual(String caso, ResponseCreator respuesta, String motivo) {
        identidad.expect(requestTo(startsWith(INDICADORES))).andRespond(respuesta);

        TableroDeModeracion tablero = controlador.tablero(null, null, TOKEN);

        assertThat(tablero.sanciones().total()).isEqualTo(9);
        assertThat(tablero.alertasConfiguradas()).isTrue();
        assertThat(tablero.alertas()).hasSize(2);
        assertThat(tablero.registroDeUsuarios()).isNull();
        assertThat(tablero.pendientes()).hasSize(2);
        assertThat(tablero.pendientes().get(0))
                .startsWith("registro de nuevos usuarios: ms-identidad")
                .contains(motivo);
    }

    static Stream<Arguments> identidadQueNoDaLasCuentas() {
        return Stream.of(
                Arguments.of("plazo vencido", withException(new SocketTimeoutException("Read timed out")),
                        "no responde"),
                Arguments.of("503", withStatus(HttpStatus.SERVICE_UNAVAILABLE), "no responde"),
                Arguments.of("respuesta incompleta: porEstado sin BANEADO",
                        withSuccess(RESPUESTA.replace(",\"BANEADO\":1}", "}"), MediaType.APPLICATION_JSON),
                        "sin «porEstado con sus cinco estados»"),
                Arguments.of("respuesta invalida: JSON roto", withSuccess("{\"total\":", MediaType.APPLICATION_JSON),
                        "no son los indicadores"));
    }
}
