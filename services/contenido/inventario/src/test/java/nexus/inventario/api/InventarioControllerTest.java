package nexus.inventario.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Contrato HTTP de SCRUM-318 para que la vitrina consuma el inventario. */
class InventarioControllerTest {

    private RepositorioDeInventarios repositorio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDeInventarios.class);
        ConsultarInventarioPaginado consulta = new ConsultarInventarioPaginado(repositorio);
        mvc = MockMvcBuilders.standaloneSetup(new InventarioController(consulta)).build();
    }

    @Test
    @DisplayName("GET devuelve 16 productos y metadatos de paginacion")
    void consultarPrimeraPagina() throws Exception {
        when(repositorio.buscarPorPropietario("jugador-A"))
                .thenReturn(Optional.of(inventarioConElementos(17)));

        mvc.perform(get("/api/v1/inventarios/jugador-A/elementos")
                        .queryParam("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(16))
                .andExpect(jsonPath("$.numero").value(0))
                .andExpect(jsonPath("$.tamanio").value(16))
                .andExpect(jsonPath("$.totalElementos").value(17))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.ultima").value(false));
    }

    @Test
    @DisplayName("GET de un jugador sin productos devuelve 200 y una lista vacia")
    void consultarInventarioVacio() throws Exception {
        when(repositorio.buscarPorPropietario("jugador-vacio"))
                .thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/inventarios/jugador-vacio/elementos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.ultima").value(true));
    }

    @Test
    @DisplayName("GET rechaza un numero de pagina negativo")
    void paginaNegativa() throws Exception {
        mvc.perform(get("/api/v1/inventarios/jugador-A/elementos")
                        .queryParam("pagina", "-1"))
                .andExpect(status().isBadRequest());
    }

    private Inventario inventarioConElementos(int cantidad) {
        List<ElementoInventario> elementos = IntStream.rangeClosed(1, cantidad)
                .mapToObj(numero -> new ElementoInventario(
                        "elemento-" + numero,
                        "producto-" + numero,
                        TipoElementoInventario.ITEM,
                        "Producto " + numero))
                .toList();
        return new Inventario("inventario-A", "jugador-A", elementos);
    }
}
