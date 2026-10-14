package nexus.inventario.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import nexus.inventario.aplicacion.CategoriaInventario;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.PaginaInventario;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.configuracion.SeguridadConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El inventario de una cuenta para su ficha administrativa — inventario.yaml
 * 1.8.0. Cadena de seguridad real y tokens firmados de verdad: lo que se prueba
 * es que solo la administracion lee el inventario de otro.
 */
@WebMvcTest(controllers = InventarioDeUsuarioController.class)
@Import({SeguridadConfig.class, IdentidadDelLlamador.class, DecodificadorDePrueba.class, ManejadorDeErrores.class})
@DisplayName("GET /inventario/usuarios/{uid}/elementos (1.8.0)")
class InventarioDeUsuarioTest {

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final UUID CUENTA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String RUTA = "/api/v1/inventario/usuarios/" + CUENTA + "/elementos";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarInventarioPaginado consulta;

    private static String con(String rol) {
        return "Bearer " + EMISOR.tokenDeUsuario("ada", UUID.randomUUID(), rol);
    }

    @Test
    @DisplayName("Administrador y Super Administrador leen el inventario de la cuenta, con su pagina y su categoria")
    void laAdministracionLeeElInventarioDeOtro() throws Exception {
        when(consulta.consultar(CUENTA.toString(), 2, CategoriaInventario.HEROES))
                .thenReturn(new PaginaInventario(List.of(), 2, 16, 40, 3, false));

        for (String rol : List.of("ADMINISTRADOR", "SUPER_ADMINISTRADOR")) {
            mvc.perform(get(RUTA).param("pagina", "2").param("categoria", "HEROES")
                            .header(HttpHeaders.AUTHORIZATION, con(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.numero").value(2))
                    .andExpect(jsonPath("$.totalElementos").value(40));
        }
        verify(consulta, org.mockito.Mockito.times(2)).consultar(CUENTA.toString(), 2, CategoriaInventario.HEROES);
    }

    @Test
    @DisplayName("un jugador, un moderador o un servicio no leen el inventario de otro (403); sin token, 401")
    void nadieMasLoLee() throws Exception {
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeJugador("lyra", CUENTA)))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, con("MODERADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("ms-subastas")))
                .andExpect(status().isForbidden());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        // Ninguna escritura cuelga de esta ruta.
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, con("SUPER_ADMINISTRADOR")))
                .andExpect(status().isForbidden());
        verify(consulta, never()).consultar(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("un uid que no es un UUID es 400 y no se consulta nada")
    void uidInvalido() throws Exception {
        mvc.perform(get("/api/v1/inventario/usuarios/lyra_roja/elementos")
                        .header(HttpHeaders.AUTHORIZATION, con("ADMINISTRADOR")))
                .andExpect(status().isBadRequest());
        verify(consulta, never()).consultar(anyString(), anyInt(), any());
    }
}
