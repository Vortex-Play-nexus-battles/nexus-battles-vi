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

    /** 1.6.0 (B9): misiones consulta el heroe antes de matricularlo, con su credencial de servicio. */
    @Test
    void consultaInternaAceptaAlServicioDeMisiones() throws Exception {
        UUID productoId = UUID.randomUUID();
        UUID propietarioUid = UUID.randomUUID();
        UUID ejecucion = UUID.randomUUID();
        when(consultaElemento.consultar("heroe-1")).thenReturn(new DetalleElementoInventario(
                "heroe-1", productoId, propietarioUid, false, false, null,
                nexus.inventario.dominio.TipoElementoInventario.HEROE, "Vorn", 2, 12.5, ejecucion));

        mvc.perform(get("/api/v1/inventario/elementos/heroe-1")
                        .with(jwt().jwt(token -> token.claim("azp", "misiones"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_SERVICIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("HEROE"))
                .andExpect(jsonPath("$.nivel").value(2))
                .andExpect(jsonPath("$.ejecucionMisionId").value(ejecucion.toString()));

        mvc.perform(get("/api/v1/inventario/elementos/heroe-1")
                        .with(jwt().jwt(token -> token.claim("azp", "misiones"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaInternaRechazaTokenDeOtroCliente() throws Exception {
        mvc.perform(get("/api/v1/inventario/elementos/elemento-1")
                        .with(jwt().jwt(token -> token.claim("azp", "cliente-jugador"))))
                .andExpect(status().isForbidden());
    }
}
