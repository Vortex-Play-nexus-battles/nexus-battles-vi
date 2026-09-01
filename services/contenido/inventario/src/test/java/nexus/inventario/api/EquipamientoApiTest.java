package nexus.inventario.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nexus.inventario.aplicacion.GestionarEquipamiento;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EquipamientoApiTest {

    private RepositorioInventariosEnMemoria repositorio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        mvc = MockMvcBuilders.standaloneSetup(
                        new EquipamientoController(new GestionarEquipamiento(repositorio)))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    @DisplayName("PUT equipa y DELETE desequipa un elemento propio")
    void equiparYDesequipar() throws Exception {
        guardarInventario("jugador-A", "heroe-A", "arma-A", "arma-B", "arma-C");

        mvc.perform(put(ruta("heroe-A", "arma-A")).header("X-User-Name", "jugador-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroeId").value("heroe-A"))
                .andExpect(jsonPath("$.armas[0]").value("arma-A"));

        mvc.perform(delete(ruta("heroe-A", "arma-A")).header("X-User-Name", "jugador-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.armas.length()").value(0));
    }

    @Test
    @DisplayName("GET consulta el equipamiento actual del heroe propio")
    void consultar() throws Exception {
        guardarInventario("jugador-A", "heroe-A", "arma-A");

        mvc.perform(get("/api/v1/inventario/heroes/heroe-A/equipamiento")
                        .header("X-User-Name", "jugador-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heroeId").value("heroe-A"))
                .andExpect(jsonPath("$.armaduras").isMap())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("exceder dos armas responde conflicto y no guarda la tercera")
    void rechazarTerceraArma() throws Exception {
        guardarInventario("jugador-A", "heroe-A", "arma-A", "arma-B", "arma-C");
        mvc.perform(put(ruta("heroe-A", "arma-A")).header("X-User-Name", "jugador-A"));
        mvc.perform(put(ruta("heroe-A", "arma-B")).header("X-User-Name", "jugador-A"));

        mvc.perform(put(ruta("heroe-A", "arma-C")).header("X-User-Name", "jugador-A"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Limite de equipamiento"));

        assertEquals(2, repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().equipamiento("heroe-A").armas().size());
    }

    @Test
    @DisplayName("equipar sobre un heroe ajeno responde prohibido")
    void rechazarHeroeAjeno() throws Exception {
        guardarInventario("jugador-B", "heroe-B", "arma-B");

        mvc.perform(put(ruta("heroe-B", "arma-B")).header("X-User-Name", "jugador-A"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Inventario ajeno"));
    }

    private String ruta(String heroeId, String elementoId) {
        return "/api/v1/inventario/heroes/" + heroeId + "/equipamiento/" + elementoId;
    }

    private void guardarInventario(String propietario, String heroeId, String... armas) {
        Inventario inventario = Inventario.vacio(propietario).agregar(new ElementoInventario(
                heroeId, "producto-" + heroeId, TipoElementoInventario.HEROE, heroeId));
        for (String arma : armas) {
            inventario = inventario.agregar(new ElementoInventario(
                    arma, "producto-" + arma, TipoElementoInventario.ARMA, arma));
        }
        repositorio.guardar(inventario);
    }
}
