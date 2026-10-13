package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaNoVerificadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.AccesoConSegundoFactorResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.ActivacionConDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.CanjeDeDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

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

/**
 * {@code /api/v1/auth/login/segundo-factor}: publico (lo autentica el
 * desafio), siempre problem details, y la misma {@code LoginResponse} de
 * siempre cuando sale bien.
 */
@DisplayName("SegundoPasoController (segundo paso del login, publico)")
class SegundoPasoControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final String RUTA = "/api/v1/auth/login/segundo-factor";

    private MockMvc mockMvc;
    private SegundoPasoDelLogin segundoPaso;

    @BeforeEach
    void preparar() {
        segundoPaso = mock(SegundoPasoDelLogin.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SegundoPasoController(segundoPaso)).build();
    }

    private static LoginResponse sesion() {
        return new LoginResponse(7L, "ana", "ana@nexus.test", "ADMINISTRADOR", true, "token-2fa",
                "6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b", true);
    }

    @Test
    @DisplayName("canje correcto, sin token previo: la LoginResponse de siempre, sin cache y sin campos de mas")
    void canjeCorrecto() throws Exception {
        when(segundoPaso.canjear(eq(new CanjeDeDesafioRequest("valor", "287082", null)), eq("203.0.113.9"),
                eq("Navegador/1.0"))).thenReturn(AccesoConSegundoFactorResponse.con(sesion()));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.9").header("User-Agent", "Navegador/1.0")
                        .content("{\"desafio\":\"valor\",\"codigo\":\"287082\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value("token-2fa"))
                .andExpect(jsonPath("$.apodo").value("ana"))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.uid").value("6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b"))
                .andExpect(jsonPath("$.dispositivoNuevo").value(true))
                .andExpect(jsonPath("$.onboardingListo").value(true))
                .andExpect(jsonPath("$.codigosRecuperacionRestantes").doesNotExist())
                .andExpect(jsonPath("$.codigosRecuperacion").doesNotExist());
    }

    @Test
    @DisplayName("con un codigo de recuperacion dice cuantos quedan")
    void canjeConRecuperacion() throws Exception {
        when(segundoPaso.canjear(any(), any(), any()))
                .thenReturn(AccesoConSegundoFactorResponse.conRecuperacion(sesion(), 3));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"valor\",\"codigoRecuperacion\":\"K7QX2-M9PRT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigosRecuperacionRestantes").value(3));
    }

    @Test
    @DisplayName("desafio caducado o desconocido, y codigo incorrecto: 401 con su type; cuenta bloqueada: 423")
    void rechazos() throws Exception {
        when(segundoPaso.canjear(any(), any(), any()))
                .thenThrow(SegundoFactorRechazadoException.desafioInvalido())
                .thenThrow(new SegundoFactorRechazadoException(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO, "No vale."))
                .thenThrow(new SegundoFactorRechazadoException(Motivo.CUENTA_BLOQUEADA, "Bloqueada."))
                .thenThrow(new SegundoFactorRechazadoException(Motivo.NO_DISPONIBLE, "Sin clave."));
        String cuerpo = "{\"desafio\":\"valor\",\"codigo\":\"000000\"}";

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TIPOS + "desafio-invalido"))
                .andExpect(jsonPath("$.instance").value(RUTA));
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(TIPOS + "codigo-segundo-factor-invalido"));
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-bloqueada"));
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(TIPOS + "segundo-factor-no-disponible"));
    }

    @Test
    @DisplayName("una sancion entre los dos pasos: los mismos 403 que el login, con el fin de la suspension")
    void estadosDeCuenta() throws Exception {
        when(segundoPaso.canjear(any(), any(), any()))
                .thenThrow(new CuentaSuspendidaException("Suspendida.",
                        OffsetDateTime.parse("2026-10-06T10:00:00-05:00")))
                .thenThrow(new CuentaBaneadaException("Baneada."))
                .thenThrow(new CuentaInactivaException("Inactiva."))
                .thenThrow(new CuentaNoVerificadaException());
        String cuerpo = "{\"desafio\":\"valor\",\"codigo\":\"287082\"}";

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(TIPOS + "cuenta-suspendida"))
                .andExpect(jsonPath("$.suspendidoHasta").value("2026-10-06T15:00:00Z"));
        for (String tipo : new String[] {"cuenta-baneada", "cuenta-inactiva", "cuenta-no-verificada"}) {
            mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value(TIPOS + tipo));
        }
    }

    @Test
    @DisplayName("sin desafio o cuerpo ilegible: 400 datos-invalidos sin llegar al servicio")
    void malFormado() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"287082\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        mockMvc.perform(post(RUTA + "/activacion").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        verifyNoInteractions(segundoPaso);
    }

    @Test
    @DisplayName("enrolamiento obligatorio: secreto con el desafio, y la activacion entrega sesion y codigos")
    void enrolamientoObligatorio() throws Exception {
        when(segundoPaso.enrolarConDesafio(new DesafioRequest("valor"))).thenReturn(new EnrolamientoResponse(
                "JBSWY3DPEHPK3PXP", "otpauth://totp/x", "Nexus", "ana@nexus.test", "SHA1", 6, 30));
        when(segundoPaso.activarConDesafio(eq(new ActivacionConDesafioRequest("valor", "287082")), any(), any()))
                .thenReturn(AccesoConSegundoFactorResponse.recienActivado(sesion(), List.of("K7QX2-M9PRT")));

        mockMvc.perform(post(RUTA + "/enrolamiento").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"valor\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.secreto").value("JBSWY3DPEHPK3PXP"));
        mockMvc.perform(post(RUTA + "/activacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"valor\",\"codigo\":\"287082\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value("token-2fa"))
                .andExpect(jsonPath("$.codigosRecuperacion[0]").value("K7QX2-M9PRT"));
        verify(segundoPaso).activarConDesafio(eq(new ActivacionConDesafioRequest("valor", "287082")), any(), any());
    }
}
