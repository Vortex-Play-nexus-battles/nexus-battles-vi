package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que de verdad entra en el índice: se sirve un catálogo de dos páginas y las
 * fichas de héroes con un {@link HttpServer} del JDK, como en
 * {@link ResolutorDeProductoHttpTest}.
 */
class FuenteDelCatalogoHttpTest {

    private HttpServer servidor;
    private final List<String> rutas = new CopyOnWriteArrayList<>();
    private volatile boolean heroesCaido;
    private volatile boolean productosCaido;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", intercambio -> {
            String ruta = intercambio.getRequestURI().getPath();
            String consulta = intercambio.getRequestURI().getQuery();
            rutas.add(consulta == null ? ruta : ruta + "?" + consulta);
            int estado = 200;
            String cuerpo;
            if (ruta.equals("/api/v1/productos")) {
                estado = productosCaido ? 503 : 200;
                cuerpo = consulta != null && consulta.contains("page=1") ? PAGINA_1 : PAGINA_0;
            } else if (ruta.equals("/api/v1/heroes")) {
                estado = heroesCaido ? 503 : 200;
                cuerpo = "[{\"nombre\":\"Mago Fuego\",\"tipo\":\"Mago\",\"esSanador\":false}]";
            } else if (ruta.equals("/api/v1/heroes/Mago Fuego")) {
                cuerpo = """
                        {"nombre":"Mago Fuego","tipo":"Mago",
                         "descripcion":"Entrenado en la magia elemental.",
                         "acciones":[{"nombre":"Vulcano","efecto":"+3 al ataque, +(3d9) al daño"},
                                     {"nombre":"Misiles de magma","efecto":"+1 al ataque, +2 de daño"}]}
                        """;
            } else {
                estado = 404;
                cuerpo = "{}";
            }
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private FuenteDelCatalogoHttp fuente() {
        URI base = URI.create("http://localhost:" + servidor.getAddress().getPort());
        return new FuenteDelCatalogoHttp(base, base, HttpClient.newHttpClient());
    }

    @Test
    @DisplayName("recorre todas las páginas del catálogo y junta su información buscable")
    void juntaElCatalogo() {
        Map<String, String> textos = fuente().textosBuscables();

        assertEquals(List.of("p-heroe", "p-casco", "p-epica"), List.copyOf(textos.keySet()));
        assertTrue(rutas.contains("/api/v1/productos?page=0&size=50"));
        assertTrue(rutas.contains("/api/v1/productos?page=1&size=50"));
        String casco = textos.get("p-casco");
        assertTrue(casco.contains("Corona de hielo"));
        assertTrue(casco.contains("Armadura"));
        assertTrue(casco.contains("Casco"));
        assertTrue(casco.contains("+1 a la defensa"));
    }

    @Test
    @DisplayName("el héroe lleva su descripción y sus acciones; la épica, el nombre de su héroe")
    void heroesYEpicas() {
        Map<String, String> textos = fuente().textosBuscables();

        assertTrue(textos.get("p-heroe").contains("Vulcano"));
        assertTrue(textos.get("p-heroe").contains("magia elemental"));
        assertTrue(textos.get("p-epica").contains("Mago Fuego"));
        assertTrue(textos.get("p-epica").contains("+2 al daño"));
    }

    @Test
    @DisplayName("sin el servicio de héroes el índice se construye igual, sin sus acciones")
    void heroesCaido() {
        heroesCaido = true;

        Map<String, String> textos = fuente().textosBuscables();

        assertEquals(3, textos.size());
        assertFalse(textos.get("p-heroe").contains("Vulcano"));
    }

    @Test
    @DisplayName("sin el catálogo no hay índice: el llamador lo trata como no disponible")
    void productosCaido() {
        productosCaido = true;

        assertThrows(ResolutorDeProductoException.class, () -> fuente().textosBuscables());
    }

    private static final String PAGINA_0 = """
            {"content":[
              {"id":"p-heroe","nombre":"Mago Fuego","tipo":"HEROE","prototipo":"Mago Fuego",
               "descripcion":"Héroe de tipo Mago Fuego."},
              {"id":"p-casco","nombre":"Corona de hielo","tipo":"ARMADURA","parte":"CASCO",
               "descripcion":"Armadura de Mago Hielo. Efectos: +1 a la defensa, +1 de vida."}
            ],"page":0,"size":50,"totalElements":3,"totalPages":2}
            """;

    private static final String PAGINA_1 = """
            {"content":[
              {"id":"p-epica","nombre":"Luz cegadora","tipo":"EPICA","heroe":"p-heroe",
               "efectoGeneral":"+1 a la vida","efectoPotenciado":"+2 al daño +1 % de crítico"}
            ],"page":1,"size":50,"totalElements":3,"totalPages":2}
            """;
}
