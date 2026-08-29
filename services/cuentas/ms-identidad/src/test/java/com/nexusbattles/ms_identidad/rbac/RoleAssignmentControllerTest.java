package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.rbac.controller.RoleAssignmentController;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleAssignmentControllerTest {

    @Mock
    private RoleAssignmentService roleAssignmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        RbacMatrixRepository repository =
                new RbacMatrixRepository();

        RbacAuthorizationService authorizationService =
                new RbacAuthorizationService(repository);

        SecurityInterceptor interceptor =
                new SecurityInterceptor(authorizationService);

        RoleAssignmentController controller =
                new RoleAssignmentController(roleAssignmentService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void debeRechazarAsignacionCuandoSolicitanteNoEsSuperAdministrador()
            throws Exception {

        mockMvc.perform(
                        put("/api/v1/rbac/usuarios/2/rol")
                                .header("X-User-Name", "admin_sin_permiso")
                                .header("X-User-Role", "ADMINISTRADOR")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "nuevoRol": "MODERADOR"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(roleAssignmentService, never())
                .asignarRol(anyLong(), any());
    }

    @Test
    void debePermitirAsignacionCuandoSolicitanteEsSuperAdministrador()
            throws Exception {

        mockMvc.perform(
                        put("/api/v1/rbac/usuarios/2/rol")
                                .header("X-User-Name", "super_admin")
                                .header("X-User-Role", "SUPER_ADMINISTRADOR")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "nuevoRol": "MODERADOR"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.mensaje")
                                .value("Rol actualizado correctamente")
                );

        verify(roleAssignmentService)
                .asignarRol(2L, com.nexusbattles.ms_identidad.rbac.model.Role.MODERADOR);
    }
}