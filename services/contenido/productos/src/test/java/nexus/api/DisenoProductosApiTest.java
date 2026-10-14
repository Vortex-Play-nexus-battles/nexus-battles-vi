package nexus.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import nexus.aplicacion.GestionarVersionesDisenoServicio;
import nexus.aplicacion.ProyeccionDeProductos;
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
class DisenoProductosApiTest {

        private static final String PRODUCTO_ID = "550e8400-e29b-41d4-a716-446655440000";

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private GestionarVersionesDisenoServicio versiones;

        @MockitoBean
        private ProyeccionDeProductos proyeccion;

        @Test
        @DisplayName("un administrador consulta las versiones de diseño con fecha y autor")
        void consultaVersiones() throws Exception {
                when(versiones.consultar(PRODUCTO_ID)).thenReturn(List.of(new VersionDisenoVista(
                                3,
                                Instant.parse("2026-10-11T15:00:00Z"),
                                "admin-uid",
                                "actual.webp",
                                "Diseño actual",
                                true)));

                mvc.perform(get("/api/v1/productos/" + PRODUCTO_ID + "/diseno/versiones")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].version").value(3))
                                .andExpect(jsonPath("$[0].autor").value("admin-uid"));
        }

        @Test
        @DisplayName("un jugador no puede consultar ni importar diseños")
        void jugadorNoAdministraDisenos() throws Exception {
                mvc.perform(get("/api/v1/productos/" + PRODUCTO_ID + "/diseno/versiones")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
                                .andExpect(status().isForbidden());

                mvc.perform(post("/api/v1/productos/" + PRODUCTO_ID + "/diseno/importaciones")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("una plantilla inválida responde 400 antes de alterar el producto")
        void plantillaInvalidaNoAlteraElProducto() throws Exception {
                String invalida = """
                                {
                                  "formato": "formato-ajeno",
                                  "versionFormato": 99,
                                  "productoOrigenId": "origen",
                                  "versionProducto": 2,
                                  "imagen": "",
                                  "descripcion": "Diseño",
                                  "exportadaEn": "2026-10-11T15:00:00Z"
                                }
                                """;

                mvc.perform(post("/api/v1/productos/" + PRODUCTO_ID + "/diseno/importaciones")
                                .with(jwt().jwt(token -> token.claim("uid", "admin-uid"))
                                                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalida))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(versiones);
        }
}
