package nexus.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
class ProductosApiTest {

        private static final String PRODUCTO_VALIDO = """
                {
                  "nombre": "Arma de prueba",
                  "imagen": "productos/arma-prueba.webp",
                  "descripcion": "Arma creada para verificar el contrato",
                  "tipo": "ARMA",
                  "tiraje": 100,
                  "precioCreditos": 500,
                  "premium": false,
                  "poderDeAtaque": 25,
                  "tasaDeCaida": 10
                }
                """;

        @Autowired
        private MockMvc mvc;
        @MockitoBean
        private JwtDecoder jwtDecoder;

        @Test
        @DisplayName("rechaza la creacion cuando no se envia un token")
        void requiereAutenticacion() throws Exception {
                mvc.perform(post("/api/v1/productos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCTO_VALIDO))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rechaza un jugador autenticado sin permiso administrativo")
        void rechazaUsuarioSinPermiso() throws Exception {
                publicarComo("ROLE_JUGADOR", PRODUCTO_VALIDO)
                        .andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "permite crear productos con {0}")
        @ValueSource(strings = {
                "ROLE_ADMINISTRADOR",
                "ROLE_SUPER_ADMINISTRADOR"
        })
        void permiteRolesAdministrativos(String autoridad) throws Exception {
                publicarComo(autoridad, PRODUCTO_VALIDO)
                        .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "rechaza la ausencia del campo obligatorio {0}")
        @ValueSource(strings = {
                "nombre",
                "imagen",
                "descripcion",
                "tipo",
                "tiraje",
                "premium"
        })
        void rechazaCamposObligatoriosAusentes(String campo) throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", sinCampo(PRODUCTO_VALIDO, campo))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza un tipo de producto no permitido")
        void rechazaTipoNoPermitido() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"tipo\": \"ARMA\"",
                        "\"tipo\": \"POCION\"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza un precio en creditos negativo")
        void rechazaPrecioNegativo() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"precioCreditos\": 500",
                        "\"precioCreditos\": -1");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza tiraje cero")
        void rechazaTirajeCero() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"tiraje\": 100",
                        "\"tiraje\": 0");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

                @Test
        @DisplayName("un producto premium exige precio en moneda real")
        void premiumExigePrecioMonedaReal() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"premium\": false",
                        "\"premium\": true");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un producto no premium exige precio en creditos")
        void noPremiumExigePrecioCreditos() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_VALIDO, "precioCreditos"))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un arma exige poder de ataque")
        void armaExigePoderDeAtaque() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_VALIDO, "poderDeAtaque"))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un arma exige tasa de caida")
        void armaExigeTasaDeCaida() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_VALIDO, "tasaDeCaida"))
                        .andExpect(status().isBadRequest());
        }

        private ResultActions publicarComo(String autoridad, String cuerpo) throws Exception {
                return mvc.perform(post("/api/v1/productos")
                        .with(jwt().authorities(new SimpleGrantedAuthority(autoridad)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
        }

        private static String sinCampo(String json, String campo) {
        String resultado = json.replaceFirst(
                "(?m)^\\s*\"" + campo + "\"\\s*:\\s*.*\\R",
                "");

        return resultado.replaceFirst(",\\s*}", "\n}");

    }

}