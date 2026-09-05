package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.controller.AdminActionDemoController;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.AuditoriaEventClient;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecurityBypassTest {

    private MockMvc mockMvc;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "claveSecretaTexto", "clave-de-pruebas-suficientemente-larga-para-hmac-sha");
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);

        RbacMatrixRepository repository = new RbacMatrixRepository();
        RbacAuthorizationService service = new RbacAuthorizationService(repository);
        AuditoriaEventClient auditoriaClient = new AuditoriaEventClient("http://localhost:8083/api/v1/admin/auditoria/eventos");
        SecurityInterceptor interceptor = new SecurityInterceptor(service, auditoriaClient, jwtService);
        AdminActionDemoController controller = new AdminActionDemoController();

        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addInterceptors(interceptor)
            .build();
    }

    @Test
    @DisplayName("Ataque Bypass: Usuario 'JUGADOR' intenta invocar /api/v1/admin/ban -> 403 Forbidden")
    void testJugadorCannotBypassAdminEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ban")
                .header("X-User-Name", "jugador_atacante")
                .header("X-User-Role", "JUGADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("No tienes permiso para esta acción"));
    }

    @Test
    @DisplayName("Petición sin Rol ni Token (Anónimo) -> 403 Forbidden (Fail-Closed)")
    void testNoTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ban")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario 'ADMINISTRADOR' invoca /api/v1/admin/ban con header temporal -> 200 OK")
    void testAdminCanBan() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ban")
                .header("X-User-Name", "super_admin_user")
                .header("X-User-Role", "ADMINISTRADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("JWT Válido con rol 'ADMINISTRADOR' -> 200 OK")
    void testValidJwtAdminCanBan() throws Exception {
        String token = jwtService.generarToken("admin_autenticado", "ADMINISTRADOR", 0);

        mockMvc.perform(post("/api/v1/admin/ban")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("JWT Válido con rol 'JUGADOR' intenta invocar /api/v1/admin/ban -> 403 Forbidden")
    void testValidJwtJugadorCannotBan() throws Exception {
        String token = jwtService.generarToken("jugador_autenticado", "JUGADOR", 0);

        mockMvc.perform(post("/api/v1/admin/ban")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("No tienes permiso para esta acción"));
    }

    @Test
    @DisplayName("JWT Alterado/Manipulado -> 403 Forbidden (Fail-Closed)")
    void testTamperedJwtIsForbidden() throws Exception {
        String token = jwtService.generarToken("hacker", "SUPER_ADMINISTRADOR", 0);
        String tamperedToken = token.substring(0, token.length() - 2) + "ZZ";

        mockMvc.perform(post("/api/v1/admin/ban")
                .header("Authorization", "Bearer " + tamperedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("Token de autenticación inválido o expirado"));
    }

    @Test
    @DisplayName("JWT con versión de token vieja (rol fue cambiado) -> 403 Forbidden (HU-RBAC-003)")
    void testRevokedTokenVersionIsForbidden() throws Exception {
        // Generar un JWT con versión 0 (como si el usuario nunca hubiera cambiado de rol)
        String tokenViejo = jwtService.generarToken("admin_degradado", "ADMINISTRADOR", 0);

        // Simular que el usuario ahora tiene versionToken=1 (le cambiaron el rol)
        Usuario usuario = mock(Usuario.class);
        when(usuario.getVersionToken()).thenReturn(1);

        UsuarioRepository mockRepo = mock(UsuarioRepository.class);
        when(mockRepo.findByApodo("admin_degradado")).thenReturn(Optional.of(usuario));

        // Construir MockMvc con el interceptor que SÍ tiene UsuarioRepository
        RbacMatrixRepository repository = new RbacMatrixRepository();
        RbacAuthorizationService service = new RbacAuthorizationService(repository);
        AuditoriaEventClient auditoriaClient = new AuditoriaEventClient("http://localhost:8083/api/v1/admin/auditoria/eventos");
        SecurityInterceptor interceptor = new SecurityInterceptor(service, auditoriaClient, jwtService, mockRepo);
        AdminActionDemoController controller = new AdminActionDemoController();

        MockMvc mockMvcConRepo = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor)
                .build();

        mockMvcConRepo.perform(post("/api/v1/admin/ban")
                .header("Authorization", "Bearer " + tokenViejo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Token de autenticación inválido o expirado"));
    }
}
