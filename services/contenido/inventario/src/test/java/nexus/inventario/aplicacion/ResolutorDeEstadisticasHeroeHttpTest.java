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
import nexus.inventario.dominio.EstadisticasHeroe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HU-INV-006: valida el mapeo real del esquema Estadisticas completo de
 * heroes.yaml (poder, vida, defensa, ataqueDetalle, danoDetalle,
 * sanarDetalle), sin depender de Docker ni de un contenedor real de heroes
 * — a diferencia de ClienteHeroesHttpIntegracionTest en motor-combate, aqui
 * basta un servidor HTTP embebido del JDK (com.sun.net.httpserver) para
 * cubrir la logica real de parseo, construccion de URI y manejo de errores,
 * que es lo unico que puede romperse en este adaptador.
 */
class ResolutorDeEstadisticasHeroeHttpTest {

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

    private ResolutorDeEstadisticasHeroeHttp resolutor() {
        URI baseUri = URI.create("http://localhost:" + servidor.getAddress().getPort());
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new ResolutorDeEstadisticasHeroeHttp(baseUri, cliente);
    }

    @Test
    void mapeaLasTresFormulasCuandoLasTresEstanPresentes() {
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "nombre": "Guerrero Armas",
                  "estadisticasNivel1": {
                    "poder": 9, "vida": 40, "defensa": 8,
                    "ataqueDetalle": {"base": 12, "cantidadDados": 1, "caras": 6},
                    "danoDetalle": {"base": 2, "cantidadDados": 1, "caras": 4},
                    "sanarDetalle": {"base": 0, "cantidadDados": 1, "caras": 4}
                  }
                }
                """;

        EstadisticasHeroe resultado = resolutor().resolver("Guerrero Armas");

        assertThat(resultado.poder()).isEqualTo(9);
        assertThat(resultado.vida()).isEqualTo(40);
        assertThat(resultado.defensa()).isEqualTo(8);
        assertThat(resultado.ataqueDetalle().base()).isEqualTo(12);
        assertThat(resultado.danoDetalle().cantidadDados()).isEqualTo(1);
        assertThat(resultado.sanarDetalle().caras()).isEqualTo(4);
    }

    @Test
    void formulasAusentesEnElJsonSeMapeanANullSinFallar() {
        // Como un sanador real: heroes usa spring.jackson.default-property-inclusion=non-null,
        // asi que ataqueDetalle/danoDetalle vienen AUSENTES del JSON, no con valor null.
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "nombre": "Chaman",
                  "estadisticasNivel1": {
                    "poder": 7, "vida": 38, "defensa": 6,
                    "sanarDetalle": {"base": 0, "cantidadDados": 1, "caras": 4}
                  }
                }
                """;

        EstadisticasHeroe resultado = resolutor().resolver("Chaman");

        assertThat(resultado.ataqueDetalle()).isNull();
        assertThat(resultado.danoDetalle()).isNull();
        assertThat(resultado.sanarDetalle()).isNotNull();
    }

    @Test
    void construyeLaUriConElConstructorDeCincoArgumentos_nombreConEspacios() {
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {"nombre": "Guerrero Tanque", "estadisticasNivel1": {"poder": 10, "vida": 44, "defensa": 11}}
                """;

        resolutor().resolver("Guerrero Tanque");

        // Un espacio real en la ruta (%20), nunca "+" (lo que produciria URLEncoder).
        assertThat(ultimaRutaRecibida.get()).isEqualTo("/api/v1/heroes/Guerrero%20Tanque");
    }

    @Test
    void construyeLaUriConTildeReal_UTF8_sinUrlEncoder() {
        // heroes.yaml: la busqueda de GET /api/v1/heroes/{nombre} "tolera
        // tildes y mayusculas". La 'a' con tilde debe llegar percent-encoded
        // en UTF-8 (%C3%A1, 2 bytes), nunca como entidad ISO-8859-1 de un
        // solo byte (%E1) ni convertida a otra cosa por URLEncoder.
        estadoRespuesta = 200;
        cuerpoRespuesta = """
                {
                  "nombre": "Chamán",
                  "estadisticasNivel1": {
                    "poder": 7, "vida": 38, "defensa": 6,
                    "sanarDetalle": {"base": 0, "cantidadDados": 1, "caras": 4}
                  }
                }
                """;

        EstadisticasHeroe resultado = resolutor().resolver("Chamán");

        assertThat(ultimaRutaRecibida.get()).isEqualTo("/api/v1/heroes/Cham%C3%A1n");
        assertThat(resultado.poder()).isEqualTo(7);
        assertThat(resultado.sanarDetalle()).isNotNull();
    }

    @Test
    void heroeInexistenteLanzaExcepcionPropia() {
        estadoRespuesta = 404;
        cuerpoRespuesta = """
                {"detail": "No existe el prototipo solicitado"}
                """;

        assertThatThrownBy(() -> resolutor().resolver("Prototipo Fantasma"))
                .isInstanceOf(PrototipoDeHeroeNoEncontradoException.class)
                .hasMessageContaining("Prototipo Fantasma")
                .hasMessageContaining("No existe el prototipo solicitado");
    }

    @Test
    void respuestaInesperadaLanzaExcepcionPropiaConElCodigoDeEstado() {
        estadoRespuesta = 500;
        cuerpoRespuesta = "";

        assertThatThrownBy(() -> resolutor().resolver("Guerrero Tanque"))
                .isInstanceOf(ResolutorDeEstadisticasHeroeException.class)
                .hasMessageContaining("500");
    }
}
