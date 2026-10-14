package nexus.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
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

import nexus.auditoria.ObjetoAuditado;
import nexus.auditoria.OperacionAdministrativa;
import nexus.auditoria.OperacionAdministrativaRepository;
import nexus.auditoria.TipoOperacion;
import nexus.dominio.Banner;
import nexus.persistencia.BannerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

        /** SA-18: el doble de {@code ConfiguracionOperacionesParaPruebas}. */
        @Autowired
        @Qualifier("operacionesAdministrativasSimuladas")
        private OperacionAdministrativaRepository operaciones;

        @BeforeEach
        void simularPersistencia() {
                reset(operaciones);
                when(repositorio.save(any(Banner.class)))
                        .thenAnswer(invocacion -> invocacion.getArgument(0, Banner.class));
        }

        private OperacionAdministrativa operacionRegistrada() {
                ArgumentCaptor<OperacionAdministrativa> captor =
                        ArgumentCaptor.forClass(OperacionAdministrativa.class);
                verify(operaciones).insert(captor.capture());
                return captor.getValue();
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
        @DisplayName("un administrador consulta todos los banners")
        void administradorConsultaTodos() throws Exception {
                when(repositorio.findAllByOrderByPublicarDesdeDesc())
                        .thenReturn(List.of(bannerExistente()));

                mvc.perform(get("/api/v1/banners")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].id").value("banner-1"));
        }

        @Test
        @DisplayName("un jugador no puede listar todos los banners")
        void jugadorNoConsultaTodos() throws Exception {
                mvc.perform(get("/api/v1/banners")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_JUGADOR"))))
                        .andExpect(status().isForbidden());
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

        @Test
        @DisplayName("SA-18: publicar deja quién lo hizo en el banner y la operación con su IP")
        void publicarDejaAutorYOperacion() throws Exception {
                mvc.perform(post("/api/v1/banners")
                                .with(jwt().jwt(token -> token.subject("uid-ana"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                                .header("X-Real-IP", "181.52.10.4")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SOLICITUD_VALIDA))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.creadoPor").value("uid-ana"))
                        .andExpect(jsonPath("$.modificadoPor").value("uid-ana"));

                OperacionAdministrativa operacion = operacionRegistrada();
                assertThat(operacion.tipo()).isEqualTo(TipoOperacion.ALTA_BANNER);
                assertThat(operacion.objeto()).isEqualTo(ObjetoAuditado.BANNER);
                assertThat(operacion.autor()).isEqualTo("uid-ana");
                assertThat(operacion.ipOrigen()).isEqualTo("181.52.10.4");
                assertThat(operacion.nombre()).isEqualTo("Temporada de héroes disponible");
                assertThat(operacion.valorAnterior()).isNull();
        }

        @Test
        @DisplayName("SA-18: retirar con motivo lo deja en la operación, con el banner antes y después")
        void retirarConMotivo() throws Exception {
                when(repositorio.findById("banner-1"))
                        .thenReturn(Optional.of(bannerExistente()));

                mvc.perform(delete("/api/v1/banners/banner-1")
                                .param("motivo", "Evento cancelado")
                                .with(jwt().jwt(token -> token.subject("uid-ana"))
                                        .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR"))))
                        .andExpect(status().isNoContent());

                ArgumentCaptor<Banner> guardado = ArgumentCaptor.forClass(Banner.class);
                verify(repositorio).save(guardado.capture());
                assertThat(guardado.getValue().retirado()).isTrue();
                assertThat(guardado.getValue().modificadoPor()).isEqualTo("uid-ana");
                OperacionAdministrativa operacion = operacionRegistrada();
                assertThat(operacion.tipo()).isEqualTo(TipoOperacion.RETIRO_BANNER);
                assertThat(operacion.motivo()).isEqualTo("Evento cancelado");
                assertThat(operacion.valorAnterior()).doesNotContain("Retirado");
                assertThat(operacion.valorNuevo()).endsWith("Retirado");
        }

        @Test
        @DisplayName("SA-18: un motivo de más de 500 caracteres responde 400 y no retira nada")
        void motivoDemasiadoLargo() throws Exception {
                mvc.perform(delete("/api/v1/banners/banner-1")
                                .param("motivo", "x".repeat(501))
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                        .andExpect(status().isBadRequest());

                verify(repositorio, never()).save(any(Banner.class));
        }

        @Test
        @DisplayName("SA-18: retirar uno ya retirado no escribe ni registra otra vez")
        void retirarDosVeces() throws Exception {
                Banner retirado = bannerExistente();
                when(repositorio.findById("banner-1")).thenReturn(Optional.of(new Banner(
                        retirado.id(), retirado.contenido(), retirado.publicarDesde(), retirado.vigenteHasta(),
                        true, retirado.creadoEn(), retirado.modificadoEn())));

                mvc.perform(delete("/api/v1/banners/banner-1")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                        .andExpect(status().isNoContent());

                verify(repositorio, never()).save(any(Banner.class));
                verify(operaciones, never()).insert(any(OperacionAdministrativa.class));
        }

        @Test
        @DisplayName("SA-18: el historial de un banner, para la administración y sin la IP")
        void historialSinIp() throws Exception {
                when(repositorio.findById("banner-1"))
                        .thenReturn(Optional.of(bannerExistente()));
                when(operaciones.findByObjetoAndObjetoIdOrderByOcurridaEnDesc(ObjetoAuditado.BANNER, "banner-1"))
                        .thenReturn(List.of(new OperacionAdministrativa("op-1",
                                Instant.parse("2026-10-14T15:00:00Z"), "uid-ana", TipoOperacion.RETIRO_BANNER,
                                ObjetoAuditado.BANNER, "banner-1", "Mensaje anterior", "«Mensaje anterior»",
                                "«Mensaje anterior» · Retirado", "Evento cancelado", "Retiro de un banner",
                                "181.52.10.4")));

                mvc.perform(get("/api/v1/banners/banner-1/historial")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].id").value("op-1"))
                        .andExpect(jsonPath("$[0].fecha").value("2026-10-14T15:00:00Z"))
                        .andExpect(jsonPath("$[0].autor").value("uid-ana"))
                        .andExpect(jsonPath("$[0].tipo").value("RETIRO_BANNER"))
                        .andExpect(jsonPath("$[0].motivo").value("Evento cancelado"))
                        .andExpect(jsonPath("$[0].descripcion").value("Retiro de un banner"))
                        .andExpect(jsonPath("$[0].ipOrigen").doesNotExist());
        }

        @Test
        @DisplayName("SA-18: un jugador no ve el historial de un banner (403) y uno inexistente es 404")
        void historialProtegido() throws Exception {
                mvc.perform(get("/api/v1/banners/banner-1/historial")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_JUGADOR"))))
                        .andExpect(status().isForbidden());
                when(repositorio.findById("ausente")).thenReturn(Optional.empty());
                mvc.perform(get("/api/v1/banners/ausente/historial")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                        .andExpect(status().isNotFound());
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
