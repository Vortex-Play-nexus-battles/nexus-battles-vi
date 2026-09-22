package nexus.inventario.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import nexus.inventario.aplicacion.BuscarElementosInventario;
import nexus.inventario.aplicacion.ConsultarElementoInventario;
import nexus.inventario.aplicacion.ConsultarInventarioPaginado;
import nexus.inventario.aplicacion.DetalleElementoInventario;
import nexus.inventario.aplicacion.GestionarInventario;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.configuracion.SeguridadConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InventarioController.class)
@Import({SeguridadConfig.class, IdentidadDelLlamador.class})
class SeguridadConsultaElementoTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GestionarInventario gestion;

    @MockitoBean
    private ConsultarInventarioPaginado consulta;

    @MockitoBean
    private BuscarElementosInventario busqueda;

    @MockitoBean
    private ConsultarElementoInventario consultaElemento;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void consultaInternaExigeBearerToken() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos/elemento-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultaInternaAceptaBearerTokenValido() throws Exception {
        UUID productoId = UUID.randomUUID();
        UUID propietarioUid = UUID.randomUUID();
        when(consultaElemento.consultar("elemento-1")).thenReturn(
                new DetalleElementoInventario(
                        "elemento-1", productoId, propietarioUid, false, true, null));

        mvc.perform(get("/api/v1/inventario/elementos/elemento-1")
                        .with(jwt().jwt(token -> token.claim("azp", "ms-subastas"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(productoId.toString()))
                .andExpect(jsonPath("$.propietarioUid").value(propietarioUid.toString()));
    }

    @Test
    void consultaInternaRechazaTokenDeOtroCliente() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos/elemento-1")
                        .with(jwt().jwt(token -> token.claim("azp", "cliente-jugador"))))
                .andExpect(status().isForbidden());
    }
}
