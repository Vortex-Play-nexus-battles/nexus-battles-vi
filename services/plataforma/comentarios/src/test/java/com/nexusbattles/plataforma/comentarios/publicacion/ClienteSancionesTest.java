package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contrato {@code moderacion-sanciones-consulta.yaml} (RF-USR-004) aplicado a
 * la publicacion de comentarios (HU-COM-001, CA-03): un autor con sancion
 * activa no publica. Y, sobre todo, que sin respuesta no se asume habilitado.
 */
@DisplayName("ClienteSanciones · estado disciplinario del autor (HU-COM-001)")
class ClienteSancionesTest {

    private static final String BASE = "http://localhost:8086/api/v1";
    private static final String AUTOR = "11111111-1111-1111-1111-111111111111";
    private static final String CONSULTA = BASE + "/sanciones/usuarios/" + AUTOR + "/activa";

    private MockRestServiceServer servidor;
    private ClienteSanciones cliente;

    @BeforeEach
    void montarServidorSimulado() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteSanciones(constructor.build(), BASE);
    }

    @Test
    @DisplayName("sin sancion activa el autor esta habilitado; es el GET del contrato, por uid")
    void sinSancionHabilitado() {
        servidor.expect(requestTo(CONSULTA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"sancionActiva\":false,\"motivo\":null,\"vigenteHasta\":null}",
                        MediaType.APPLICATION_JSON));

        assertEquals(EstadoDeAutor.HABILITADO, cliente.estadoDe(AUTOR));
        servidor.verify();
    }

    @Test
    @DisplayName("con sancion activa el autor esta silenciado")
    void conSancionSilenciado() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess(
                "{\"sancionActiva\":true,\"motivo\":\"Spam reiterado\",\"vigenteHasta\":\"2026-10-01T00:00:00Z\"}",
                MediaType.APPLICATION_JSON));

        assertEquals(EstadoDeAutor.SILENCIADO, cliente.estadoDe(AUTOR));
    }

    @Test
    @DisplayName("si sanciones no responde no se asume habilitado: 503 para que reintente")
    void sinRespuestaNoSeAsumeHabilitado() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withServerError());

        assertThrows(SancionesNoDisponibles.class, () -> cliente.estadoDe(AUTOR));
    }

    @Test
    @DisplayName("una respuesta vacia tampoco vale como habilitado")
    void respuestaVaciaTampoco() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThrows(SancionesNoDisponibles.class, () -> cliente.estadoDe(AUTOR));
    }

    @Test
    @DisplayName("la ruta es la del contrato publicado, no de memoria")
    void coincideConElContrato() throws Exception {
        String yaml = Files.readString(
                Path.of("..", "..", "..", "contracts", "openapi", "moderacion-sanciones-consulta.yaml"));

        assertTrue(yaml.contains("/sanciones/usuarios/{usuarioId}/activa"));
        assertEquals("/sanciones/usuarios/{uid}/activa", ClienteSanciones.RUTA);
    }
}
