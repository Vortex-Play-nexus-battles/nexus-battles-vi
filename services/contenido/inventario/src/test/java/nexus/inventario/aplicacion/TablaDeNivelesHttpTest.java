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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import nexus.inventario.dominio.TablaDeNiveles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La tabla de niveles leida de heroes por HTTP de verdad (heroes.yaml, GET /api/v1/progresion/niveles). */
class TablaDeNivelesHttpTest {

    private static final String NIVELES = """
            [{"nivel":1,"experienciaParaSubir":100},{"nivel":2,"experienciaParaSubir":120.0},
             {"nivel":3,"experienciaParaSubir":144.0},{"nivel":4,"experienciaParaSubir":172.8},
             {"nivel":5,"experienciaParaSubir":207.36},{"nivel":6,"experienciaParaSubir":248.832},
             {"nivel":7,"experienciaParaSubir":298.5984},{"nivel":8}]""";

    private HttpServer servidor;
    private final AtomicInteger llamadas = new AtomicInteger();
    private final AtomicReference<String> cuerpo = new AtomicReference<>(NIVELES);
    private volatile int estado = 200;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/progresion/niveles", intercambio -> {
            llamadas.incrementAndGet();
            byte[] bytes = cuerpo.get().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private TablaDeNivelesHttp cliente() {
        return new TablaDeNivelesHttp(URI.create("http://127.0.0.1:" + servidor.getAddress().getPort()),
                HttpClient.newHttpClient());
    }

    @Test
    @DisplayName("lee los ocho niveles, ordena por nivel y guarda la tabla tras la primera lectura")
    void leeYGuarda() {
        TablaDeNivelesHttp cliente = cliente();

        TablaDeNiveles tabla = cliente.tabla();
        cliente.tabla();

        assertThat(tabla.nivelMaximo()).isEqualTo(8);
        assertThat(tabla.paraSubir().getFirst()).isEqualTo(100.0);
        assertThat(llamadas).hasValue(1);
    }

    @Test
    @DisplayName("un error o una respuesta que no se entiende es progresion no disponible, y no se guarda")
    void fallos() {
        estado = 503;
        TablaDeNivelesHttp cliente = cliente();
        assertThatThrownBy(cliente::tabla).isInstanceOf(ProgresionNoDisponibleException.class);

        estado = 200;
        cuerpo.set("no es json");
        assertThatThrownBy(cliente::tabla).isInstanceOf(ProgresionNoDisponibleException.class);

        cuerpo.set("[]");
        assertThatThrownBy(cliente::tabla).isInstanceOf(ProgresionNoDisponibleException.class);

        cuerpo.set(NIVELES);
        assertThat(cliente.tabla().nivelMaximo()).isEqualTo(8);
    }

    @Test
    @DisplayName("si heroes no contesta, progresion no disponible")
    void sinConexion() {
        int puerto = servidor.getAddress().getPort();
        servidor.stop(0);
        TablaDeNivelesHttp cliente = new TablaDeNivelesHttp(URI.create("http://127.0.0.1:" + puerto),
                HttpClient.newHttpClient());

        assertThatThrownBy(cliente::tabla).isInstanceOf(ProgresionNoDisponibleException.class);
    }
}
