package com.nexusbattles.ms_identidad.onboarding.controller;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.onboarding.dto.OnboardingResponse;
import com.nexusbattles.ms_identidad.onboarding.service.OnboardingService;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/auth/onboarding} con el interceptor REAL y tokens REALES: solo
 * lo propio (el uid sale del token), sin sesion no se entra, y la respuesta
 * no se guarda en cache.
 */
@DisplayName("GET/POST /api/v1/auth/onboarding (R17)")
class OnboardingControllerTest {

    private final UUID uid = UUID.randomUUID();
    private MockMvc mockMvc;
    private JwtService jwtService;
    private OnboardingService servicio;

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");

        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario profe = new Usuario();
        profe.setApodo("profe");
        profe.setVersionToken(0);
        when(usuarios.findByApodo("profe")).thenReturn(Optional.of(profe));

        servicio = mock(OnboardingService.class);
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()),
                new AuditoriaEventClient("http://localhost:8091/api/v1/admin/auditoria/eventos", null),
                jwtService, usuarios, false);
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(servicio))
                .addInterceptors(interceptor)
                .build();
    }

    private String token() {
        return "Bearer " + jwtService.generarToken("profe", "JUGADOR", 0, uid);
    }

    private static OnboardingResponse enProceso() {
        return new OnboardingResponse("EN_PROCESO", false, 1, 1, null,
                List.of(new OnboardingResponse.Paso("CREDITOS", "Créditos de bienvenida", "PENDIENTE", null)),
                null, null);
    }

    @Test
    @DisplayName("devuelve el estado del alta del uid del token, sin cache")
    void consulta() throws Exception {
        when(servicio.estadoDe(uid)).thenReturn(enProceso());

        mockMvc.perform(get("/api/v1/auth/onboarding").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
                .andExpect(jsonPath("$.listo").value(false))
                .andExpect(jsonPath("$.pasos[0].titulo").value("Créditos de bienvenida"));
    }

    @Test
    @DisplayName("reintentar responde 202 y lanza solo el alta propia")
    void reintenta() throws Exception {
        when(servicio.solicitarReintento(uid)).thenReturn(enProceso());

        mockMvc.perform(post("/api/v1/auth/onboarding/reintentos")
                        .header("Authorization", token())
                        .param("uid", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(servicio).solicitarReintento(uid);
    }

    @Test
    @DisplayName("sin sesion no se consulta ni se relanza nada")
    void sinSesion() throws Exception {
        mockMvc.perform(get("/api/v1/auth/onboarding")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/onboarding/reintentos")).andExpect(status().isForbidden());

        verify(servicio, never()).estadoDe(any());
        verify(servicio, never()).solicitarReintento(any());
    }

    @Test
    @DisplayName("el uid solo sale del atributo que deja el interceptor, y tiene que ser un UUID")
    void uidDelAtributo() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        assertThat(OnboardingController.uidDe(peticion)).isNull();
        peticion.setAttribute("uidActual", "no-es-uuid");
        assertThat(OnboardingController.uidDe(peticion)).isNull();
        peticion.setAttribute("uidActual", uid.toString());
        assertThat(OnboardingController.uidDe(peticion)).isEqualTo(uid);
    }
}
