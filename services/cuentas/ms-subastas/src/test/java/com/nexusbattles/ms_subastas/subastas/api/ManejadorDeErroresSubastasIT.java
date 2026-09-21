package com.nexusbattles.ms_subastas.subastas.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-SUB-011. Prueba de extremo a extremo de ManejadorDeErroresSubastas,
 * contra el servidor real (puerto aleatorio) -- no @WebMvcTest/MockMvc, ya
 * que no esta confirmado que ese starter este en el classpath de este
 * modulo (mismo tipo de sorpresa que ya tuvimos con @DataJpaTest). Se usa
 * java.net.http.HttpClient (JDK, sin dependencia nueva) contra peticiones
 * HTTP reales, replicando de forma automatizada las mismas pruebas que ya
 * se hicieron a mano con curl.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.pujas.emision-automatica-intervalo-ms=3600000",
                "app.subastas.cierre-intervalo-ms=3600000",
                "app.notificaciones.drenaje-intervalo-ms=3600000"
        })
@Testcontainers(disabledWithoutDocker = true)
class ManejadorDeErroresSubastasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @LocalServerPort
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpResponse<String> get(String rutaConQuery) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + puerto + rutaConQuery))
            .GET()
            .build();
        return cliente.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void tipoProductoInvalidoListaLosValoresPermitidosEnElDetalle() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/subastas?tipoProducto=INVALIDO");

        assertEquals(400, respuesta.statusCode());
        JsonNode json = mapper.readTree(respuesta.body());
        assertEquals("Parámetro inválido", json.get("title").asText());

        String detalle = json.get("detail").asText();
        assertTrue(detalle.contains("tipoProducto"));
        assertTrue(detalle.contains("INVALIDO"));
        assertTrue(detalle.contains("ARMA"), "debe listar los valores permitidos del enum");
    }

    @Test
    void faltaQEnSugerenciasDaMensajeClaroDelParametro() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/subastas/sugerencias");

        assertEquals(400, respuesta.statusCode());
        JsonNode json = mapper.readTree(respuesta.body());
        assertEquals("Parámetro obligatorio faltante", json.get("title").asText());
        assertTrue(json.get("detail").asText().contains("'q'"));
    }

    @Test
    void unListadoValidoSigueRespondiendo200SinPasarPorElManejador() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/subastas");

        assertEquals(200, respuesta.statusCode());
    }
}
