package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Fuente de usuarios: indicadores de cuentas de ms-identidad (HU-MET-001)")
class ClienteDeUsuariosTest {

    private static final String RESPUESTA = "{\"total\":12,"
            + "\"porEstado\":{\"ACTIVO\":8,\"PENDIENTE_VERIFICACION\":1,\"INACTIVO\":0,\"SUSPENDIDO\":2,\"BANEADO\":1},"
            + "\"registros\":{\"desde\":\"2026-09-30\",\"hasta\":\"2026-10-01\",\"total\":3,"
            + "\"porDia\":[{\"fecha\":\"2026-09-30\",\"cuentas\":1},{\"fecha\":\"2026-10-01\",\"cuentas\":2}]},"
            + "\"ocultarPruebas\":false,\"calculadoEn\":\"2026-10-01T10:00:00Z\","
            + "\"campoQueIdentidadAnadaMas\":\"aditivo\"}";

    private MockRestServiceServer servidor;
    private ClienteDeUsuarios cliente;

    private void preparar() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteDeUsuarios(constructor.build(), "http://identidad:8089/");
    }

    @Test
    @DisplayName("lee GET /api/v1/admin/jugadores/indicadores con el rango y reenvia el token del administrador")
    void leeYReenviaElToken() {
        preparar();
        servidor.expect(requestTo("http://identidad:8089/api/v1/admin/jugadores/indicadores?desde=2026-09-30&hasta=2026-10-01"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-del-admin"))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        FuenteDeUsuarios.Indicadores i = cliente.consultar("2026-09-30", "2026-10-01", "Bearer token-del-admin");

        assertThat(i.total()).isEqualTo(12);
        assertThat(i.porEstado()).containsEntry("ACTIVO", 8L).containsEntry("SUSPENDIDO", 2L).containsEntry("BANEADO", 1L);
        assertThat(i.registros().total()).isEqualTo(3);
        assertThat(i.registros().porDia()).hasSize(2);
        assertThat(i.registros().porDia().get(1).fecha()).isEqualTo("2026-10-01");
        assertThat(i.registros().porDia().get(1).cuentas()).isEqualTo(2);
        servidor.verify();
    }

    @Test
    @DisplayName("sin token en la peticion no inventa credenciales: no manda Authorization")
    void sinTokenNoMandaAuthorization() {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://identidad:8089/api/v1/admin/jugadores/indicadores")))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.consultar("2026-09-30", "2026-10-01", null))
                .isInstanceOf(FuenteDeUsuarios.NoDisponible.class)
                .hasMessageContaining("permiso");
        servidor.verify();
    }

    @Test
    @DisplayName("403: identidad niega el permiso (p. ej. un token de servicio); se dice por su nombre")
    void sinPermiso() {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://identidad:8089/api/v1/admin/jugadores/indicadores")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> cliente.consultar("2026-09-30", "2026-10-01", "Bearer de-servicio"))
                .isInstanceOf(FuenteDeUsuarios.NoDisponible.class)
                .hasMessageContaining("permiso")
                .hasMessageContaining("GESTIONAR_CUENTAS");
    }

    @Test
    @DisplayName("400: un rango que identidad no acepta (tope tecnico) no se disimula")
    void rangoRechazado() {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://identidad:8089/api/v1/admin/jugadores/indicadores")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> cliente.consultar("2024-01-01", "2026-10-01", "Bearer t"))
                .isInstanceOf(FuenteDeUsuarios.NoDisponible.class)
                .hasMessageContaining("rango");
    }

    @Test
    @DisplayName("5xx o caida: ms-identidad no responde")
    void identidadCaida() {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://identidad:8089/api/v1/admin/jugadores/indicadores")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> cliente.consultar("2026-09-30", "2026-10-01", "Bearer t"))
                .isInstanceOf(FuenteDeUsuarios.NoDisponible.class)
                .hasMessageContaining("no responde");
    }

    @Test
    @DisplayName("una respuesta vacia tampoco se da por buena")
    void respuestaVacia() {
        preparar();
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith("http://identidad:8089/api/v1/admin/jugadores/indicadores")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.consultar("2026-09-30", "2026-10-01", "Bearer t"))
                .isInstanceOf(FuenteDeUsuarios.NoDisponible.class);
    }
}
