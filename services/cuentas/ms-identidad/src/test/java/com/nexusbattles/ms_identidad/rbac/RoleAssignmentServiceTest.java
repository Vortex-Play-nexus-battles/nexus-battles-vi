package com.nexusbattles.ms_identidad.rbac;

import com.nexusbattles.ms_identidad.admin.service.JerarquiaInsuficienteException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.AuditoriaCambioRolClient;
import com.nexusbattles.ms_identidad.rbac.service.RoleAssignmentService;
import com.nexusbattles.ms_identidad.rbac.service.UltimoSuperAdministradorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private AuthAdminService authAdminService;

    @Mock
    private AuditoriaCambioRolClient auditoriaCambioRolClient;

    private RoleAssignmentService roleAssignmentService;

    @BeforeEach
    void setUp() {
        roleAssignmentService =
            new RoleAssignmentService(
                authAdminService,
                auditoriaCambioRolClient
            );
    }

    @Test
    void debeImpedirDegradarAlUltimoSuperAdministrador() {

        when(authAdminService.obtenerRolDeUsuario(1L))
            .thenReturn(Role.SUPER_ADMINISTRADOR);

        when(authAdminService.contarPorRol(
            Role.SUPER_ADMINISTRADOR))
            .thenReturn(1L);

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> roleAssignmentService.asignarRol(
                    1L,
                    Role.ADMINISTRADOR,
                    "super_admin",
                    "10.0.0.10"
                )
            );

        assertEquals(
            "No se puede quitar el rol Super Administrador: es el único que queda en el sistema.",
            exception.getMessage()
        );

        verifyNoInteractions(
            auditoriaCambioRolClient
        );

        verify(authAdminService, never())
            .actualizarRol(
                anyLong(),
                any()
            );
    }

    @Test
    void debeCambiarRolCorrectamenteYRegistrarAuditoria() {

        when(authAdminService.obtenerRolDeUsuario(2L))
            .thenReturn(Role.JUGADOR);

        roleAssignmentService.asignarRol(
            2L,
            Role.MODERADOR,
            "super_admin",
            "10.0.0.20"
        );

        verify(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin",
                2L,
                Role.JUGADOR,
                Role.MODERADOR,
                "10.0.0.20"
            );

        verify(authAdminService)
            .actualizarRol(
                2L,
                Role.MODERADOR
            );

        InOrder orden =
            inOrder(
                auditoriaCambioRolClient,
                authAdminService
            );

        orden.verify(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin",
                2L,
                Role.JUGADOR,
                Role.MODERADOR,
                "10.0.0.20"
            );

        orden.verify(authAdminService)
            .actualizarRol(
                2L,
                Role.MODERADOR
            );
    }

    @Test
    void debePermitirCambiarRolDeSuperAdministradorSiExisteOtro() {

        when(authAdminService.obtenerRolDeUsuario(1L))
            .thenReturn(Role.SUPER_ADMINISTRADOR);

        when(authAdminService.contarPorRol(
            Role.SUPER_ADMINISTRADOR))
            .thenReturn(2L);

        roleAssignmentService.asignarRol(
            1L,
            Role.ADMINISTRADOR,
            "super_admin_principal",
            "10.0.0.30"
        );

        verify(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin_principal",
                1L,
                Role.SUPER_ADMINISTRADOR,
                Role.ADMINISTRADOR,
                "10.0.0.30"
            );

        verify(authAdminService)
            .actualizarRol(
                1L,
                Role.ADMINISTRADOR
            );
    }

    @Test
    void noDebeCambiarRolSiLaAuditoriaFalla() {

        when(authAdminService.obtenerRolDeUsuario(2L))
            .thenReturn(Role.JUGADOR);

        doThrow(
            new IllegalStateException(
                "ms-cumplimiento no disponible"
            )
        )
            .when(auditoriaCambioRolClient)
            .registrarCambioRol(
                "super_admin",
                2L,
                Role.JUGADOR,
                Role.MODERADOR,
                "10.0.0.40"
            );

        assertThrows(
            IllegalStateException.class,
            () -> roleAssignmentService.asignarRol(
                2L,
                Role.MODERADOR,
                "super_admin",
                "10.0.0.40"
            )
        );

        verify(authAdminService, never())
            .actualizarRol(
                anyLong(),
                any()
            );
    }

    // --------------------------------------- con la cuenta: uid, motivo y aviso

    private static final UUID UID = UUID.fromString("0a0a0a0a-1111-4222-8333-444444444444");

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private NotificacionClient notificaciones;

    private RoleAssignmentService completo() {
        return new RoleAssignmentService(authAdminService, auditoriaCambioRolClient, usuarios, notificaciones);
    }

    private Usuario cuenta(Long id, UUID uid) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setPublicId(uid);
        return usuario;
    }

    @Test
    @DisplayName("la auditoria lleva el uid de la cuenta y el motivo; la cuenta recibe el aviso")
    void auditaConUidYMotivoYAvisa() {
        when(authAdminService.obtenerRolDeUsuario(2L)).thenReturn(Role.JUGADOR);
        when(usuarios.findById(2L)).thenReturn(Optional.of(cuenta(2L, UID)));

        completo().asignarRol(2L, Role.MODERADOR, "simon", "10.0.0.20", "Refuerzo del equipo de moderación",
            "ffffffff-1111-4222-8333-444444444444");

        InOrder orden = inOrder(auditoriaCambioRolClient, authAdminService, notificaciones);
        orden.verify(auditoriaCambioRolClient).registrarCambioRol("simon", UID.toString(), Role.JUGADOR,
            Role.MODERADOR, "Refuerzo del equipo de moderación", "10.0.0.20");
        orden.verify(authAdminService).actualizarRol(2L, Role.MODERADOR);
        orden.verify(notificaciones).emitir(eq(UID.toString()), eq("CUENTA"), anyString(), anyString());
    }

    @Test
    @DisplayName("nadie cambia su propio rol, ni siquiera un Super Administrador")
    void nuncaElPropioRol() {
        when(authAdminService.obtenerRolDeUsuario(1L)).thenReturn(Role.SUPER_ADMINISTRADOR);
        when(usuarios.findById(1L)).thenReturn(Optional.of(cuenta(1L, UID)));

        assertThrows(JerarquiaInsuficienteException.class, () -> completo().asignarRol(1L, Role.ADMINISTRADOR,
            "simon", "10.0.0.20", "Me equivoqué", UID.toString()));

        verifyNoInteractions(auditoriaCambioRolClient, notificaciones);
        verify(authAdminService, never()).actualizarRol(anyLong(), any());
    }

    @Test
    @DisplayName("el ultimo Super Administrador responde 409, no un 500")
    void ultimoSuperEsUnConflicto() {
        when(authAdminService.obtenerRolDeUsuario(1L)).thenReturn(Role.SUPER_ADMINISTRADOR);
        when(authAdminService.contarPorRol(Role.SUPER_ADMINISTRADOR)).thenReturn(1L);
        when(usuarios.findById(1L)).thenReturn(Optional.of(cuenta(1L, UID)));

        assertThrows(UltimoSuperAdministradorException.class, () -> completo().asignarRol(1L, Role.JUGADOR,
            "otro", "10.0.0.20", null, "ffffffff-1111-4222-8333-444444444444"));
        verifyNoInteractions(auditoriaCambioRolClient);
    }

    @Test
    @DisplayName("si el rol no cambia no se avisa a nadie")
    void mismoRolSinAviso() {
        when(authAdminService.obtenerRolDeUsuario(2L)).thenReturn(Role.MODERADOR);
        when(usuarios.findById(2L)).thenReturn(Optional.of(cuenta(2L, UID)));

        completo().asignarRol(2L, Role.MODERADOR, "simon", "10.0.0.20", null, null);

        verifyNoInteractions(notificaciones);
    }
}
