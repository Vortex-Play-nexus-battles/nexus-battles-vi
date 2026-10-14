package nexus.api;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import nexus.aplicacion.ConsultarHistorialProductoServicio;
import nexus.aplicacion.RevertirProductoServicio;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@AutoConfigureMockMvc
class ReversionProductoApiTest {

        private static final String PRODUCTO_ID = "550e8400-e29b-41d4-a716-446655440000";
        private static final String RESPALDO_ID = "respaldo-1";

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ConsultarHistorialProductoServicio historial;

        @MockitoBean
        private RevertirProductoServicio reversion;

        @Test
        @DisplayName("el historial requiere un administrador y expone entradas de solo lectura")
        void consultaHistorialAdministrativo() throws Exception {
                when(historial.consultar(PRODUCTO_ID)).thenReturn(List.of(new CambioProductoVista(
                                RESPALDO_ID,
                                PRODUCTO_ID,
                                TipoCambioProducto.MODIFICACION,
                                List.of("nombre"),
                                "admin-1",
                                Instant.parse("2026-10-05T10:00:00Z"),
                                1,
                                2,
                                null,
                                true)));

                mvc.perform(get("/api/v1/productos/" + PRODUCTO_ID + "/historial"))
                                .andExpect(status().isUnauthorized());

                mvc.perform(get("/api/v1/productos/" + PRODUCTO_ID + "/historial")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
                                .andExpect(status().isForbidden());

                mvc.perform(get("/api/v1/productos/" + PRODUCTO_ID + "/historial")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].id").value(RESPALDO_ID))
                                .andExpect(jsonPath("$[0].autor").value("admin-1"))
                                .andExpect(jsonPath("$[0].revertible").value(true));
        }

        @Test
        @DisplayName("un administrador revierte y recibe el producto restaurado")
        void revierteProducto() throws Exception {
                when(reversion.revertir(eq(PRODUCTO_ID), eq(RESPALDO_ID), anyString()))
                                .thenReturn(producto("Espada solar", 3));

                mvc.perform(post("/api/v1/productos/" + PRODUCTO_ID + "/reversiones")
                                .with(jwt().jwt(token -> token.claim("uid", "admin-uid"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"respaldoId\":\"" + RESPALDO_ID + "\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(PRODUCTO_ID))
                                .andExpect(jsonPath("$.nombre").value("Espada solar"))
                                .andExpect(jsonPath("$.version").value(3));
        }

        @Test
        @DisplayName("un jugador no puede ejecutar reversiones")
        void jugadorNoRevierte() throws Exception {
                mvc.perform(post("/api/v1/productos/" + PRODUCTO_ID + "/reversiones")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"respaldoId\":\"" + RESPALDO_ID + "\"}"))
                                .andExpect(status().isForbidden());
        }

        private static Producto producto(String nombre, int version) {
                Instant fecha = Instant.parse("2026-10-05T10:00:00Z");
                return new Producto(
                                PRODUCTO_ID, nombre, "productos/espada.webp", "Arma", TipoProducto.ARMA,
                                10, 500, null, false, null, null, null, null, null, null, null, null,
                                null, null, null, 20, new BigDecimal("10"), EstadoProducto.ACTIVO,
                                version, fecha, fecha, null, null, null, "admin-uid", null, List.of());
        }
}
