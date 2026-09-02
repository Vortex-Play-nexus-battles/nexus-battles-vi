package nexus.inventario.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import nexus.inventario.aplicacion.CalcularEstadisticasEquipadas;
import nexus.inventario.aplicacion.ConsultarHeroeActivo;
import nexus.inventario.aplicacion.ResolutorDeEstadisticasHeroe;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.FormulaDetalle;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HU-SAL-003: 200 camino feliz, 204 sin heroe, 409 con multiples heroes.
 */
class HeroeActivoApiTest {

    private static final String JUGADOR_ID = "jugador-1";
    private static final String RUTA = "/api/v1/inventario/heroes/activo";

    private RepositorioInventariosEnMemoria repositorio;
    private ResolutorDeProductoEnMemoria productos;
    private ResolutorDeEstadisticasHeroeEnMemoria heroes;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        productos = new ResolutorDeProductoEnMemoria();
        heroes = new ResolutorDeEstadisticasHeroeEnMemoria();
        CalcularEstadisticasEquipadas calculo = new CalcularEstadisticasEquipadas(productos, heroes);
        ConsultarHeroeActivo consulta = new ConsultarHeroeActivo(repositorio, calculo);
        mvc = MockMvcBuilders.standaloneSetup(new HeroeActivoController(consulta))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    @DisplayName("GET responde 200 con el heroe activo y sus estadisticas equipadas")
    void respondeElHeroeActivo() throws Exception {
        productos.registrar("producto-guerrero", new ResolutorDeProducto.DetalleProducto(null, "HEROE", "Guerrero Tanque"));
        heroes.registrar("Guerrero Tanque", new EstadisticasHeroe(
                10, 44, 11, 1,
                new FormulaDetalle(10, 1, 6),
                new FormulaDetalle(0, 1, 4),
                null));
        repositorio.guardar(Inventario.vacio(JUGADOR_ID)
                .agregar(new ElementoInventario(
                        "heroe-1", "producto-guerrero", TipoElementoInventario.HEROE, "Mi Guerrero")));

        mvc.perform(get(RUTA).header("X-User-Name", JUGADOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Mi Guerrero"))
                .andExpect(jsonPath("$.vida").value(44))
                .andExpect(jsonPath("$.ataque").value(10))
                .andExpect(jsonPath("$.defensa").value(11))
                .andExpect(jsonPath("$.nivel").value(1));
    }

    @Test
    @DisplayName("GET responde 204 cuando el jugador no tiene ningun heroe")
    void respondeSinContenidoSinHeroe() throws Exception {
        repositorio.guardar(Inventario.vacio(JUGADOR_ID));

        mvc.perform(get(RUTA).header("X-User-Name", JUGADOR_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET responde 409 con Problem Details cuando el jugador tiene mas de un heroe")
    void respondeConflictoConMultiplesHeroes() throws Exception {
        repositorio.guardar(Inventario.vacio(JUGADOR_ID)
                .agregar(new ElementoInventario(
                        "heroe-1", "producto-guerrero", TipoElementoInventario.HEROE, "Mi Guerrero"))
                .agregar(new ElementoInventario(
                        "heroe-2", "producto-mago", TipoElementoInventario.HEROE, "Mi Mago")));

        mvc.perform(get(RUTA).header("X-User-Name", JUGADOR_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Heroe activo no definido"));
    }

    @Test
    @DisplayName("GET responde 401 sin identidad")
    void respondeNoAutenticadoSinIdentidad() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized());
    }

    // --- Dobles en memoria (mismo patron que EstadisticasEquipadasApiTest) ---

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
