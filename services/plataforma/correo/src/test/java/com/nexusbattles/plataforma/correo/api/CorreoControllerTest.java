package com.nexusbattles.plataforma.correo.api;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.correo.cola.ColaDeCorreos;
import com.nexusbattles.plataforma.correo.cola.CorreoPedido;
import com.nexusbattles.plataforma.correo.seguridad.SecurityConfig;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el contrato publicado en contracts/openapi/correo.yaml, que
 * ms-identidad ya está consumiendo. Romper esto rompe su integración.
 *
 * <p>Desde la 1.1.0 del contrato toda ruta exige una credencial de servicio
 * (ADR-005): las peticiones de estas pruebas la llevan, firmada de verdad por
 * el emisor de prueba y verificada contra su JWKS, y el bloque
 * {@code Seguridad} afirma lo que pasa sin ella.
 *
 * <p>Desde la 1.4.0 aceptar es encolar: lo que se comprueba es el correo que
 * llega a la cola ({@link CorreoPedido}), no un envío.
 */
@WebMvcTest(controllers = CorreoController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class})
class CorreoControllerTest {

    private static final String BIENVENIDA = "/api/v1/correos/bienvenida";
    private static final String AVISO_ACCESO = "/api/v1/correos/aviso-acceso";
    private static final String CAMBIO_CLAVE = "/api/v1/correos/cambio-clave";
    private static final String RECUPERACION = "/api/v1/correos/recuperacion-clave";
    private static final String CONFIRMACION = "/api/v1/correos/confirmacion-cuenta";
    private static final String MISION = "/api/v1/correos/mision";
    private static final String SUBASTA = "/api/v1/correos/subasta";
    private static final String CONFIRMACION_COMPRA = "/api/v1/correos/confirmacion-compra";
    private static final String SANCION = "/api/v1/correos/sancion";

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ColaDeCorreos cola;

    /** Una peticion tal como la hace ms-identidad: con su credencial de servicio. */
    private static MockHttpServletRequestBuilder comoServicio(String ruta) {
        return post(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("ms-identidad"));
    }

    /** El correo que llego a la cola en esta prueba. */
    private CorreoPedido encolado() {
        ArgumentCaptor<CorreoPedido> pedido = ArgumentCaptor.forClass(CorreoPedido.class);
        verify(cola).encolar(pedido.capture(), any(), any());
        return pedido.getValue();
    }

    private void nadaEncolado() {
        verify(cola, never()).encolar(any(), any(), any());
    }

    @Nested
    class Seguridad {

        private static final String CUERPO = """
                {"email":"victima@ejemplo.com","apodo":"Victima",
                 "codigo":"482915","minutosVigencia":15}
                """;

        @Test
        void sinCredencialNadieMandaUnCodigoDeRecuperacionANadie() throws Exception {
            mockMvc.perform(post(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized());

            nadaEncolado();
        }

        @Test
        void unTokenDeUsuarioTampocoVale_correoEsEntreServicios() throws Exception {
            String deJugador = EMISOR.tokenDeJugador("ElGuerrero", UUID.randomUUID());
            String deAdministrador = EMISOR.tokenDeUsuario("Admin", UUID.randomUUID(), "SUPER_ADMINISTRADOR");

            mockMvc.perform(post(RECUPERACION).header(HttpHeaders.AUTHORIZATION, "Bearer " + deJugador)
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(CONFIRMACION).header(HttpHeaders.AUTHORIZATION, "Bearer " + deAdministrador)
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());

            nadaEncolado();
        }

        @Test
        void unModeradorNoPuedeMandarAvisosDeSancionPorSuCuenta() throws Exception {
            // El aviso lo manda moderacion-sanciones con su credencial de
            // servicio, nunca una persona desde el navegador.
            String deModerador = EMISOR.tokenDeUsuario("Mod", UUID.randomUUID(), "MODERADOR");

            mockMvc.perform(post(SANCION).header(HttpHeaders.AUTHORIZATION, "Bearer " + deModerador)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                                     "tipo":"BANEO","motivo":"Suplantación de identidad"}
                                    """))
                    .andExpect(status().isForbidden());

            nadaEncolado();
        }

        @Test
        void unTokenCaducadoOFirmadoPorOtroEs401() throws Exception {
            UUID uid = UUID.randomUUID();
            mockMvc.perform(post(RECUPERACION)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenCaducado("x", uid))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(RECUPERACION)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenFirmadoPorOtro("x", uid))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isUnauthorized());

            nadaEncolado();
        }

        @Test
        void laCredencialSeExigeAntesDeValidarElCuerpo() throws Exception {
            // Un cuerpo invalido sin credencial es 401, no 400: no se revela
            // nada del contrato a quien no es un servicio.
            mockMvc.perform(post(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ----- 1.4.0: aceptar = guardar en la cola -----

    @Nested
    class ColaPersistente {

        private static final String CUERPO = """
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero"}
                """;

        @Test
        void laIdempotencyKeyYElXTraceIdLleganALaCola() throws Exception {
            mockMvc.perform(comoServicio(BIENVENIDA)
                            .header("Idempotency-Key", "registro-7f3a")
                            .header("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736")
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isAccepted());

            verify(cola).encolar(any(), eq("registro-7f3a"), eq("4bf92f3577b34da6a3ce929d0e0e4736"));
        }

        @Test
        void sinCabecerasLaColaRecibeNulos() throws Exception {
            mockMvc.perform(comoServicio(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isAccepted());

            verify(cola).encolar(any(), isNull(), isNull());
        }

        @Test
        void unaIdempotencyKeyDe120CaracteresSeAcepta() throws Exception {
            mockMvc.perform(comoServicio(BIENVENIDA)
                            .header("Idempotency-Key", "k".repeat(120))
                            .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isAccepted());
        }

        @Test
        void unaIdempotencyKeyDeMasDe120CaracteresEs400YNoSeEncola() throws Exception {
            // Contrato 1.4.0: maxLength 120.
            mockMvc.perform(comoServicio(SANCION)
                            .header("Idempotency-Key", "k".repeat(121))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                                     "tipo":"ADVERTENCIA","motivo":"Lenguaje ofensivo en el chat"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            nadaEncolado();
        }

        @Test
        void siLaColaNoEstaDisponibleResponde503EnProblemDetailsYNunca202() throws Exception {
            when(cola.encolar(any(), any(), any()))
                    .thenThrow(new CannotGetJdbcConnectionException("Connection is not available"));

            mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                            {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                             "codigo":"482915","minutosVigencia":15}
                            """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.type")
                            .value("https://nexusbattles.local/errores/cola-de-correo-no-disponible"))
                    .andExpect(jsonPath("$.title").value("La cola de correo no está disponible"));
        }
    }

    @Test
    void aceptaUnCorreoDeBienvenidaYLoEncola() throws Exception {
        mockMvc.perform(comoServicio(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "nombres":"Santiago","apellidos":"Anaya"}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.BIENVENIDA);
        assertThat(pedido.destinatario()).isEqualTo("jugador@ejemplo.com");
        assertThat(pedido.asunto()).isEqualTo("Bienvenido a The Nexus Battles VI");
        assertThat(pedido.datos()).containsEntry("saludo", "Santiago");
        assertThat(pedido.debeEnviarse()).isTrue();
    }

    @Test
    void aceptaUnAvisoDeAccesoYLoEncola() throws Exception {
        mockMvc.perform(comoServicio(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "ip":"190.85.12.44","fechaHora":"2026-08-30T14:23:11-05:00"}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.AVISO_ACCESO);
        assertThat(pedido.destinatario()).isEqualTo("jugador@ejemplo.com");
        assertThat(pedido.asunto()).isEqualTo("Acceso desde un dispositivo no reconocido");
        assertThat(pedido.datos()).containsEntry("apodo", "ElGuerrero").containsEntry("ip", "190.85.12.44");
    }

    // HU-AUT-006 CA-01: aviso de cambio de contraseña, sobre la plantilla corporativa.

    @Test
    void aceptaUnAvisoDeCambioDeClaveYLoEncolaConLaFechaLegible() throws Exception {
        mockMvc.perform(comoServicio(CAMBIO_CLAVE).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "ip":"190.85.12.44","fechaHora":"2026-09-21T15:00:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.CAMBIO_CLAVE);
        assertThat(pedido.asunto()).isEqualTo("Tu contraseña de The Nexus Battles VI cambió");
        assertThat(pedido.datos())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("ip", "190.85.12.44")
                .containsEntry("fechaHora", "21/09/2026 a las 15:00 (GMT-05:00)")
                .doesNotContainKeys("password", "nuevaPassword");
    }

    @ParameterizedTest(name = "cambio de clave rechazado: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"ip\":\"1.1.1.1\",\"fechaHora\":\"2026-09-21T15:00:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"ip\":\"1.1.1.1\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"fechaHora\":\"2026-09-21T15:00:00-05:00\"}",
    })
    void rechazaCambioDeClaveConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(CAMBIO_CLAVE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    @ParameterizedTest(name = "bienvenida rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\"}",
            "{\"email\":\"no-es-un-correo\",\"apodo\":\"ElGuerrero\"}",
            "{\"email\":\"jugador@ejemplo.com\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"  \"}",
    })
    void rechazaBienvenidaConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    @ParameterizedTest(name = "aviso rechazado: {0}")
    @ValueSource(strings = {
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"fechaHora\":\"2026-08-30T14:23:11-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"ip\":\"190.85.12.44\"}",
    })
    void rechazaAvisoDeAccesoIncompleto(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    @Test
    void elErrorDeValidacionSaleEnFormatoProblemDetails() throws Exception {
        String cuerpo = mockMvc.perform(comoServicio(BIENVENIDA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apodo\":\"ElGuerrero\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Regla 4 de plataforma: formato de error idéntico en los 20 módulos.
        assertThat(cuerpo).contains("\"status\":400").contains("\"title\"");
    }

    @Test
    void elJugadorNuncaEnviaHtml() throws Exception {
        // HU-COR-001: el 100% de los correos se arma sobre la plantilla
        // corporativa. Si el llamante pudiera mandar HTML, se rompería.
        mockMvc.perform(comoServicio(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "html":"<h1>contenido propio</h1>"}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos()).doesNotContainKey("html");
    }

    @Test
    void elAvisoConservaLaHoraLocalDelAcceso() throws Exception {
        // Un aviso de seguridad que muestra la hora en otro huso confunde al
        // usuario: pensaría que el acceso no fue suyo. Se envía 14:23 -05:00,
        // así que debe mostrarse 14:23 y no convertido a UTC.
        mockMvc.perform(comoServicio(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "ip":"190.85.12.44","fechaHora":"2026-08-30T14:23:11-05:00"}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos().get("fechaHora").toString())
                .as("debe conservar la hora y el huso originales, no pasarlos a UTC")
                .contains("14:23")
                .doesNotContain("19:23");
    }

    @Test
    void aceptaUnCorreoDeRecuperacionYLoEncola() throws Exception {
        mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.RECUPERACION_CLAVE);
        assertThat(pedido.destinatario()).isEqualTo("jugador@ejemplo.com");
    }

    @Test
    void elCorreoDeRecuperacionLlevaElCodigoYSuVigencia() throws Exception {
        mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos())
                .containsEntry("codigo", "482915")
                .containsEntry("minutosVigencia", 15)
                .as("el enlace se arma al entregar, no se guarda con el codigo dentro")
                .doesNotContainKey("enlace");
    }

    @ParameterizedTest(name = "recuperacion rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"codigo\":\"482915\",\"minutosVigencia\":15}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"minutosVigencia\":15}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"482915\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"  \",\"minutosVigencia\":15}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"482915\",\"minutosVigencia\":0}",
    })
    void rechazaRecuperacionConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    @Test
    void losAsuntosEstanBienEscritosEnEspanol() throws Exception {
        // Un typo en el asunto lo ve cada destinatario y no lo atrapa ninguna
        // prueba de estructura: solo se nota leyendolo.
        mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().asunto()).isEqualTo("Recupera tu contraseña de The Nexus Battles VI");
    }

    // ----- HU-COR-002: confirmacion de cuenta -----

    @Test
    void aceptaUnCorreoDeConfirmacionDeCuentaYLoEncola() throws Exception {
        // CA-01: el registro deja la cuenta pendiente y despacha el correo con
        // el codigo. Lo que se prueba aqui es la mitad de este servicio: que
        // el correo va a la cola con la plantilla de confirmacion.
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"734201","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.CONFIRMACION_CUENTA);
        assertThat(pedido.destinatario()).isEqualTo("nuevo@ejemplo.com");
    }

    @Test
    void elCorreoDeConfirmacionLlevaElCodigoYSuVigenciaTalCualLlegan() throws Exception {
        // El servicio de correo no genera ni recorta el codigo ni decide la
        // vigencia: transcribe lo que ms-identidad emitio.
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"734201","minutosVigencia":10}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("codigo", "734201")
                .containsEntry("minutosVigencia", 10);
    }

    @Test
    void sinPropositoLaConfirmacionEsUnaActivacion() throws Exception {
        // Lo que ms-identidad ya enviaba antes de la 1.4.0: cuentas creadas
        // por un Super Administrador.
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"734201","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.datos()).containsEntry("proposito", "ACTIVACION");
        assertThat(pedido.asunto()).isEqualTo("Activa tu cuenta de The Nexus Battles VI");
    }

    @Test
    void conPropositoVerificacionEsElCorreoDeUnJugadorQueSeAcabaDeRegistrar() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"734201","minutosVigencia":15,"proposito":"VERIFICACION"}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.datos()).containsEntry("proposito", "VERIFICACION");
        assertThat(pedido.asunto()).isEqualTo("Confirma tu cuenta de The Nexus Battles VI");
    }

    @ParameterizedTest(name = "confirmacion rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"codigo\":\"734201\",\"minutosVigencia\":15}",
            "{\"email\":\"no-es-un-correo\",\"apodo\":\"ElGuerrero\",\"codigo\":\"734201\",\"minutosVigencia\":15}",
            "{\"email\":\"nuevo@ejemplo.com\",\"codigo\":\"734201\",\"minutosVigencia\":15}",
            "{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"minutosVigencia\":15}",
            "{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"  \",\"minutosVigencia\":15}",
            "{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"734201\"}",
            "{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"734201\",\"minutosVigencia\":0}",
            "{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"1234567890123\",\"minutosVigencia\":15}",
            "{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"codigo\":\"734201\",\"minutosVigencia\":15,\"proposito\":\"OTRO\"}",
    })
    void rechazaConfirmacionConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    @Test
    void elRechazoDeConfirmacionSaleEnFormatoProblemDetails() throws Exception {
        String cuerpo = mockMvc.perform(comoServicio(CONFIRMACION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo@ejemplo.com\",\"apodo\":\"ElGuerrero\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("\"status\":400").contains("\"title\"");
    }

    // ----- HU-COR-005: correos de misiones y subastas -----

    @Test
    void aceptaUnCorreoDeMisionYLoEncolaCuandoDebeEnviarse() throws Exception {
        mockMvc.perform(comoServicio(MISION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Nueva misión disponible","mensaje":"Derrota al dragón",
                 "debeEnviarCorreo":true}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.MISION);
        assertThat(pedido.asunto()).isEqualTo("Nueva misión disponible");
        assertThat(pedido.debeEnviarse()).isTrue();
    }

    @Test
    void conDebeEnviarCorreoFalseLaMisionQuedaOmitidaYSinContenido() throws Exception {
        // CA-02/CA-03: avisos solo dentro de la app, o categoria apagada. No
        // es un error -- 202 igual--, y el correo queda como constancia en la
        // cola (OMITIDO), sin el mensaje: no se va a enviar.
        mockMvc.perform(comoServicio(MISION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Nueva misión disponible","mensaje":"Derrota al dragón",
                 "debeEnviarCorreo":false}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.debeEnviarse()).isFalse();
        assertThat(pedido.motivoDeOmision()).contains("debeEnviarCorreo=false");
        assertThat(pedido.datos()).isEmpty();
    }

    @Test
    void elCorreoDeMisionLlevaElAsuntoYMensajeTalCualLlegan() throws Exception {
        // El servicio no decide el contenido: lo transcribe tal cual lo
        // manda el modulo de misiones.
        mockMvc.perform(comoServicio(MISION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Nueva misión disponible","mensaje":"Derrota al dragón",
                 "debeEnviarCorreo":true}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("asunto", "Nueva misión disponible")
                .containsEntry("mensaje", "Derrota al dragón");
    }

    @ParameterizedTest(name = "mision rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"mensaje\":\"m\",\"debeEnviarCorreo\":true}",
            "{\"email\":\"jugador@ejemplo.com\",\"asunto\":\"a\",\"mensaje\":\"m\",\"debeEnviarCorreo\":true}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"mensaje\":\"m\",\"debeEnviarCorreo\":true}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"debeEnviarCorreo\":true}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"mensaje\":\"m\"}",
    })
    void rechazaMisionConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(MISION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    @Test
    void aceptaUnCorreoDeSubastaYLoEncolaCuandoDebeEnviarse() throws Exception {
        mockMvc.perform(comoServicio(SUBASTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Ganaste la subasta","mensaje":"Espada Legendaria",
                 "debeEnviarCorreo":true}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.SUBASTA);
        assertThat(pedido.asunto()).isEqualTo("Ganaste la subasta");
        assertThat(pedido.datos()).containsEntry("mensaje", "Espada Legendaria");
    }

    @Test
    void conDebeEnviarCorreoFalseLaSubastaQuedaOmitida() throws Exception {
        mockMvc.perform(comoServicio(SUBASTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Ganaste la subasta","mensaje":"Espada Legendaria",
                 "debeEnviarCorreo":false}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.SUBASTA);
        assertThat(pedido.debeEnviarse()).isFalse();
        assertThat(pedido.datos()).isEmpty();
    }

    @ParameterizedTest(name = "subasta rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"mensaje\":\"m\",\"debeEnviarCorreo\":true}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"mensaje\":\"m\"}",
    })
    void rechazaSubastaConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(SUBASTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    // ----- HU-PAG-003: confirmacion de compra (issue #537, consumidor: ms-finanzas) -----

    @Test
    void aceptaUnaConfirmacionDeCompraYLaEncola() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":50000.00,"moneda":"COP","concepto":"Paquete de créditos x500",
                 "fechaHora":"2026-09-23T10:15:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.CONFIRMACION_COMPRA);
        assertThat(pedido.destinatario()).isEqualTo("jugador@ejemplo.com");
        assertThat(pedido.datos()).doesNotContainKeys("lineas", "orden");
    }

    @Test
    void elCorreoDeConfirmacionDeCompraLlevaElMontoLaMonedaYElConcepto() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":50000.00,"moneda":"COP","concepto":"Paquete de créditos x500",
                 "fechaHora":"2026-09-23T10:15:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("monto", "50000.00 COP")
                .containsEntry("concepto", "Paquete de créditos x500")
                .containsEntry("fechaHora", "23/09/2026 a las 10:15 (GMT-05:00)");
    }

    @Test
    void laConfirmacionDeCompraLlevaElDetalleDeLosProductosYLaOrden() throws Exception {
        // 7.5: «detalle de los productos adquiridos y el total pagado».
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":250.00,"moneda":"COP","concepto":"Compra en la tienda",
                 "fechaHora":"2026-09-23T10:15:00-05:00","orden":"ORD-2026-0042",
                 "lineas":[
                   {"nombre":"Espada Legendaria","cantidad":2,"precioUnitario":100.00,"subtotal":200.00},
                   {"nombre":"Poción","cantidad":1,"subtotal":50.00}]}
                """))
                .andExpect(status().isAccepted());

        Map<String, Object> datos = encolado().datos();
        assertThat(datos).containsEntry("orden", "ORD-2026-0042").containsEntry("monto", "250.00 COP");
        assertThat(datos.get("lineas")).isEqualTo(List.of(
                Map.of("nombre", "Espada Legendaria", "cantidad", 2,
                        "precioUnitario", "100.00 COP", "subtotal", "200.00 COP"),
                Map.of("nombre", "Poción", "cantidad", 1, "subtotal", "50.00 COP")));
    }

    @Test
    void elAsuntoDeConfirmacionDeCompraEstaBienEscritoEnEspanol() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":50000.00,"moneda":"COP","concepto":"Paquete de créditos x500",
                 "fechaHora":"2026-09-23T10:15:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().asunto()).isEqualTo("Confirmación de tu compra en The Nexus Battles VI");
    }

    @ParameterizedTest(name = "confirmacion de compra rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"no-es-un-correo\",\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"monto\":50000.00,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":0,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":-1,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"moneda\":\"cop\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"moneda\":\"PESOS\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"moneda\":\"COP\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":50000.00,\"moneda\":\"COP\",\"concepto\":\"x\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":1,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\",\"lineas\":[{\"cantidad\":1,\"subtotal\":1}]}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":1,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\",\"lineas\":[{\"nombre\":\"a\",\"subtotal\":1}]}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"monto\":1,\"moneda\":\"COP\",\"concepto\":\"x\",\"fechaHora\":\"2026-09-23T10:15:00-05:00\",\"lineas\":[{\"nombre\":\"a\",\"cantidad\":1}]}",
    })
    void rechazaConfirmacionDeCompraConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }

    // ----- 1.4.1: aviso de sancion (7.3.2) -----

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(delimiter = '|', value = {
            "ADVERTENCIA        | Recibiste una advertencia en The Nexus Battles VI",
            "SUSPENSION         | Tu cuenta de The Nexus Battles VI está suspendida",
            "BANEO              | Tu cuenta de The Nexus Battles VI fue baneada de forma definitiva",
            "APELACION_RESUELTA | Resolución de tu apelación en The Nexus Battles VI",
    })
    void cadaTipoDeSancionSaleConSuAsunto(String tipo, String asunto) throws Exception {
        mockMvc.perform(comoServicio(SANCION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "tipo":"%s","motivo":"Uso de lenguaje ofensivo en el chat"}
                """.formatted(tipo)))
                .andExpect(status().isAccepted());

        CorreoPedido pedido = encolado();
        assertThat(pedido.plantilla()).isEqualTo(Plantilla.SANCION);
        assertThat(pedido.asunto()).isEqualTo(asunto);
        assertThat(pedido.datos()).containsEntry("tipo", tipo);
    }

    @Test
    void laSuspensionLlevaMotivoFinYPlazoDeApelacionConSuHusoOriginal() throws Exception {
        // 7.3.7: motivo, fecha de vigencia y proceso de apelacion.
        mockMvc.perform(comoServicio(SANCION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero","tipo":"SUSPENSION",
                 "motivo":"Acoso a otros jugadores",
                 "hasta":"2026-10-01T18:00:00-05:00","apelableHasta":"2026-10-25T23:59:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("motivo", "Acoso a otros jugadores")
                .containsEntry("hasta", "01/10/2026 a las 18:00 (GMT-05:00)")
                .containsEntry("apelableHasta", "25/10/2026 a las 23:59 (GMT-05:00)")
                .doesNotContainKey("resultadoApelacion");
    }

    @Test
    void laResolucionDeUnaApelacionLlevaSuResultado() throws Exception {
        mockMvc.perform(comoServicio(SANCION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero","tipo":"APELACION_RESUELTA",
                 "motivo":"Acoso a otros jugadores","resultadoApelacion":"REVERTIDA",
                 "hasta":null,"apelableHasta":null}
                """))
                .andExpect(status().isAccepted());

        assertThat(encolado().datos())
                .containsEntry("resultadoApelacion", "REVERTIDA")
                .doesNotContainKeys("hasta", "apelableHasta");
    }

    @ParameterizedTest(name = "sancion rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"tipo\":\"BANEO\",\"motivo\":\"x\"}",
            "{\"email\":\"no-es-un-correo\",\"apodo\":\"ElGuerrero\",\"tipo\":\"BANEO\",\"motivo\":\"x\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"tipo\":\"BANEO\",\"motivo\":\"x\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"motivo\":\"x\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"tipo\":\"BANEO\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"tipo\":\"BANEO\",\"motivo\":\"  \"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"tipo\":\"EXPULSION\",\"motivo\":\"x\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"tipo\":\"APELACION_RESUELTA\",\"motivo\":\"x\",\"resultadoApelacion\":\"ANULADA\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"tipo\":\"SUSPENSION\",\"motivo\":\"x\",\"hasta\":\"mañana\"}",
    })
    void rechazaSancionConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(SANCION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        nadaEncolado();
    }
}
