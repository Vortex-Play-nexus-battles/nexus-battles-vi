package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * La sonda dice UP solo cuando recibe la salud de Actuator y esta dice UP.
 *
 * <p>Hasta RFINAL-08 bastaba con que un 200 CONTUVIERA el texto
 * {@code "status":"UP"} en cualquier parte. De ahi salian dos mentiras: una
 * pagina que llevara ese texto (la del borde, por ejemplo) contaba como
 * salud, igual que un estado raiz {@code UNKNOWN} con un componente
 * {@code UP}; y al reves, un JSON con espacios ({@code "status" : "UP"}) se
 * daba por caido.
 */
@DisplayName("Sonda de Actuator: UP solo con la salud de Actuator en JSON (RFINAL-08)")
class SondaDeActuatorTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");
    private static final String URL = "http://srv-torneos:8083/actuator/health";

    private final RestClient.Builder constructor = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final SondaDeActuator sonda =
            new SondaDeActuator(constructor.build(), Duration.ofMillis(1000), Duration.ofMillis(1500));

    private HttpServer servidorReal;

    @AfterEach
    void apagar() {
        if (servidorReal != null) {
            servidorReal.stop(0);
        }
    }

    @Test
    @DisplayName("200 con {status: UP} en JSON: disponible, y la pide como JSON")
    void upEnJson() {
        servidor.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        Comprobacion c = sonda.comprobar("torneos", URL, AHORA);

        assertThat(c.disponible()).isTrue();
        assertThat(c.esperaAgotada()).isFalse();
        servidor.verify();
    }

    @Test
    @DisplayName("el tipo propio de Actuator (application/vnd.spring-boot.actuator.v3+json) tambien es JSON")
    void tipoPropioDeActuator() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"UP\",\"groups\":[\"liveness\"]}",
                MediaType.parseMediaType("application/vnd.spring-boot.actuator.v3+json")));

        assertThat(sonda.comprobar("torneos", URL, AHORA).disponible()).isTrue();
    }

    @Test
    @DisplayName("un 200 con estado raiz UNKNOWN y un componente UP NO es UP: manda el estado de arriba")
    void unknownConUnComponenteUp() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"status\":\"UNKNOWN\",\"components\":{\"ping\":{\"status\":\"UP\"}}}", MediaType.APPLICATION_JSON));

        Comprobacion c = sonda.comprobar("torneos", URL, AHORA);

        assertThat(c.disponible()).isFalse();
        assertThat(c.detalle()).isEqualTo("el servicio reporta UNKNOWN (HTTP 200)");
    }

    @Test
    @DisplayName("JSON con espacios o saltos de linea sigue siendo UP: se lee el JSON, no se busca un texto")
    void jsonConEspacios() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\n  \"status\" : \"UP\"\n}", MediaType.APPLICATION_JSON));

        assertThat(sonda.comprobar("torneos", URL, AHORA).disponible()).isTrue();
    }

    @Test
    @DisplayName("DOWN con componentes sanos NO es UP, y el motivo dice DOWN y no el texto del 503")
    void downConComponentesSanos() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"DOWN\",\"components\":{\"db\":{\"status\":\"DOWN\"},"
                        + "\"ping\":{\"status\":\"UP\"}}}"));

        Comprobacion c = sonda.comprobar("torneos", URL, AHORA);

        assertThat(c.disponible()).isFalse();
        assertThat(c.esperaAgotada()).isFalse();
        assertThat(c.detalle()).isEqualTo("el servicio reporta DOWN (HTTP 503)");
    }

    @Test
    @DisplayName("una pagina HTML (la del borde, por ejemplo) no es salud aunque contenga el texto")
    void paginaHtmlNoEsSalud() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                "<html><body>{\"status\":\"UP\"}</body></html>", MediaType.TEXT_HTML));

        Comprobacion c = sonda.comprobar("torneos", URL, AHORA);

        assertThat(c.disponible()).isFalse();
        assertThat(c.detalle()).isEqualTo("no responde con la salud de Actuator (HTTP 200, text/html)");
    }

    @Test
    @DisplayName("un 404 en problem details tiene un status numerico: no es la salud de Actuator")
    void problemDetailNoEsSalud() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}"));

        Comprobacion c = sonda.comprobar("torneos", URL, AHORA);

        assertThat(c.disponible()).isFalse();
        assertThat(c.detalle()).isEqualTo("respuesta sin el estado de Actuator (HTTP 404)");
    }

    @Test
    @DisplayName("JSON ilegible: caido con motivo, sin reventar la ronda")
    void jsonIlegible() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":", MediaType.APPLICATION_JSON));

        Comprobacion c = sonda.comprobar("torneos", URL, AHORA);

        assertThat(c.disponible()).isFalse();
        assertThat(c.detalle()).isEqualTo("salud de Actuator ilegible (HTTP 200)");
    }

    @Test
    @DisplayName("cualquier estado distinto de UP se dice tal cual")
    void otroEstado() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"OUT_OF_SERVICE\"}",
                MediaType.APPLICATION_JSON));

        assertThat(sonda.comprobar("torneos", URL, AHORA).detalle())
                .isEqualTo("el servicio reporta OUT_OF_SERVICE (HTTP 200)");
    }

    /** Con red de verdad: un servicio que acepta la conexion y no contesta a tiempo. */
    @Test
    @DisplayName("conecta y no responde dentro del plazo: espera agotada (LENTO), no caida")
    void respuestaFueraDePlazo() throws IOException {
        servidorReal = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidorReal.setExecutor(Executors.newCachedThreadPool());
        servidorReal.createContext("/actuator/health", intercambio -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidorReal.start();
        String url = "http://127.0.0.1:" + servidorReal.getAddress().getPort() + "/actuator/health";
        SondaDeActuator conPlazosCortos = SondaDeActuator.conPlazos(Duration.ofMillis(500), Duration.ofMillis(200));

        long inicio = System.nanoTime();
        Comprobacion c = conPlazosCortos.comprobar("lento", url, AHORA);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(c.disponible()).isFalse();
        assertThat(c.esperaAgotada()).isTrue();
        assertThat(c.detalle()).isEqualTo("sin respuesta en 200 ms");
        assertThat(ms).isLessThan(1500);
    }

    @Test
    @DisplayName("nadie escucha en el puerto: conexion rechazada, caido y no lento")
    void conexionRechazada() throws IOException {
        int libre;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            libre = socket.getLocalPort();
        }
        SondaDeActuator conPlazosCortos = SondaDeActuator.conPlazos(Duration.ofMillis(500), Duration.ofMillis(500));

        Comprobacion c = conPlazosCortos.comprobar("apagado", "http://127.0.0.1:" + libre + "/actuator/health", AHORA);

        assertThat(c.disponible()).isFalse();
        assertThat(c.esperaAgotada()).isFalse();
        assertThat(c.detalle()).isEqualTo("conexión rechazada");
    }

    @Test
    @DisplayName("con red de verdad y salud UP: disponible")
    void upConRedDeVerdad() throws IOException {
        servidorReal = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidorReal.createContext("/actuator/health", intercambio -> {
            byte[] cuerpo = "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}"
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidorReal.start();
        String url = "http://127.0.0.1:" + servidorReal.getAddress().getPort() + "/actuator/health";

        Comprobacion c = SondaDeActuator.conPlazos(Duration.ofMillis(500), Duration.ofMillis(500))
                .comprobar("sano", url, AHORA);

        assertThat(c.disponible()).isTrue();
    }
}
