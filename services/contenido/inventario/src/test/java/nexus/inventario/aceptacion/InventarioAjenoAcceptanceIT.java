package nexus.inventario.aceptacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import nexus.inventario.api.ComoLlamador;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class InventarioAjenoAcceptanceIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    /** Los tokens de jugador se verifican contra un JWKS real (ADR-002). */
    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RepositorioDeInventarios repositorio;

    /**
     * El inventario ahora verifica cada producto en el catalogo. Aqui el
     * catalogo es un doble que dice que los productos de la prueba existen
     * como ITEM activo: lo que se verifica es la propiedad, no el catalogo.
     */
    @MockitoBean
    private ResolutorDeProducto productos;

    @BeforeEach
    void catalogoConLosProductosDeLaPrueba() {
        when(productos.resolver(anyString()))
                .thenReturn(new ResolutorDeProducto.DetalleProducto("Elemento", "ITEM", null, "ACTIVO"));
    }

    @Test
    @DisplayName("crear persiste solo en el inventario del jugador por el que actua el servicio")
    void crearSoloEnInventarioPropio() throws Exception {
        crear("jugador-creacion-B", "producto-B", "Elemento de B");
        Inventario inventarioBAntes = inventarioDe("jugador-creacion-B");

        crear("jugador-creacion-A", "producto-A", "Elemento de A");

        assertEquals(inventarioBAntes, inventarioDe("jugador-creacion-B"));
        assertEquals("producto-A", inventarioDe("jugador-creacion-A")
                .elementos().getFirst().productoId());
    }

    @Test
    @DisplayName("jugador A no modifica el inventario B y el documento persiste sin cambios")
    void rechazarModificacionDeInventarioAjeno() throws Exception {
        crear("jugador-modificacion-A", "producto-A", "Elemento de A");
        String elementoDeB = crear("jugador-modificacion-B", "producto-B", "Elemento original de B");
        Inventario inventarioBAntes = inventarioDe("jugador-modificacion-B");

        mvc.perform(patch("/api/v1/inventario/elementos/{elementoId}", elementoDeB)
                        .header("Authorization", ComoLlamador.portadorDeJugador("jugador-modificacion-A", uidDe("jugador-modificacion-A")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Elemento alterado por A\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Inventario ajeno"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso sobre ese inventario."));

        assertEquals(inventarioBAntes, inventarioDe("jugador-modificacion-B"));
    }

    @Test
    @DisplayName("B4: un jugador ya no se crea elementos, ni en su inventario ni en el de otro: 403 y nada guardado")
    void unJugadorNoSeCreaElementos() throws Exception {
        mvc.perform(post("/api/v1/inventario/elementos")
                        .header("Authorization", ComoLlamador.portadorDeJugador("jugador-gratis", uidDe("jugador-gratis")))
                        .header("X-User-Name", uidDe("jugador-gratis").toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"producto-gratis","tipo":"ITEM","nombrePropio":"Gratis"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));

        assertEquals(java.util.Optional.empty(), repositorio.buscarPorPropietario(uidDe("jugador-gratis").toString()));
    }

    /**
     * Desde B4 crear un elemento es de un servicio (el paquete inicial de
     * ms-identidad lo hace asi) o de un administrador: la prueba crea como
     * servicio, declarando al jugador en X-User-Name.
     */
    private String crear(String jugador, String producto, String nombre) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/inventario/elementos")
                        .header("Authorization", ComoLlamador.portadorDeServicio("ms-identidad"))
                        .header("X-User-Name", uidDe(jugador).toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"%s","tipo":"ITEM","nombrePropio":"%s"}
                                """.formatted(producto, nombre)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    /** El propietario es el identificador estable del token; el apodo es solo el nombre del caso. */
    private static final Map<String, UUID> UIDS = new HashMap<>();

    private static UUID uidDe(String jugador) {
        return UIDS.computeIfAbsent(jugador, apodo -> UUID.randomUUID());
    }

    private Inventario inventarioDe(String jugador) {
        return repositorio.buscarPorPropietario(uidDe(jugador).toString()).orElseThrow();
    }
}
