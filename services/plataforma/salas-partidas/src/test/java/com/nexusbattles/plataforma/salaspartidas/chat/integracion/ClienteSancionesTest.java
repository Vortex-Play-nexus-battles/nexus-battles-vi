package com.nexusbattles.plataforma.salaspartidas.chat.integracion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexusbattles.plataforma.salaspartidas.chat.SancionesNoDisponibles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Contrato {@code moderacion-sanciones-consulta.yaml} (RF-USR-004) aplicado al
 * chat de HU-JUE-015: quien tiene una sancion activa no escribe. Y, sobre
 * todo, que sin respuesta no se asume «sin sancion».
 */
@DisplayName("ClienteSanciones · silencio por sancion activa (HU-JUE-015, CA-03)")
class ClienteSancionesTest {

    private static final String BASE = "http://localhost:8086/api/v1";
    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CONSULTA = BASE + "/sanciones/usuarios/" + JUGADOR + "/activa";

    private MockRestServiceServer servidor;
    private ClienteSanciones cliente;

    @BeforeEach
    void montarServidorSimulado() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteSanciones(constructor.build(), BASE);
    }

    @Test
    @DisplayName("sin sancion activa no esta silenciado; la consulta es el GET del contrato, por uid")
    void sinSancionNoEstaSilenciado() {
        servidor.expect(requestTo(CONSULTA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"sancionActiva\":false,\"motivo\":null,\"vigenteHasta\":null}",
                        MediaType.APPLICATION_JSON));

        assertFalse(cliente.estaSilenciado(JUGADOR));
        servidor.verify();
    }

    @Test
    @DisplayName("con sancion activa esta silenciado, y se recuerda el motivo para decirselo")
    void conSancionEstaSilenciado() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess(
                "{\"sancionActiva\":true,\"motivo\":\"Lenguaje ofensivo reiterado\","
                        + "\"vigenteHasta\":\"2026-09-30T00:00:00Z\"}",
                MediaType.APPLICATION_JSON));

        assertTrue(cliente.estaSilenciado(JUGADOR));
    }

    @Test
    @DisplayName("si sanciones no responde, no se asume «sin sancion»: el mensaje se bloquea con 503")
    void sinRespuestaNoSeAsumeLibre() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withServerError());

        SancionesNoDisponibles error = assertThrows(SancionesNoDisponibles.class,
                () -> cliente.estaSilenciado(JUGADOR));
        assertEquals(503, error.estado());
    }

    @Test
    @DisplayName("una respuesta vacia tampoco vale como «sin sancion»")
    void respuestaVaciaTampoco() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThrows(SancionesNoDisponibles.class, () -> cliente.estaSilenciado(JUGADOR));
    }

    @Test
    @DisplayName("la ruta y el campo son los del contrato publicado, no de memoria")
    void coincideConElContrato() throws Exception {
        Path contrato = Path.of("..", "..", "..", "contracts", "openapi", "moderacion-sanciones-consulta.yaml");
        String yaml = Files.readString(contrato);

        assertAll(
                () -> assertTrue(yaml.contains("/sanciones/usuarios/{usuarioId}/activa"), "ruta"),
                () -> assertTrue(yaml.contains("sancionActiva:"), "campo"),
                () -> assertEquals("/sanciones/usuarios/{uid}/activa", ClienteSanciones.RUTA));
    }
}
