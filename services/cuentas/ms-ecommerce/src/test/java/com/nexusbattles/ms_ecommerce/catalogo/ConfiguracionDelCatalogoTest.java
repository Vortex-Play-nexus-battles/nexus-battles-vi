package com.nexusbattles.ms_ecommerce.catalogo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESPADA;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.json;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.pagina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuracion del cliente contra un servidor HTTP de verdad (el del JDK):
 * que la URL base y los tiempos de espera configurados son los que se aplican.
 * Un servidor simulado no puede probar esto, porque sustituye justo la fabrica
 * de peticiones que lleva los tiempos.
 */
@DisplayName("Configuracion del catalogo: URL y tiempos de espera de verdad")
class ConfiguracionDelCatalogoTest {

    private HttpServer servidor;

    @AfterEach
    void apagar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private String arrancar(com.sun.net.httpserver.HttpHandler manejador) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", manejador);
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static CatalogoMaestro catalogoEn(String url, Duration conexion, Duration lectura) {
        return new CatalogoMaestro(ConfiguracionDelCatalogo.constructorDelCliente(
                new PropiedadesDelCatalogo(url, conexion, lectura)).build());
    }

    @Test
    @DisplayName("pide el listado por omision a la URL configurada, sin token")
    void usaLaUrlConfigurada() throws IOException {
        AtomicReference<String> pedido = new AtomicReference<>();
        AtomicReference<String> autorizacion = new AtomicReference<>();
        String url = arrancar(intercambio -> {
            pedido.set(intercambio.getRequestURI().toString());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, 200, pagina(0, 1, json(ESPADA, "Espada")));
        });

        // Con barra final: la ruta no sale con "//".
        CatalogoMaestro catalogo = catalogoEn(url + "/", Duration.ofSeconds(2), Duration.ofSeconds(3));

        assertThat(catalogo.productosEnVenta()).extracting(ProductoDelCatalogo::id).containsExactly(ESPADA);
        assertThat(pedido.get()).isEqualTo("/api/v1/productos?page=0&size=50");
        assertThat(autorizacion.get()).isNull();
    }

    @Test
    @DisplayName("un catalogo que no contesta a tiempo se abandona en el tiempo de lectura")
    void aplicaElTiempoDeLectura() throws IOException {
        String url = arrancar(intercambio -> {
            try {
                Thread.sleep(1_500);
                responder(intercambio, 200, pagina(0, 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException clienteSeFue) {
                // El cliente ya se rindio: es lo que se prueba.
            }
        });
        CatalogoMaestro catalogo = catalogoEn(url, Duration.ofSeconds(2), Duration.ofMillis(200));

        long inicio = System.nanoTime();
        assertThatThrownBy(catalogo::productosEnVenta).isInstanceOf(CatalogoNoDisponibleException.class);
        long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(milisegundos).isLessThan(1_400);
    }

    @Test
    @DisplayName("una conexion rechazada es catalogo no disponible")
    void conexionRechazada() throws IOException {
        int puertoLibre;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoLibre = socket.getLocalPort();
        }
        CatalogoMaestro catalogo = catalogoEn("http://127.0.0.1:" + puertoLibre,
                Duration.ofMillis(500), Duration.ofMillis(500));

        assertThatThrownBy(catalogo::productosEnVenta).isInstanceOf(CatalogoNoDisponibleException.class);
        assertThatThrownBy(() -> catalogo.producto(ESPADA)).isInstanceOf(CatalogoNoDisponibleException.class);
    }

    @Test
    @DisplayName("un 404 real del catalogo es 'no existe'")
    void noExisteDeVerdad() throws IOException {
        String url = arrancar(intercambio -> responder(intercambio, 404,
                "{\"type\":\"urn:nexus:problema:producto-no-encontrado\",\"status\":404}"));

        assertThat(catalogoEn(url, Duration.ofSeconds(2), Duration.ofSeconds(3)).producto(ESPADA)).isEmpty();
    }

    @Test
    @DisplayName("el contexto crea las propiedades con sus valores por omision, el cliente y el reloj")
    void valoresPorOmision() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracionDelCatalogo.class)
                .run(contexto -> {
                    PropiedadesDelCatalogo propiedades = contexto.getBean(PropiedadesDelCatalogo.class);
                    assertThat(propiedades.url()).isEqualTo("http://localhost:8103");
                    assertThat(propiedades.timeoutConexion()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(propiedades.timeoutLectura()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(contexto.getBean(ConfiguracionDelCatalogo.CLIENTE, RestClient.class)).isNotNull();
                    assertThat(contexto.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
                });
    }

    @Test
    @DisplayName("PRODUCTOS_URL y los tiempos se pueden cambiar por configuracion")
    void valoresConfigurados() throws IOException {
        String url = arrancar(intercambio -> responder(intercambio, 200, pagina(0, 1, json(ESPADA, "Espada"))));

        new ApplicationContextRunner()
                .withUserConfiguration(ConfiguracionDelCatalogo.class)
                .withPropertyValues("catalogo.productos.url=" + url,
                        "catalogo.productos.timeout-conexion=750ms",
                        "catalogo.productos.timeout-lectura=1s")
                .run(contexto -> {
                    PropiedadesDelCatalogo propiedades = contexto.getBean(PropiedadesDelCatalogo.class);
                    assertThat(propiedades.url()).isEqualTo(url);
                    assertThat(propiedades.timeoutConexion()).isEqualTo(Duration.ofMillis(750));
                    assertThat(propiedades.timeoutLectura()).isEqualTo(Duration.ofSeconds(1));

                    RestClient cliente = contexto.getBean(ConfiguracionDelCatalogo.CLIENTE, RestClient.class);
                    assertThat(new CatalogoMaestro(cliente).productosEnVenta()).hasSize(1);
                });
    }
}
