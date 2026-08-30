package nexus.inventario.aceptacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RepositorioDeInventarios repositorio;

    @Test
    @DisplayName("crear siempre persiste en el inventario del jugador autenticado")
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
                        .header("X-User-Name", "jugador-modificacion-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombrePropio\":\"Elemento alterado por A\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Inventario ajeno"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso sobre ese inventario."));

        assertEquals(inventarioBAntes, inventarioDe("jugador-modificacion-B"));
    }

    private String crear(String jugador, String producto, String nombre) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/inventario/elementos")
                        .header("X-User-Name", jugador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productoId":"%s","tipo":"ITEM","nombrePropio":"%s"}
                                """.formatted(producto, nombre)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(
                resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    private Inventario inventarioDe(String jugador) {
        return repositorio.buscarPorPropietario(jugador).orElseThrow();
    }
}
