package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClienteTransferidorEquipoHttpTest {

    private HttpServer servidor;
    private final AtomicReference<String> clave = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private int estado = 200;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/v1/inventario/transferencias-combate", this::responder);
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("envia todas las asignaciones en una sola solicitud idempotente")
    void enviaLoteAlInventario() {
        cliente().transferir("partida-42", List.of(
                transferencia("objeto-1", "perdedor-1", "heroe-1", "ganador-1"),
                transferencia("objeto-2", "perdedor-2", "heroe-2", "ganador-2")));

        assertEquals("partida-42", clave.get());
        assertEquals("Bearer token-servicio", autorizacion.get());
        assertEquals(
                "{\"transferencias\":["
                        + "{\"elementoId\":\"objeto-1\",\"propietarioOrigenId\":\"perdedor-1\","
                        + "\"heroeOrigenId\":\"heroe-1\",\"propietarioDestinoId\":\"ganador-1\"},"
                        + "{\"elementoId\":\"objeto-2\",\"propietarioOrigenId\":\"perdedor-2\","
                        + "\"heroeOrigenId\":\"heroe-2\",\"propietarioDestinoId\":\"ganador-2\"}]}",
                cuerpo.get());
    }

    @Test
    @DisplayName("informa como error de integracion un rechazo del inventario")
    void informaRechazo() {
        estado = 409;

        assertThrows(IntegracionBotinException.class, () -> cliente().transferir(
                "partida-43",
                List.of(transferencia("objeto-1", "perdedor", "heroe-1", "ganador"))));
    }

    private ClienteTransferidorEquipoHttp cliente() {
        URI base = URI.create("http://localhost:" + servidor.getAddress().getPort());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new ClienteTransferidorEquipoHttp(base, http, () -> "token-servicio");
    }

    private static TransferenciaEquipo transferencia(
            String elementoId,
            String origen,
            String heroeId,
            String destino) {
        return new TransferenciaEquipo(elementoId, origen, heroeId, destino);
    }

    private void responder(HttpExchange intercambio) throws IOException {
        clave.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
        autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
        cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] respuesta = "{}".getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, respuesta.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(respuesta);
        }
    }
}
