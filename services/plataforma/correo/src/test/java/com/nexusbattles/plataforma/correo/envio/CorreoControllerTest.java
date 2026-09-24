package com.nexusbattles.plataforma.correo.envio;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.correo.seguridad.SecurityConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica el contrato publicado en contracts/openapi/correo.yaml, que
 * ms-identidad ya está consumiendo. Romper esto rompe su integración.
 *
 * <p>Desde la 1.1.0 del contrato toda ruta exige una credencial de servicio
 * (ADR-005): las peticiones de estas pruebas la llevan, firmada de verdad por
 * el emisor de prueba y verificada contra su JWKS, y el bloque
 * {@code Seguridad} afirma lo que pasa sin ella.
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

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnviadorCorreoService enviador;

    /** Una peticion tal como la hace ms-identidad: con su credencial de servicio. */
    private static MockHttpServletRequestBuilder comoServicio(String ruta) {
        return post(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + EMISOR.tokenDeServicio("ms-identidad"));
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

            verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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

            verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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

            verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
        }

        @Test
        void laCredencialSeExigeAntesDeValidarElCuerpo() throws Exception {
            // Un cuerpo invalido sin credencial es 401, no 400: no se revela
            // nada del contrato a quien no es un servicio.
            mockMvc.perform(post(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aceptaUnCorreoDeBienvenidaYLoDespacha() throws Exception {
        mockMvc.perform(comoServicio(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "nombres":"Santiago","apellidos":"Anaya"}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(eq("jugador@ejemplo.com"), anyString(), eq("email/bienvenida"), any());
    }

    @Test
    void aceptaUnAvisoDeAccesoYLoDespacha() throws Exception {
        mockMvc.perform(comoServicio(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "ip":"190.85.12.44","fechaHora":"2026-08-30T14:23:11-05:00"}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(eq("jugador@ejemplo.com"), anyString(), eq("email/aviso-acceso"), any());
    }

    // HU-AUT-006 CA-01: aviso de cambio de contraseña, sobre la plantilla corporativa.

    @Test
    void aceptaUnAvisoDeCambioDeClaveYLoDespachaConLaFechaLegible() throws Exception {
        mockMvc.perform(comoServicio(CAMBIO_CLAVE).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "ip":"190.85.12.44","fechaHora":"2026-09-21T15:00:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> modelo =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(enviador).enviar(eq("jugador@ejemplo.com"), eq("Tu contraseña de The Nexus Battles VI cambió"),
                eq("email/cambio-clave"), modelo.capture());
        org.assertj.core.api.Assertions.assertThat(modelo.getValue())
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

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @ParameterizedTest(name = "aviso rechazado: {0}")
    @ValueSource(strings = {
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"fechaHora\":\"2026-08-30T14:23:11-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"ip\":\"190.85.12.44\"}",
    })
    void rechazaAvisoDeAccesoIncompleto(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> tipo = (Class<Map<String, Object>>) (Class<?>) Map.class;
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(tipo);
        verify(enviador).enviar(anyString(), anyString(), anyString(), captor.capture());
        assertThat(captor.getValue()).doesNotContainKey("html");
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

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> tipo = (Class<Map<String, Object>>) (Class<?>) Map.class;
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(tipo);
        verify(enviador).enviar(anyString(), anyString(), anyString(), captor.capture());

        assertThat(captor.getValue().get("fechaHora").toString())
                .as("debe conservar la hora y el huso originales, no pasarlos a UTC")
                .contains("14:23")
                .doesNotContain("19:23");
    }

    @Test
    void aceptaUnCorreoDeRecuperacionYLoDespacha() throws Exception {
        mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(
                eq("jugador@ejemplo.com"), anyString(), eq("email/recuperacion-clave"), any());
    }

    @Test
    void elCorreoDeRecuperacionLlevaElCodigoYSuVigencia() throws Exception {
        mockMvc.perform(comoServicio(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> tipo = (Class<Map<String, Object>>) (Class<?>) Map.class;
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(tipo);
        verify(enviador).enviar(anyString(), anyString(), anyString(), captor.capture());

        assertThat(captor.getValue())
                .containsEntry("codigo", "482915")
                .containsEntry("minutosVigencia", 15);
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

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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

        org.mockito.ArgumentCaptor<String> asunto = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(enviador).enviar(anyString(), asunto.capture(), anyString(), any());

        assertThat(asunto.getValue()).isEqualTo("Recupera tu contraseña de The Nexus Battles VI");
    }

    // ----- HU-COR-002: confirmacion de cuenta -----

    @Test
    void aceptaUnCorreoDeConfirmacionDeCuentaYLoDespacha() throws Exception {
        // CA-01: el registro deja la cuenta pendiente y despacha el correo con
        // el codigo. Lo que se prueba aqui es la mitad de este servicio: que
        // el correo sale por la plantilla de confirmacion.
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"734201","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(
                eq("nuevo@ejemplo.com"), anyString(), eq("email/confirmacion-cuenta"), any());
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

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> tipo = (Class<Map<String, Object>>) (Class<?>) Map.class;
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(tipo);
        verify(enviador).enviar(anyString(), anyString(), anyString(), captor.capture());

        assertThat(captor.getValue())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("codigo", "734201")
                .containsEntry("minutosVigencia", 10);
    }

    @Test
    void elAsuntoDeConfirmacionEstaBienEscritoEnEspanol() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nuevo@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"734201","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        org.mockito.ArgumentCaptor<String> asunto = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(enviador).enviar(anyString(), asunto.capture(), anyString(), any());

        assertThat(asunto.getValue()).isEqualTo("Confirma tu cuenta de The Nexus Battles VI");
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
    })
    void rechazaConfirmacionConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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
    void aceptaUnCorreoDeMisionYLoDespachaCuandoDebeEnviarse() throws Exception {
        mockMvc.perform(comoServicio(MISION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Nueva misión disponible","mensaje":"Derrota al dragón",
                 "debeEnviarCorreo":true}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(eq("jugador@ejemplo.com"), eq("Nueva misión disponible"), eq("email/mision"), any());
    }

    @Test
    void noEnviaCorreoDeMisionSiDebeEnviarCorreoEsFalse() throws Exception {
        // CA-02/CA-03: avisos solo dentro de la app, o categoria apagada.
        // No es un error -- se responde 202 igual, pero sin despachar nada.
        mockMvc.perform(comoServicio(MISION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Nueva misión disponible","mensaje":"Derrota al dragón",
                 "debeEnviarCorreo":false}
                """))
                .andExpect(status().isAccepted());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
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

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> tipo = (Class<Map<String, Object>>) (Class<?>) Map.class;
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(tipo);
        verify(enviador).enviar(anyString(), anyString(), anyString(), captor.capture());

        assertThat(captor.getValue())
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

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aceptaUnCorreoDeSubastaYLoDespachaCuandoDebeEnviarse() throws Exception {
        mockMvc.perform(comoServicio(SUBASTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Ganaste la subasta","mensaje":"Espada Legendaria",
                 "debeEnviarCorreo":true}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(eq("jugador@ejemplo.com"), eq("Ganaste la subasta"), eq("email/subasta"), any());
    }

    @Test
    void noEnviaCorreoDeSubastaSiDebeEnviarCorreoEsFalse() throws Exception {
        mockMvc.perform(comoServicio(SUBASTA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "asunto":"Ganaste la subasta","mensaje":"Espada Legendaria",
                 "debeEnviarCorreo":false}
                """))
                .andExpect(status().isAccepted());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @ParameterizedTest(name = "subasta rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"mensaje\":\"m\",\"debeEnviarCorreo\":true}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"asunto\":\"a\",\"mensaje\":\"m\"}",
    })
    void rechazaSubastaConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(SUBASTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    // ----- HU-PAG-003: confirmacion de compra (issue #537, consumidor: ms-finanzas) -----

    @Test
    void aceptaUnaConfirmacionDeCompraYLaDespacha() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":50000.00,"moneda":"COP","concepto":"Paquete de créditos x500",
                 "fechaHora":"2026-09-23T10:15:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(
                eq("jugador@ejemplo.com"), anyString(), eq("email/confirmacion-compra"), any());
    }

    @Test
    void elCorreoDeConfirmacionDeCompraLlevaElMontoLaMonedaYElConcepto() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":50000.00,"moneda":"COP","concepto":"Paquete de créditos x500",
                 "fechaHora":"2026-09-23T10:15:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> tipo = (Class<Map<String, Object>>) (Class<?>) Map.class;
        org.mockito.ArgumentCaptor<Map<String, Object>> captor = org.mockito.ArgumentCaptor.forClass(tipo);
        verify(enviador).enviar(anyString(), anyString(), anyString(), captor.capture());

        assertThat(captor.getValue())
                .containsEntry("apodo", "ElGuerrero")
                .containsEntry("monto", "50000.00 COP")
                .containsEntry("concepto", "Paquete de créditos x500")
                .containsEntry("fechaHora", "23/09/2026 a las 10:15 (GMT-05:00)");
    }

    @Test
    void elAsuntoDeConfirmacionDeCompraEstaBienEscritoEnEspanol() throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "monto":50000.00,"moneda":"COP","concepto":"Paquete de créditos x500",
                 "fechaHora":"2026-09-23T10:15:00-05:00"}
                """))
                .andExpect(status().isAccepted());

        org.mockito.ArgumentCaptor<String> asunto = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(enviador).enviar(anyString(), asunto.capture(), anyString(), any());

        assertThat(asunto.getValue()).isEqualTo("Confirmación de tu compra en The Nexus Battles VI");
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
    })
    void rechazaConfirmacionDeCompraConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(comoServicio(CONFIRMACION_COMPRA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }
}
