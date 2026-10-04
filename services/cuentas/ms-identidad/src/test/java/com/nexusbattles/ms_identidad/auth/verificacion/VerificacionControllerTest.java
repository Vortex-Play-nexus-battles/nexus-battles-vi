package com.nexusbattles.ms_identidad.auth.verificacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("POST /api/v1/auth/verificacion/* (B1)")
class VerificacionControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";

    private VerificacionDeCorreoService servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(VerificacionDeCorreoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new VerificacionController(servicio)).build();
    }

    private static String cuerpo(String email, String codigo) {
        return "{\"email\":\"" + email + "\",\"codigo\":\"" + codigo + "\"}";
    }

    @Test
    @DisplayName("confirmacion correcta: 200 VerificacionResponse, sin cache, con la IP del borde")
    void confirma() throws Exception {
        when(servicio.confirmar("ada@upb.edu.co", "K7QX2M9P", "203.0.113.5"))
                .thenReturn(new VerificacionResponse("ACTIVO", "Tu correo quedó verificado. Ya puedes iniciar sesión."));

        mvc.perform(post("/api/v1/auth/verificacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.5").content(cuerpo("ada@upb.edu.co", "K7QX2M9P")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.mensaje").value(containsString("verificado")));
    }

    @Test
    @DisplayName("codigo invalido: 400 codigo-invalido; agotado: 429 demasiados-intentos (problem details)")
    void errores() throws Exception {
        when(servicio.confirmar(eq("ada@upb.edu.co"), eq("MALO1234"), any())).thenThrow(new CodigoInvalidoException());
        when(servicio.confirmar(eq("ada@upb.edu.co"), eq("OTRO1234"), any())).thenThrow(new DemasiadosIntentosException());

        mvc.perform(post("/api/v1/auth/verificacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ada@upb.edu.co", "MALO1234")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TIPOS + "codigo-invalido"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/verificacion/confirmacion"));

        mvc.perform(post("/api/v1/auth/verificacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ada@upb.edu.co", "OTRO1234")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value(TIPOS + "demasiados-intentos"));
    }

    @Test
    @DisplayName("un cuerpo sin codigo o ilegible es 400 datos-invalidos, sin llegar al servicio")
    void malFormado() throws Exception {
        mvc.perform(post("/api/v1/auth/verificacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@upb.edu.co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        mvc.perform(post("/api/v1/auth/verificacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("reenvio: 202 y el mismo mensaje neutro pase lo que pase")
    void reenvio() throws Exception {
        mvc.perform(post("/api/v1/auth/verificacion/reenvio").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"quien-sea@upb.edu.co\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(VerificacionDeCorreoService.MENSAJE_REENVIO));
        verify(servicio).reenviar("quien-sea@upb.edu.co");
    }

    @Test
    @DisplayName("el cuerpo de la peticion no deja el codigo en ningun toString")
    void sinCodigoEnToString() {
        org.assertj.core.api.Assertions.assertThat(new CodigoDeCorreoRequest("a@b.co", "K7QX2M9P").toString())
                .doesNotContain("K7QX2M9P");
    }
}
