package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.controller.CambioDePasswordController;
import com.nexusbattles.ms_identidad.auth.dto.CambiarPasswordRequest;
import com.nexusbattles.ms_identidad.auth.dto.CambioDePasswordResponse;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.CambioDePasswordService;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /api/v1/auth/password} con el interceptor REAL y tokens REALES
 * (firmados por el JwtService de este servicio). Lo que se afirma: quien
 * cambia es el sujeto del token y no un campo del cuerpo; sin token no se
 * entra; un token de una version revocada tampoco; y los rechazos salen como
 * problem details (CA-06) con su type.
 */
@DisplayName("PUT /api/v1/auth/password · HU-AUT-006")
class CambioDePasswordControllerTest {

    private static final String CUERPO = """
            {"passwordActual":"Actual-Segura-1!","nuevaPassword":"Nueva-Segura-2!","confirmacion":"Nueva-Segura-2!"}
            """;

    private MockMvc mockMvc;
    private JwtService jwtService;
    private CambioDePasswordService servicio;
    private UsuarioRepository usuarioRepository;
    private final UUID uid = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");

        usuarioRepository = mock(UsuarioRepository.class);
        Usuario ana = new Usuario();
        ana.setApodo("ana");
        ana.setVersionToken(2);
        when(usuarioRepository.findByApodo("ana")).thenReturn(Optional.of(ana));

        servicio = mock(CambioDePasswordService.class);
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()),
                new AuditoriaEventClient("http://localhost:8091/api/v1/admin/auditoria/eventos", null),
                jwtService, usuarioRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(new CambioDePasswordController(servicio))
                .addInterceptors(interceptor)
                .build();
    }

    private String tokenDeAna(int version) {
        return jwtService.generarToken("ana", "JUGADOR", version, uid);
    }

    @Test
    @DisplayName("con token vigente, cambia para el sujeto del token y devuelve el token nuevo")
    void cambiaParaElSujetoDelToken() throws Exception {
        when(servicio.cambiar(eq("ana"), any(), any()))
                .thenReturn(new CambioDePasswordResponse("token-nuevo", "Contraseña actualizada."));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenDeAna(2))
                        .header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-nuevo"))
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada."));

        ArgumentCaptor<CambiarPasswordRequest> datos = ArgumentCaptor.forClass(CambiarPasswordRequest.class);
        verify(servicio).cambiar(eq("ana"), datos.capture(), eq("203.0.113.9"));
        assertEquals("Actual-Segura-1!", datos.getValue().getPasswordActual());
        assertEquals("Nueva-Segura-2!", datos.getValue().getNuevaPassword());
    }

    @Test
    @DisplayName("sin token no se entra (fail-closed del interceptor) y el servicio ni se llama")
    void sinToken() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());

        verify(servicio, never()).cambiar(any(), any(), any());
    }

    @Test
    @DisplayName("CA-04 desde el otro lado: un token de una version ya revocada no sirve para cambiarla otra vez")
    void tokenDeVersionRevocada() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenDeAna(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verify(servicio, never()).cambiar(any(), any(), any());
    }

    @Test
    @DisplayName("un cuerpo que intente nombrar a otro usuario no cambia a quien se aplica: manda el token")
    void elCuerpoNoIdentificaANadie() throws Exception {
        when(servicio.cambiar(eq("ana"), any(), any()))
                .thenReturn(new CambioDePasswordResponse("t", "ok"));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenDeAna(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apodo":"bruno","usuarioId":99,"passwordActual":"Actual-Segura-1!",
                                 "nuevaPassword":"Nueva-Segura-2!","confirmacion":"Nueva-Segura-2!"}
                                """))
                .andExpect(status().isOk());

        verify(servicio).cambiar(eq("ana"), any(), any());
    }

    @Test
    @DisplayName("CA-06: un rechazo sale como problem details con su type, no como texto plano")
    void rechazoComoProblemDetails() throws Exception {
        when(servicio.cambiar(eq("ana"), any(), any())).thenThrow(
                new CambioDePasswordRechazadoException(Motivo.ACTUAL_INCORRECTA, "La contraseña actual es incorrecta."));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenDeAna(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/contrasena-actual-incorrecta"))
                .andExpect(jsonPath("$.title").value("La contraseña actual es incorrecta"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("La contraseña actual es incorrecta."));
    }

    @Test
    @DisplayName("la cuenta bloqueada responde 423 con su type")
    void cuentaBloqueada() throws Exception {
        when(servicio.cambiar(eq("ana"), any(), any())).thenThrow(
                new CambioDePasswordRechazadoException(Motivo.CUENTA_BLOQUEADA, "Cuenta bloqueada temporalmente."));

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenDeAna(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/cuenta-bloqueada"));
    }

    @Test
    @DisplayName("un cuerpo incompleto se rechaza antes de llegar al servicio")
    void cuerpoIncompleto() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + tokenDeAna(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"x\"}"))
                .andExpect(status().isBadRequest());

        verify(servicio, never()).cambiar(any(), any(), any());
    }
}
