package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminGestionUsuarioServiceTest {

    @Mock
    private AuthAdminService authAdminService;

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private AuditoriaClient auditoriaClient;

    @InjectMocks
    private AdminGestionUsuarioService service;

    private static final Long USUARIO_ID = 1L;
    private static final String ADMINISTRADOR_ID = "admin";
    private static final String IP_ORIGEN = "127.0.0.1";

    @Test
    void debeEditarPerfilDeUsuario() {
        PerfilUsuario perfil = new PerfilUsuario();

        when(perfilUsuarioService.actualizarPerfilPropio(
            USUARIO_ID,
            "Santiago",
            "Sanabria",
            "avatar.png",
            "Biografía",
            "preferencias",
            "Santi"
        )).thenReturn(perfil);

        PerfilUsuario resultado = service.editarPerfilDeUsuario(
            USUARIO_ID,
            "Santiago",
            "Sanabria",
            "avatar.png",
            "Biografía",
            "preferencias",
            "Santi",
            ADMINISTRADOR_ID,
            IP_ORIGEN
        );

        assertEquals(perfil, resultado);

        verify(perfilUsuarioService).actualizarPerfilPropio(
            USUARIO_ID,
            "Santiago",
            "Sanabria",
            "avatar.png",
            "Biografía",
            "preferencias",
            "Santi"
        );

        verify(auditoriaClient).registrar(
            "ACTUALIZACION",
            ADMINISTRADOR_ID,
            "1",
            null,
            "nombres=Santiago, apellidos=Sanabria",
            "Edición administrativa de perfil",
            IP_ORIGEN
        );
    }

    @Test
    void debeSuspenderCuenta() {
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 10, 12, 0);

        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID))
            .thenReturn("ACTIVO");

        service.suspenderCuenta(
            USUARIO_ID,
            fecha,
            ADMINISTRADOR_ID,
            IP_ORIGEN
        );

        verify(authAdminService).actualizarEstadoCuenta(
            USUARIO_ID,
            "SUSPENDIDA",
            fecha
        );

        verify(auditoriaClient).registrar(
            "SUSPENSION",
            ADMINISTRADOR_ID,
            "1",
            "ACTIVO",
            "SUSPENDIDA hasta " + fecha,
            "Suspensión de cuenta",
            IP_ORIGEN
        );
    }

    @Test
    void debeBanearCuenta() {
        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID))
            .thenReturn("ACTIVO");

        service.banearCuenta(
            USUARIO_ID,
            ADMINISTRADOR_ID,
            IP_ORIGEN
        );

        verify(authAdminService).actualizarEstadoCuenta(
            USUARIO_ID,
            "BANEADA",
            null
        );

        verify(auditoriaClient).registrar(
            "SANCION",
            ADMINISTRADOR_ID,
            "1",
            "ACTIVO",
            "BANEADA",
            "Baneo definitivo de cuenta",
            IP_ORIGEN
        );
    }

    @Test
    void debeReactivarCuenta() {
        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID))
            .thenReturn("SUSPENDIDA");

        service.reactivarCuenta(
            USUARIO_ID,
            ADMINISTRADOR_ID,
            IP_ORIGEN
        );

        verify(authAdminService).actualizarEstadoCuenta(
            USUARIO_ID,
            "ACTIVO",
            null
        );

        verify(auditoriaClient).registrar(
            "ACTUALIZACION",
            ADMINISTRADOR_ID,
            "1",
            "SUSPENDIDA",
            "ACTIVO",
            "Reactivación de cuenta",
            IP_ORIGEN
        );
    }

    @Test
    void noDebeReactivarCuentaBaneada() {
        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID))
            .thenReturn("BANEADA");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.reactivarCuenta(
                USUARIO_ID,
                ADMINISTRADOR_ID,
                IP_ORIGEN
            )
        );

        assertEquals(
            "No se puede reactivar una cuenta baneada definitivamente.",
            exception.getMessage()
        );

        verify(authAdminService, never())
            .actualizarEstadoCuenta(anyLong(), anyString(), any());

        verifyNoInteractions(auditoriaClient);
    }

    @Test
    void debeRestablecerPassword() {
        service.restablecerPassword(
            USUARIO_ID,
            ADMINISTRADOR_ID,
            IP_ORIGEN
        );

        verify(authAdminService)
            .restablecerContrasena(USUARIO_ID);

        verify(auditoriaClient).registrar(
            "OTRO",
            ADMINISTRADOR_ID,
            "1",
            null,
            null,
            "Restablecimiento de contraseña (token de un solo uso generado)",
            IP_ORIGEN
        );
    }
}
