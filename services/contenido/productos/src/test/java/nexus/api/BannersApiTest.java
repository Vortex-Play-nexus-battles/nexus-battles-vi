package nexus.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import nexus.dominio.Banner;
import nexus.persistencia.BannerRepository;
import org.junit.jupiter.api.BeforeEach;
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
class BannersApiTest {

        private static final String SOLICITUD_VALIDA = """
                {
                  "contenido": "Temporada de héroes disponible",
                  "publicarDesde": "2099-01-01T00:00:00Z",
                  "vigenteHasta": "2099-01-31T23:59:59Z"
                }
                """;

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private BannerRepository repositorio;

        @BeforeEach
        void simularPersistencia() {
                when(repositorio.save(any(Banner.class)))
                        .thenAnswer(invocacion -> invocacion.getArgument(0, Banner.class));
        }

        @Test
        @DisplayName("un administrador crea y programa un banner")
        void administradorCreaBanner() throws Exception {
                mvc.perform(post("/api/v1/banners")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SOLICITUD_VALIDA))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andExpect(jsonPath("$.contenido")
                                .value("Temporada de héroes disponible"))
                        .andExpect(jsonPath("$.retirado").value(false));
        }

        @Test
        @DisplayName("crear un banner sin token responde 401")
        void crearSinTokenNoEntra() throws Exception {
                mvc.perform(post("/api/v1/banners")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SOLICITUD_VALIDA))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un jugador no puede administrar banners")
        void jugadorNoAdministra() throws Exception {
                mvc.perform(post("/api/v1/banners")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_JUGADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SOLICITUD_VALIDA))
                        .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("rechaza una vigencia que termina antes de publicarse")
        void rechazaPeriodoInvalido() throws Exception {
                String solicitud = SOLICITUD_VALIDA
                        .replace("2099-01-31T23:59:59Z", "2098-12-31T23:59:59Z");

                mvc.perform(post("/api/v1/banners")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(solicitud))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("la consulta publica devuelve solamente lo vigente")
        void consultaVigentesSinAutenticacion() throws Exception {
                Instant ahora = Instant.now();
                Banner banner = new Banner(
                        "banner-1",
                        "Oferta vigente",
                        ahora.minusSeconds(60),
                        ahora.plusSeconds(3600),
                        false,
                        ahora,
                        ahora);
                when(repositorio
                        .findByRetiradoFalseAndPublicarDesdeLessThanEqualAndVigenteHastaGreaterThanOrderByPublicarDesdeDesc(
                                any(Instant.class),
                                any(Instant.class)))
                        .thenReturn(List.of(banner));

                mvc.perform(get("/api/v1/banners/vigentes"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].id").value("banner-1"))
                        .andExpect(jsonPath("$[0].contenido").value("Oferta vigente"));
        }

        @Test
        @DisplayName("un administrador edita un banner")
        void administradorEditaBanner() throws Exception {
                when(repositorio.findById("banner-1"))
                        .thenReturn(Optional.of(bannerExistente()));

                mvc.perform(put("/api/v1/banners/banner-1")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SOLICITUD_VALIDA))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value("banner-1"));
        }

        @Test
        @DisplayName("retirar un banner es logico y responde 204")
        void administradorRetiraBanner() throws Exception {
                when(repositorio.findById("banner-1"))
                        .thenReturn(Optional.of(bannerExistente()));

                mvc.perform(delete("/api/v1/banners/banner-1")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                        .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("editar un banner inexistente responde 404")
        void editarInexistente() throws Exception {
                when(repositorio.findById("ausente")).thenReturn(Optional.empty());

                mvc.perform(put("/api/v1/banners/ausente")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SOLICITUD_VALIDA))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.type")
                                .value("urn:nexus:problema:banner-no-encontrado"));
        }

        private Banner bannerExistente() {
                Instant ahora = Instant.now();
                return new Banner(
                        "banner-1",
                        "Mensaje anterior",
                        ahora.minusSeconds(60),
                        ahora.plusSeconds(3600),
                        false,
                        ahora,
                        ahora);
        }
}
