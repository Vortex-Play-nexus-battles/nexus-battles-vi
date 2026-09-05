package nexus.inventario.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import nexus.inventario.aplicacion.CalcularEstadisticasEquipadas;
import nexus.inventario.aplicacion.ConsultarEstadisticasEquipadas;
import nexus.inventario.aplicacion.ResolutorDeEstadisticasHeroe;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.aplicacion.ResolutorDeProductoHttp;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.FormulaDetalle;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HU-INV-006, criterio 4: expone el calculo por HTTP. El camino feliz usa
 * dobles en memoria que SI resuelven (mismos que CalcularEstadisticasEquipadasTest);
 * el caso 404 usa el adaptador HTTP real ResolutorDeProductoHttp contra un
 * HttpServer embebido que responde 404, ahora que HU-INV-007 (PR #203) ya
 * no esta bloqueado — confirma que el 404 real llega como Problem Details,
 * no como un 500 sin manejar.
 */
class EstadisticasEquipadasApiTest {

    private static final String JUGADOR_ID = "jugador-1";
    private static final String HEROE_ELEMENTO_ID = "elemento-mi-guerrero-tanque";
    private static final String HEROE_PRODUCTO_ID = "producto-guerrero-tanque";
    private static final String ESPADA_ELEMENTO_ID = "elemento-mi-espada";
    private static final String ESPADA_PRODUCTO_ID = "producto-espada-una-mano";

    private RepositorioInventariosEnMemoria repositorio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        Inventario inventario = Inventario.vacio(JUGADOR_ID)
                .agregar(new ElementoInventario(
                        HEROE_ELEMENTO_ID, HEROE_PRODUCTO_ID,
                        TipoElementoInventario.HEROE, "Mi Guerrero Tanque"))
                .agregar(new ElementoInventario(
                        ESPADA_ELEMENTO_ID, ESPADA_PRODUCTO_ID,
                        TipoElementoInventario.ARMA, "mi espada"));
        repositorio.guardar(inventario.equipar(HEROE_ELEMENTO_ID, ESPADA_ELEMENTO_ID));
    }

    @Test
    @DisplayName("GET estadisticas del heroe propio aplica el modificador del arma equipada")
    void consultarEstadisticasEquipadas() throws Exception {
        ResolutorDeProductoEnMemoria productos = new ResolutorDeProductoEnMemoria();
        productos.registrar(HEROE_PRODUCTO_ID,
                new ResolutorDeProducto.DetalleProducto(null, "HEROE", "Guerrero Tanque"));
        productos.registrar(ESPADA_PRODUCTO_ID,
                new ResolutorDeProducto.DetalleProducto("Espada de una mano", "ARMA", null));

        ResolutorDeEstadisticasHeroeEnMemoria heroes = new ResolutorDeEstadisticasHeroeEnMemoria();
        heroes.registrar("Guerrero Tanque", new EstadisticasHeroe(
                10, 44, 11,
                new FormulaDetalle(10, 1, 6),
                new FormulaDetalle(0, 1, 4),
                null));

        MockMvc mvc = construirMvc(productos, heroes);

        mvc.perform(get(ruta(HEROE_ELEMENTO_ID)).header("X-User-Name", JUGADOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroeId").value(HEROE_ELEMENTO_ID))
                .andExpect(jsonPath("$.vida").value(44))
                .andExpect(jsonPath("$.defensa").value(11))
                .andExpect(jsonPath("$.ataque.base").value(11)) // Espada de una mano: +1 ataque
                .andExpect(jsonPath("$.ataque.cantidadDados").value(1))
                .andExpect(jsonPath("$.ataque.caras").value(6))
                .andExpect(jsonPath("$.ataque.formula").value("11 + 1d6"))
                .andExpect(jsonPath("$.dano.formula").value("1d4"))
                .andExpect(jsonPath("$.sanar").doesNotExist());
    }

    @Test
    @DisplayName("responde 404 con Problem Details cuando el producto no existe en el catalogo real")
    void respondeNoEncontradoCuandoElProductoNoExisteEnElCatalogo() throws Exception {
        HttpServer servidorProductos = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidorProductos.createContext("/", exchange -> {
            byte[] cuerpo = "{\"detail\": \"No existe ningun producto con ese identificador\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, cuerpo.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidorProductos.start();

        try {
            URI baseUri = URI.create("http://localhost:" + servidorProductos.getAddress().getPort());
            ResolutorDeProductoHttp productos = new ResolutorDeProductoHttp(
                    baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());

            MockMvc mvc = construirMvc(productos, new ResolutorDeEstadisticasHeroeEnMemoria());

            String cuerpo = mvc.perform(get(ruta(HEROE_ELEMENTO_ID)).header("X-User-Name", JUGADOR_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Producto no encontrado"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(cuerpo).contains(HEROE_PRODUCTO_ID);
        } finally {
            servidorProductos.stop(0);
        }
    }

    private MockMvc construirMvc(ResolutorDeProducto productos, ResolutorDeEstadisticasHeroe heroes) {
        CalcularEstadisticasEquipadas calculo = new CalcularEstadisticasEquipadas(productos, heroes);
        ConsultarEstadisticasEquipadas consulta = new ConsultarEstadisticasEquipadas(repositorio, calculo);
        return MockMvcBuilders.standaloneSetup(new EstadisticasEquipadasController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    private String ruta(String heroeId) {
        return "/api/v1/inventario/heroes/" + heroeId + "/estadisticas";
    }

    // --- Dobles en memoria (mismo patron que CalcularEstadisticasEquipadasTest) ---

    private static class ResolutorDeProductoEnMemoria implements ResolutorDeProducto {
        private final Map<String, DetalleProducto> productos = new HashMap<>();

        void registrar(String id, DetalleProducto detalle) {
            productos.put(id, detalle);
        }

        @Override
        public DetalleProducto resolver(String productoId) {
            DetalleProducto detalle = productos.get(productoId);
            if (detalle == null) {
                throw new IllegalStateException("Producto no registrado en el doble: " + productoId);
            }
            return detalle;
        }
    }

    private static class ResolutorDeEstadisticasHeroeEnMemoria implements ResolutorDeEstadisticasHeroe {
        private final Map<String, EstadisticasHeroe> estadisticas = new HashMap<>();

        void registrar(String prototipo, EstadisticasHeroe valores) {
            estadisticas.put(prototipo, valores);
        }

        @Override
        public EstadisticasHeroe resolver(String prototipo) {
            EstadisticasHeroe valores = estadisticas.get(prototipo);
            if (valores == null) {
                throw new IllegalStateException("Prototipo no registrado en el doble: " + prototipo);
            }
            return valores;
        }
    }
}
