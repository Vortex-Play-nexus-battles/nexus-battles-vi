package nexus.alertas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * HU-NOT-001 (#532), productos.yaml 1.6.0 — {@code GET /api/v1/productos/alertas/cambios}
 * con tokens del emisor REAL (los firma {@link EmisorDeTokensDePrueba}, la
 * forma de ms-identidad) y la cadena de seguridad completa.
 *
 * <p>La operacion es solo de servicios: la llama notificaciones con su
 * credencial (ADR-005). Un usuario —tambien un ADMINISTRADOR— recibe 403, y
 * sin token 401. La trampa que esta clase fija: en {@code SeguridadConfig}
 * {@code GET /api/v1/productos/{id}} es publica, asi que la regla de la ruta
 * nueva va antes que ella y el detalle del catalogo sigue siendo publico.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Productos · cambios del catalogo para otro servicio (1.6.0)")
class CambiosDelCatalogoApiTest {

    private static final String RUTA = "/api/v1/productos/alertas/cambios";
    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final Instant CAMBIO = Instant.parse("2026-09-27T15:30:00Z");

    @DynamicPropertySource
    static void apuntarAlEmisorReal(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("alertasCatalogoSimuladas")
    private AlertaCatalogoRepository alertas;

    @Autowired
    @Qualifier("consultasAlertasSimuladas")
    private ConsultaAlertasJugadorRepository consultas;

    /** El detalle por id no es el sujeto: solo se comprueba que sigue publico. */
    @MockitoBean
    private ProductoRepository productos;

    @BeforeEach
    void preparar() {
        reset(alertas, consultas);
        when(alertas.buscarPrimerasImplementadasEntre(any(), any(), any()))
                .thenReturn(List.of(new AlertaCatalogo(
                        "alerta-1",
                        "producto-1",
                        "Espada solar",
                        TipoCambioCatalogo.CAMBIO_BALANCE,
                        "Se actualizo el balance de Espada solar.",
                        CAMBIO)));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Nested
    @DisplayName("quien puede leerla")
    class Acceso {

        @Test
        @DisplayName("sin token: 401 con problem details")
        void sinToken() throws Exception {
            mvc.perform(get(RUTA).param("desde", "2026-09-20T00:00:00Z"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:no-autenticado"));
            verifyNoInteractions(alertas, consultas);
        }

        @Test
        @DisplayName("un jugador: 403")
        void jugador() throws Exception {
            mvc.perform(get(RUTA)
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"));
            verifyNoInteractions(alertas, consultas);
        }

        @ParameterizedTest(name = "un {0}: 403, administrar el catalogo no es ser un servicio")
        @ValueSource(strings = {"ADMINISTRADOR", "SUPER_ADMINISTRADOR", "MODERADOR"})
        void administracion(String rol) throws Exception {
            mvc.perform(get(RUTA)
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(EMISOR.tokenDeUsuario("raiz", UUID.randomUUID(), rol))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:acceso-denegado"));
            verifyNoInteractions(alertas, consultas);
        }

        @Test
        @DisplayName("un servicio (notificaciones): 200 con el lote, sin tocar el cursor de inicio-sesion")
        void servicio() throws Exception {
            mvc.perform(get(RUTA)
                            .param("desde", "2026-09-20T00:00:00Z")
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(EMISOR.tokenDeServicio("notificaciones"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasta").value("2026-09-27T15:30:00Z"))
                    .andExpect(jsonPath("$.completo").value(true))
                    .andExpect(jsonPath("$.alertas[0].id").value("alerta-1"))
                    .andExpect(jsonPath("$.alertas[0].tipo").value("CAMBIO_BALANCE"))
                    .andExpect(jsonPath("$.alertas[0].descripcion")
                            .value("Se actualizo el balance de Espada solar."))
                    .andExpect(jsonPath("$.alertas[0].implementadaEn").value("2026-09-27T15:30:00Z"));
            verifyNoInteractions(consultas);
        }

        @Test
        @DisplayName("un servicio sin desde recibe la linea base: ninguna alerta y completo")
        void lineaBase() throws Exception {
            mvc.perform(get(RUTA)
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(EMISOR.tokenDeServicio("notificaciones"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasta").isNotEmpty())
                    .andExpect(jsonPath("$.completo").value(true))
                    .andExpect(jsonPath("$.alertas.length()").value(0));
            verifyNoInteractions(alertas, consultas);
        }
    }

    @Nested
    @DisplayName("parametros invalidos: 400 con problem details, nunca 500")
    class Parametros {

        @ParameterizedTest(name = "limite={0}")
        @ValueSource(strings = {"0", "201", "-1", "muchos"})
        void limiteInvalido(String limite) throws Exception {
            mvc.perform(get(RUTA)
                            .param("limite", limite)
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(EMISOR.tokenDeServicio("notificaciones"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:solicitud-invalida"));
            verifyNoInteractions(alertas, consultas);
        }

        @Test
        @DisplayName("desde que no es una fecha y hora: 400")
        void desdeInvalido() throws Exception {
            mvc.perform(get(RUTA)
                            .param("desde", "ayer")
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(EMISOR.tokenDeServicio("notificaciones"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("urn:nexus:problema:solicitud-invalida"));
            verifyNoInteractions(alertas, consultas);
        }
    }

    @Nested
    @DisplayName("lo que ya funcionaba sigue igual")
    class SinRegresion {

        @Test
        @DisplayName("GET /api/v1/productos/{id} sigue siendo publico: sin token responde 404, no 401")
        void detalleSiguePublico() throws Exception {
            when(productos.findById("p-1")).thenReturn(Optional.empty());

            mvc.perform(get("/api/v1/productos/{id}", "p-1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("inicio-sesion sigue pidiendo token de usuario y su cursor sigue siendo suyo")
        void inicioSesionIgual() throws Exception {
            mvc.perform(get("/api/v1/productos/alertas/inicio-sesion"))
                    .andExpect(status().isUnauthorized());

            UUID uid = UUID.randomUUID();
            when(consultas.findById(uid.toString())).thenReturn(Optional.empty());
            mvc.perform(get("/api/v1/productos/alertas/inicio-sesion")
                            .header(HttpHeaders.AUTHORIZATION, bearer(EMISOR.tokenDeJugador("lyra", uid))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
