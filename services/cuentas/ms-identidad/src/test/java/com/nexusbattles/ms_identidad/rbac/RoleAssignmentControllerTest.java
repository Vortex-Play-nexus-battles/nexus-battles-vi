package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.admin.service.UsuarioNoEncontradoException;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.controller.RoleAssignmentController;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import com.nexusbattles.ms_identidad.rbac.service.UltimoSuperAdministradorException;
import io.jsonwebtoken.Claims;
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

    @Mock
    private JwtService jwtService;

    @Mock
    private Claims claims;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        RbacMatrixRepository repository =
            new RbacMatrixRepository();

        RbacAuthorizationService authorizationService =
            new RbacAuthorizationService(repository);

        // R9.5 — en `true` a proposito: una de las pruebas de esta clase manda
        // X-User-Role y espera 403 por rol insuficiente. Con el respaldo
        // apagado seguiria dando 403, pero por otro motivo, y la prueba
        // dejaria de comprobar lo que dice comprobar.
        SecurityInterceptor interceptor =
            new SecurityInterceptor(
                authorizationService,
                null,
                jwtService,
                null,
                true
            );

        RoleAssignmentController controller =
            new RoleAssignmentController(
                roleAssignmentService,
                jwtService
            );

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
                    .header(
                        "X-User-Name",
                        "admin_sin_permiso"
                    )
                    .header(
                        "X-User-Role",
                        "ADMINISTRADOR"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                                        {
                                          "nuevoRol": "MODERADOR"
                                        }
                                        """)
            )
            .andExpect(
                status().isForbidden()
            );

        verify(
            roleAssignmentService,
            never()
        ).asignarRol(
            anyLong(),
            any(),
            anyString(),
            anyString(),
            any(),
            any()
        );
    }

    @Test
    void debePermitirAsignacionConJwtDeSuperAdministrador()
        throws Exception {

        when(
            jwtService.validarYObtenerClaims(
                "token-valido"
            )
        ).thenReturn(claims);

        when(claims.getSubject())
            .thenReturn("super_admin");

        when(
            claims.get(
                "rol",
                String.class
            )
        ).thenReturn(
            "SUPER_ADMINISTRADOR"
        );

        mockMvc.perform(
                put("/api/v1/rbac/usuarios/2/rol")
                    .header(
                        "Authorization",
                        "Bearer token-valido"
                    )
                    .with(request -> {
                        request.setRemoteAddr(
                            "10.0.0.50"
                        );
                        return request;
                    })
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                                        {
                                          "nuevoRol": "MODERADOR"
                                        }
                                        """)
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.mensaje")
                    .value(
                        "Rol actualizado correctamente"
                    )
            );

        verify(
            roleAssignmentService
        ).asignarRol(
            2L,
            Role.MODERADOR,
            "super_admin",
            "10.0.0.50",
            null,
            null
        );
    }

    private void comoSuperAdministrador() {
        when(jwtService.validarYObtenerClaims("token-valido")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("super_admin");
        when(claims.get("rol", String.class)).thenReturn("SUPER_ADMINISTRADOR");
        when(claims.get("uid", String.class)).thenReturn("uid-del-super");
    }

    @Test
    void elMotivoYElUidDeQuienActuaLleganAlServicio() throws Exception {
        comoSuperAdministrador();

        mockMvc.perform(put("/api/v1/rbac/usuarios/2/rol")
                .header("Authorization", "Bearer token-valido")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nuevoRol": "MODERADOR", "motivo": "Refuerzo del equipo de moderación"}
                    """))
            .andExpect(status().isOk());

        verify(roleAssignmentService).asignarRol(eq(2L), eq(Role.MODERADOR), eq("super_admin"), anyString(),
            eq("Refuerzo del equipo de moderación"), eq("uid-del-super"));
    }

    @Test
    void cuentaInexistenteEs404ConSuTipo() throws Exception {
        comoSuperAdministrador();
        doThrow(new UsuarioNoEncontradoException(99L)).when(roleAssignmentService)
            .asignarRol(eq(99L), any(), anyString(), anyString(), any(), any());

        mockMvc.perform(put("/api/v1/rbac/usuarios/99/rol")
                .header("Authorization", "Bearer token-valido")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nuevoRol\": \"MODERADOR\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://nexusbattles.upb.edu.co/errors/cuenta-no-encontrada"));
    }

    @Test
    void quitarElUltimoSuperAdministradorEs409() throws Exception {
        comoSuperAdministrador();
        doThrow(new UltimoSuperAdministradorException()).when(roleAssignmentService)
            .asignarRol(eq(2L), any(), anyString(), anyString(), any(), any());

        mockMvc.perform(put("/api/v1/rbac/usuarios/2/rol")
                .header("Authorization", "Bearer token-valido")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nuevoRol\": \"ADMINISTRADOR\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type")
                .value("https://nexusbattles.upb.edu.co/errors/ultimo-super-administrador"));
    }
}
