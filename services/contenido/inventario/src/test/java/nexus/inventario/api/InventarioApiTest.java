package nexus.inventario.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.GestionarInventario;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InventarioApiTest {

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarInventario gestion;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        gestion = new GestionarInventario(repositorio);
        mvc = MockMvcBuilders.standaloneSetup(
                        new InventarioController(
                                gestion, new ConsultarInventarioPaginado(repositorio)))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    @DisplayName("POST crea en el inventario indicado por la identidad autenticada")
    void crearElemento() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-1","tipo":"ITEM","nombrePropio":"Amuleto de Niebla"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productoId").value("producto-1"))
                .andExpect(jsonPath("$.nombrePropio").value("Amuleto de Niebla"));

        mvc.perform(post("/api/v1/inventario/elementos")
                        .header("X-User-Name", "jugador-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-2","tipo":"HEROE","nombrePropio":"Mi guerrero"}
                                """))
                .andExpect(status().isCreated());

        assertEquals(1, repositorio.buscarPorPropietario("jugador-A").orElseThrow().elementos().size());
        assertEquals(1, repositorio.buscarPorPropietario("jugador-B").orElseThrow().elementos().size());
    }

    @Test
    @DisplayName("PATCH permite al propietario modificar su elemento")
    void modificarElementoPropio() throws Exception {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto de Niebla");

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto de Bruma\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePropio").value("Amuleto de Bruma"));
    }

    @Test
    @DisplayName("PATCH rechaza modificar el elemento de otro jugador y conserva sus datos")
    void rechazarModificacionAjena() throws Exception {
        ElementoInventario elementoDeB = gestion.crear(
                "jugador-B", "producto-1", TipoElementoInventario.ITEM, "Daga Corta");

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", elementoDeB.id())
                        .header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Daga Robada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Inventario ajeno"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso sobre ese inventario."));

        assertEquals("Daga Corta", repositorio.buscarPorPropietario("jugador-B").orElseThrow()
                .elementos().getFirst().nombrePropio());
    }

    @Test
    @DisplayName("una solicitud sin identidad autenticada responde 401")
    void identidadRequerida() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-1","tipo":"ITEM","nombrePropio":"Daga"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Identidad requerida"));
    }

    @Test
    @DisplayName("POST con datos invalidos responde 400 sin escribir")
    void solicitudInvalida() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"\",\"tipo\":\"ITEM\",\"nombrePropio\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"));

        assertEquals(0, repositorio.buscarPorPropietario("jugador-A").stream().count());
    }

    @Test
    @DisplayName("PATCH de un elemento inexistente responde 404")
    void elementoInexistente() throws Exception {
        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", "elemento-inexistente")
                        .header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Otro nombre\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Elemento no encontrado"));
    }

    @Test
    @DisplayName("una escritura fallida responde 503 y conserva el estado completo anterior")
    void escrituraFallidaEsAtomica() throws Exception {
        ElementoInventario creado = gestion.crear(
                "jugador-A", "producto-1", TipoElementoInventario.ITEM, "Amuleto original");
        repositorio.fallarSiguienteGuardado();

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", creado.id())
                        .header("X-User-Name", "jugador-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Amuleto incompleto\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Inventario no disponible"));

        ElementoInventario persistido = repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().elemento(creado.id());
        assertEquals("Amuleto original", persistido.nombrePropio());
        assertEquals("producto-1", persistido.productoId());
    }

    @Test
    @DisplayName("GET entrega la vitrina en paginas de dieciseis del inventario propio")
    void consultarPaginaDeLaVitrina() throws Exception {
        for (int i = 0; i < 20; i++) {
            gestion.crear("jugador-A", "producto-" + i,
                    TipoElementoInventario.ARMA, "Espada " + i);
        }

        mvc.perform(get("/api/v1/inventario/elementos")
                        .header("X-User-Name", "jugador-A")
                        .param("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(16))
                .andExpect(jsonPath("$.totalElementos").value(20))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.ultima").value(false));
    }

    @Test
    @DisplayName("GET de un jugador sin inventario responde 200 con la pagina vacia")
    void consultarSinInventario() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos")
                        .header("X-User-Name", "jugador-nuevo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    @DisplayName("GET sin identidad no expone ningun inventario")
    void consultarSinIdentidad() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET con una pagina negativa es una solicitud invalida")
    void consultarPaginaNegativa() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos")
                        .header("X-User-Name", "jugador-A")
                        .param("pagina", "-1"))
                .andExpect(status().isBadRequest());
    }
}
