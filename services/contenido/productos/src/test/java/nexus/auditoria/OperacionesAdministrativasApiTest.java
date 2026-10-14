package nexus.auditoria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SA-18, productos.yaml 1.12.0 — {@code GET /api/v1/productos/auditoria/operaciones}
 * con tokens del emisor real y la cadena de seguridad completa.
 *
 * <p>Las operaciones llevan quién actuó y desde qué IP: las lee solo la
 * bitácora central (ms-cumplimiento). Otro servicio, aunque tenga credencial,
 * recibe 403; un usuario —también un Super Administrador— 403; sin token 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Productos · operaciones administrativas para la bitácora central (1.12.0)")
class OperacionesAdministrativasApiTest {

        private static final String RUTA = "/api/v1/productos/auditoria/operaciones";
        private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
        private static final Instant MOMENTO = Instant.parse("2026-10-14T15:30:00.250Z");

        @DynamicPropertySource
        static void apuntarAlEmisorReal(DynamicPropertyRegistry registro) {
                EmisorDeTokensDePrueba.registrarJwks(registro);
        }

        @Autowired
        private MockMvc mvc;

        @Autowired
        @Qualifier("operacionesAdministrativasSimuladas")
        private OperacionAdministrativaRepository operaciones;

        /** El catálogo no es el sujeto; sin doble, el contexto buscaría MongoDB. */
        @MockitoBean
        private ProductoRepository productos;

        @BeforeEach
        void preparar() {
                reset(operaciones);
                when(operaciones.buscarPrimerasEntre(any(), any(), any())).thenReturn(List.of(
                                new OperacionAdministrativa("op-1", MOMENTO, "uid-admin",
                                                TipoOperacion.SUSPENSION_PRODUCTO, ObjetoAuditado.PRODUCTO,
                                                "producto-1", "Espada solar", "Estado: Activo",
                                                "Estado: Suspendido", "Rompe el balance",
                                                "Suspensión del producto", "181.52.10.4")));
        }

        private static String bearer(String token) {
                return "Bearer " + token;
        }

        @Nested
        @DisplayName("quién puede leerlas")
        class Acceso {

                @Test
                @DisplayName("sin token: 401 con problem details")
                void sinToken() throws Exception {
                        mvc.perform(get(RUTA))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.type").value("urn:nexus:problema:no-autenticado"));
                        verifyNoInteractions(operaciones);
                }

                @ParameterizedTest(name = "un {0}: 403, ni el Super Administrador las lee aquí")
                @ValueSource(strings = {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
                void usuarios(String rol) throws Exception {
                        mvc.perform(get(RUTA)
                                                        .header(HttpHeaders.AUTHORIZATION,
                                                                        bearer(EMISOR.tokenDeUsuario("raiz",
                                                                                        UUID.randomUUID(), rol))))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"));
                        verifyNoInteractions(operaciones);
                }

                @Test
                @DisplayName("otro servicio con credencial (notificaciones): 403, no es la bitácora")
                void otroServicio() throws Exception {
                        mvc.perform(get(RUTA)
                                                        .header(HttpHeaders.AUTHORIZATION,
                                                                        bearer(EMISOR.tokenDeServicio("notificaciones"))))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"))
                                        .andExpect(jsonPath("$.detail").value(
                                                        "Las operaciones administrativas del catálogo solo las lee la bitácora central."));
                        verifyNoInteractions(operaciones);
                }

                @Test
                @DisplayName("la bitácora central (ms-cumplimiento): 200 con el lote completo de cada operación")
                void bitacoraCentral() throws Exception {
                        mvc.perform(get(RUTA)
                                                        .param("desde", "2026-10-14T00:00:00Z")
                                                        .header(HttpHeaders.AUTHORIZATION,
                                                                        bearer(EMISOR.tokenDeServicio("ms-cumplimiento"))))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.hasta").value("2026-10-14T15:30:00.250Z"))
                                        .andExpect(jsonPath("$.completo").value(true))
                                        .andExpect(jsonPath("$.operaciones[0].id").value("op-1"))
                                        .andExpect(jsonPath("$.operaciones[0].ocurridaEn")
                                                        .value("2026-10-14T15:30:00.250Z"))
                                        .andExpect(jsonPath("$.operaciones[0].autor").value("uid-admin"))
                                        .andExpect(jsonPath("$.operaciones[0].tipo").value("SUSPENSION_PRODUCTO"))
                                        .andExpect(jsonPath("$.operaciones[0].objeto").value("PRODUCTO"))
                                        .andExpect(jsonPath("$.operaciones[0].objetoId").value("producto-1"))
                                        .andExpect(jsonPath("$.operaciones[0].nombre").value("Espada solar"))
                                        .andExpect(jsonPath("$.operaciones[0].valorAnterior").value("Estado: Activo"))
                                        .andExpect(jsonPath("$.operaciones[0].valorNuevo").value("Estado: Suspendido"))
                                        .andExpect(jsonPath("$.operaciones[0].motivo").value("Rompe el balance"))
                                        .andExpect(jsonPath("$.operaciones[0].descripcion")
                                                        .value("Suspensión del producto"))
                                        .andExpect(jsonPath("$.operaciones[0].ipOrigen").value("181.52.10.4"));
                }

                @Test
                @DisplayName("el detalle por id sigue público: /auditoria a secas es un id más (404, no 401)")
                void detallePublico() throws Exception {
                        mvc.perform(get("/api/v1/productos/auditoria"))
                                        .andExpect(status().isNotFound());
                }
        }

        @Nested
        @DisplayName("lo que se le pide")
        class Parametros {

                @ParameterizedTest(name = "limite={0}: 400")
                @ValueSource(strings = {"0", "201"})
                void limiteFueraDeRango(String limite) throws Exception {
                        mvc.perform(get(RUTA)
                                                        .param("limite", limite)
                                                        .header(HttpHeaders.AUTHORIZATION,
                                                                        bearer(EMISOR.tokenDeServicio("ms-cumplimiento"))))
                                        .andExpect(status().isBadRequest());
                }

                @Test
                @DisplayName("desde que no es una fecha: 400")
                void desdeInvalido() throws Exception {
                        mvc.perform(get(RUTA)
                                                        .param("desde", "ayer")
                                                        .header(HttpHeaders.AUTHORIZATION,
                                                                        bearer(EMISOR.tokenDeServicio("ms-cumplimiento"))))
                                        .andExpect(status().isBadRequest());
                }
        }
}
