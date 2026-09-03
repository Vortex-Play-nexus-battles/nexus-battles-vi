package nexus.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HU-INV-006 / HU-INV-007 (PR #203, ya no bloqueado): valida el mapeo real
 * de tipo, nombre y prototipo desde {@code GET /api/v1/productos/{id}},
 * sin depender de Docker ni de un contenedor real de productos — igual que
 * ResolutorDeEstadisticasHeroeHttpTest, basta un HttpServer embebido del
 * JDK para cubrir la logica real de parseo, construccion de URI y manejo
 * de errores.
 */
class ResolutorDeProductoHttpTest {

    private HttpServer servidor;
    private final AtomicReference<String> ultimaRutaRecibida = new AtomicReference<>();
    private volatile int estadoRespuesta = 200;
    private volatile String cuerpoRespuesta = "";

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", exchange -> {
            ultimaRutaRecibida.set(exchange.getRequestURI().toString());
            byte[] cuerpo = cuerpoRespuesta.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(estadoRespuesta, cuerpo.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private ResolutorDeProductoHttp resolutor() {
        URI baseUri = URI.create("http://localhost:" + servidor.getAddress().getPort());
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new ResolutorDeProductoHttp(baseUri, cliente);
    }

    @Test
    void mapeaTipoNombreYPrototipoCuandoElProductoEsUnHeroe() {
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440000",
                  "nombre": "Guerrero Tanque",
                  "tipo": "HEROE",
                  "prototipo": "Guerrero Tanque",
                  "imagen": "productos/guerrero-tanque.webp",
                  "descripcion": "Heroe de prueba",
                  "tiraje": -1,
                  "premium": false,
                  "estado": "ACTIVO",
                  "version": 1,
                  "creadoEn": "2026-08-01T00:00:00Z",
                  "modificadoEn": "2026-08-01T00:00:00Z"
                }
                """;

        ResolutorDeProducto.DetalleProducto resultado =
                resolutor().resolver("550e8400-e29b-41d4-a716-446655440000");

        assertThat(resultado.tipo()).isEqualTo("HEROE");
        assertThat(resultado.nombre()).isEqualTo("Guerrero Tanque");
        assertThat(resultado.prototipo()).isEqualTo("Guerrero Tanque");
    }

    @Test
    void prototipoAusenteEnElJsonSeMapeaANullCuandoElProductoNoEsUnHeroe() {
        // Como un arma o armadura real: el esquema ProductoCreado solo trae
        // "prototipo" cuando tipo=="HEROE"; para el resto viene ausente, no
        // presente con valor null.
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "id": "660e8400-e29b-41d4-a716-446655440001",
                  "nombre": "Espada de una mano",
                  "tipo": "ARMA",
                  "imagen": "productos/espada-una-mano.webp",
                  "descripcion": "Arma de prueba",
                  "tiraje": 100,
                  "premium": false,
                  "poderDeAtaque": 10,
                  "tasaDeCaida": 5.0,
                  "estado": "ACTIVO",
                  "version": 1,
                  "creadoEn": "2026-08-01T00:00:00Z",
                  "modificadoEn": "2026-08-01T00:00:00Z"
                }
                """;

        ResolutorDeProducto.DetalleProducto resultado =
                resolutor().resolver("660e8400-e29b-41d4-a716-446655440001");

        assertThat(resultado.tipo()).isEqualTo("ARMA");
        assertThat(resultado.nombre()).isEqualTo("Espada de una mano");
        assertThat(resultado.prototipo()).isNull();
    }

    @Test
    void mapeaUnaArmaduraSinPrototipo() {
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "id": "770e8400-e29b-41d4-a716-446655440002",
                  "nombre": "Magma Ardiente",
                  "tipo": "ARMADURA",
                  "imagen": "productos/magma-ardiente.webp",
                  "descripcion": "Armadura de prueba",
                  "tiraje": 50,
                  "premium": false,
                  "defensa": 8,
                  "parte": "CASCO",
                  "tasaDeCaida": 3.0,
                  "estado": "ACTIVO",
                  "version": 1,
                  "creadoEn": "2026-08-01T00:00:00Z",
                  "modificadoEn": "2026-08-01T00:00:00Z"
                }
                """;

        ResolutorDeProducto.DetalleProducto resultado =
                resolutor().resolver("770e8400-e29b-41d4-a716-446655440002");

        assertThat(resultado.tipo()).isEqualTo("ARMADURA");
        assertThat(resultado.nombre()).isEqualTo("Magma Ardiente");
        assertThat(resultado.prototipo()).isNull();
    }

    @Test
    void decodificaCorrectamenteUnNombreConTildeReal_UTF8() {
        // A diferencia de ResolutorDeEstadisticasHeroeHttpTest (donde la
        // tilde va en la URI de salida, porque el nombre del prototipo es
        // parte de la ruta), aqui el id siempre es un UUID sin tildes: lo
        // relevante es que el JSON de RESPUESTA se decodifique en UTF-8, no
        // la construccion de la URI.
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "id": "880e8400-e29b-41d4-a716-446655440003",
                  "nombre": "Poción de energía",
                  "tipo": "ITEM",
                  "imagen": "productos/pocion-energia.webp",
                  "descripcion": "Item de prueba",
                  "tiraje": 20,
                  "premium": false,
                  "efecto": "Recupera puntos de poder",
                  "tasaDeCaida": 2.0,
                  "estado": "ACTIVO",
                  "version": 1,
                  "creadoEn": "2026-08-01T00:00:00Z",
                  "modificadoEn": "2026-08-01T00:00:00Z"
                }
                """;

        ResolutorDeProducto.DetalleProducto resultado =
                resolutor().resolver("880e8400-e29b-41d4-a716-446655440003");

        assertThat(resultado.nombre()).isEqualTo("Poción de energía");
    }

    @Test
    void productoInexistenteLanzaExcepcionPropia() {
        estadoRespuesta = 404;
        cuerpoRespuesta = """
                {"detail": "No existe ningun producto con ese identificador"}
                """;

        String id = "990e8400-e29b-41d4-a716-446655440004";

        assertThatThrownBy(() -> resolutor().resolver(id))
                .isInstanceOf(ProductoNoEncontradoException.class)
                .hasMessageContaining(id)
                .hasMessageContaining("No existe ningun producto con ese identificador");
    }

    @Test
    void respuestaInesperadaLanzaExcepcionPropiaConElCodigoDeEstado() {
        estadoRespuesta = 500;
        cuerpoRespuesta = "";

        assertThatThrownBy(() -> resolutor().resolver("aa0e8400-e29b-41d4-a716-446655440005"))
                .isInstanceOf(ResolutorDeProductoException.class)
                .hasMessageContaining("500");
    }

    @Test
    void construyeLaUriConElConstructorDeCincoArgumentos() {
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {"nombre": "Espada de una mano", "tipo": "ARMA"}
                """;
        String id = "bb0e8400-e29b-41d4-a716-446655440006";

        resolutor().resolver(id);

        assertThat(ultimaRutaRecibida.get()).isEqualTo("/api/v1/productos/" + id);
    }
}
