package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.rbac.controller.AdminActionDemoController;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecurityBypassTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RbacMatrixRepository repository = new RbacMatrixRepository();
        RbacAuthorizationService service = new RbacAuthorizationService(repository);
        SecurityInterceptor interceptor = new SecurityInterceptor(service);
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
    @DisplayName("Petición sin Rol (Anónimo) -> 403 Forbidden (Fail-Closed)")
    void testNoTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ban")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario 'ADMINISTRADOR' invoca /api/v1/admin/ban -> 200 OK")
    void testAdminCanBan() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ban")
                .header("X-User-Name", "super_admin_user")
                .header("X-User-Role", "ADMINISTRADOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"target_user_123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
