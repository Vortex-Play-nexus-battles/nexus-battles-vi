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

class ClientesBotinHttpTest {

    private HttpServer servidor;
    private final AtomicReference<String> identidadPost = new AtomicReference<>();
    private final AtomicReference<String> cuerpoPost = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("productos entrega tipo, parte y tasa de caida del contrato PRD-001")
    void consultaLaTasaDelCatalogo() {
        ProductoBotin producto = clienteCatalogo().consultar("producto-armadura");

        assertEquals("producto-armadura", producto.id());
        assertEquals(TipoBotin.ARMADURA, producto.tipo());
        assertEquals(ParteArmaduraBotin.CASCO, producto.parteArmadura());
        assertEquals("12.5", producto.tasaDeCaida().toPlainString());
    }

    @Test
    @DisplayName("inventario distingue equipo y almacenamiento y descarta tipos no elegibles")
    void consultaObjetosEquipadosYAlmacenados() {
        List<ElementoCandidatoBotin> candidatos = clienteInventario().listarCandidatos(
                "jugador-enemigo",
                "heroe-enemigo");

        assertEquals(3, candidatos.size());
        assertEquals(OrigenBotin.EQUIPADO, candidatos.get(0).origen());
        assertEquals(OrigenBotin.EQUIPADO, candidatos.get(1).origen());
        assertEquals(ParteArmaduraBotin.CASCO, candidatos.get(1).parteArmadura());
        assertEquals(OrigenBotin.ALMACENADO, candidatos.get(2).origen());
    }

    @Test
    @DisplayName("inventario registra el objeto obtenido para el jugador ganador")
    void registraElBotinEnElInventarioGanador() {
        ElementoCandidatoBotin elemento = new ElementoCandidatoBotin(
                "elemento-item",
                "producto-item",
                TipoBotin.ITEM,
                "Pocion enemiga",
                null,
                OrigenBotin.ALMACENADO);

        clienteInventario().otorgar("jugador-ganador", elemento);

        assertEquals("jugador-ganador", identidadPost.get());
        assertEquals(
                "{\"productoId\":\"producto-item\",\"tipo\":\"ITEM\","
                        + "\"nombrePropio\":\"Pocion enemiga\",\"parteArmadura\":null}",
                cuerpoPost.get());
    }

    @Test
    @DisplayName("una respuesta inexistente del catalogo se informa como error de integracion")
    void informaProductoInexistente() {
        assertThrows(
                IntegracionBotinException.class,
                () -> clienteCatalogo().consultar("inexistente"));
    }

    private ClienteCatalogoBotinHttp clienteCatalogo() {
        return new ClienteCatalogoBotinHttp(baseUri(), httpClient());
    }

    private ClienteInventarioBotinHttp clienteInventario() {
        return new ClienteInventarioBotinHttp(baseUri(), httpClient());
    }

    private URI baseUri() {
        return URI.create("http://localhost:" + servidor.getAddress().getPort());
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private void responder(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        String metodo = intercambio.getRequestMethod();
        if (ruta.equals("/api/v1/productos/producto-armadura")) {
            enviar(intercambio, 200, """
                    {
                      "id": "producto-armadura",
                      "nombre": "Casco de hierro",
                      "tipo": "ARMADURA",
                      "parte": "CASCO",
                      "tasaDeCaida": 12.5,
                      "estado": "ACTIVO"
                    }
                    """);
            return;
        }
        if (ruta.equals("/api/v1/productos/inexistente")) {
            enviar(intercambio, 404, "{\"detail\":\"Producto no encontrado\"}");
            return;
        }
        if (ruta.equals("/api/v1/inventario/heroes/heroe-enemigo/equipamiento")) {
            assertEquals("jugador-enemigo", intercambio.getRequestHeaders().getFirst("X-User-Name"));
            enviar(intercambio, 200, """
                    {
                      "heroeId": "heroe-enemigo",
                      "armas": ["elemento-arma"],
                      "armaduras": {"CASCO": "elemento-armadura"},
                      "items": []
                    }
                    """);
            return;
        }
        if (ruta.equals("/api/v1/inventario/elementos") && metodo.equals("GET")) {
            assertEquals("pagina=0", intercambio.getRequestURI().getQuery());
            assertEquals("jugador-enemigo", intercambio.getRequestHeaders().getFirst("X-User-Name"));
            enviar(intercambio, 200, """
                    {
                      "elementos": [
                        {"id":"elemento-arma","productoId":"producto-arma","tipo":"ARMA","nombrePropio":"Espada"},
                        {"id":"elemento-armadura","productoId":"producto-armadura","tipo":"ARMADURA","nombrePropio":"Casco","parteArmadura":"CASCO"},
                        {"id":"elemento-item","productoId":"producto-item","tipo":"ITEM","nombrePropio":"Pocion"},
                        {"id":"heroe-enemigo","productoId":"producto-heroe","tipo":"HEROE","nombrePropio":"Guerrero"}
                      ],
                      "numero": 0,
                      "tamanio": 16,
                      "totalElementos": 4,
                      "totalPaginas": 1,
                      "ultima": true
                    }
                    """);
            return;
        }
        if (ruta.equals("/api/v1/inventario/elementos") && metodo.equals("POST")) {
            identidadPost.set(intercambio.getRequestHeaders().getFirst("X-User-Name"));
            cuerpoPost.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            enviar(intercambio, 201, "{\"id\":\"nuevo-elemento\"}");
            return;
        }
        enviar(intercambio, 404, "{}");
    }

    private void enviar(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
