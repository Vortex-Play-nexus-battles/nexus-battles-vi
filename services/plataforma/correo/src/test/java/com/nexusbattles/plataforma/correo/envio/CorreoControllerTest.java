package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

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
 */
@WebMvcTest(controllers = CorreoController.class)
class CorreoControllerTest {

    private static final String BIENVENIDA = "/api/v1/correos/bienvenida";
    private static final String AVISO_ACCESO = "/api/v1/correos/aviso-acceso";
    private static final String RECUPERACION = "/api/v1/correos/recuperacion-clave";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnviadorCorreoService enviador;

    @Test
    void aceptaUnCorreoDeBienvenidaYLoDespacha() throws Exception {
        mockMvc.perform(post(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "nombres":"Santiago","apellidos":"Anaya"}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(eq("jugador@ejemplo.com"), anyString(), eq("email/bienvenida"), any());
    }

    @Test
    void aceptaUnAvisoDeAccesoYLoDespacha() throws Exception {
        mockMvc.perform(post(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "ip":"190.85.12.44","fechaHora":"2026-08-30T14:23:11-05:00"}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(eq("jugador@ejemplo.com"), anyString(), eq("email/aviso-acceso"), any());
    }

    @ParameterizedTest(name = "bienvenida rechazada: {0}")
    @ValueSource(strings = {
            "{\"apodo\":\"ElGuerrero\"}",
            "{\"email\":\"no-es-un-correo\",\"apodo\":\"ElGuerrero\"}",
            "{\"email\":\"jugador@ejemplo.com\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"  \"}",
    })
    void rechazaBienvenidaConDatosInvalidos(String cuerpo) throws Exception {
        mockMvc.perform(post(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @ParameterizedTest(name = "aviso rechazado: {0}")
    @ValueSource(strings = {
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"fechaHora\":\"2026-08-30T14:23:11-05:00\"}",
            "{\"email\":\"jugador@ejemplo.com\",\"apodo\":\"ElGuerrero\",\"ip\":\"190.85.12.44\"}",
    })
    void rechazaAvisoDeAccesoIncompleto(String cuerpo) throws Exception {
        mockMvc.perform(post(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void elErrorDeValidacionSaleEnFormatoProblemDetails() throws Exception {
        String cuerpo = mockMvc.perform(post(BIENVENIDA)
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
        mockMvc.perform(post(BIENVENIDA).contentType(MediaType.APPLICATION_JSON).content("""
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
        mockMvc.perform(post(AVISO_ACCESO).contentType(MediaType.APPLICATION_JSON).content("""
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
        mockMvc.perform(post(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        verify(enviador).enviar(
                eq("jugador@ejemplo.com"), anyString(), eq("email/recuperacion-clave"), any());
    }

    @Test
    void elCorreoDeRecuperacionLlevaElCodigoYSuVigencia() throws Exception {
        mockMvc.perform(post(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
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
        mockMvc.perform(post(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void losAsuntosEstanBienEscritosEnEspanol() throws Exception {
        // Un typo en el asunto lo ve cada destinatario y no lo atrapa ninguna
        // prueba de estructura: solo se nota leyendolo.
        mockMvc.perform(post(RECUPERACION).contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                 "codigo":"482915","minutosVigencia":15}
                """))
                .andExpect(status().isAccepted());

        org.mockito.ArgumentCaptor<String> asunto = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(enviador).enviar(anyString(), asunto.capture(), anyString(), any());

        assertThat(asunto.getValue()).isEqualTo("Recupera tu contraseña de The Nexus Battles VI");
    }
}
