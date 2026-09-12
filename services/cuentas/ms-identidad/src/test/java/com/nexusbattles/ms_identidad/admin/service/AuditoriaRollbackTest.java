package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HU-AUD-001 (fail-closed): si el registro de auditoría falla, la operación
 * administrativa NO debe consumarse.
 *
 * AuditoriaClient está configurado como fail-closed: su fallback SIEMPRE lanza
 * IllegalStateException cuando ms-cumplimiento no responde. Estos servicios son
 * @Transactional, así que esa excepción propagada es la que dispara el rollback
 * de la transacción en tiempo de ejecución.
 *
 * Alcance: estas pruebas son UNITARIAS (con mocks). Verifican que la excepción
 * de auditoría se PROPAGA hacia afuera del método de negocio (que es el
 * mecanismo del que depende el rollback). El rollback físico de la base de
 * datos lo garantiza Spring vía @Transactional ante una excepción propagada, y
 * su verificación de extremo a extremo correspondería a una prueba de
 * integración con base de datos real.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaRollbackTest {

    private static final Long USUARIO_ID = 1L;
    private static final String ADMIN = "admin";
    private static final String IP = "127.0.0.1";

    @Mock
    private AuthAdminService authAdminService;

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private AuditoriaClient auditoriaClient;

    @Mock
    private AvatarStorageService avatarStorageService;

    @Mock
    private NotificacionClient notificacionClient;

    @InjectMocks
    private AdminGestionUsuarioService service;

    private void auditoriaFalla() {
        doThrow(new IllegalStateException(
            "No se pudo completar la operación: el servicio de auditoría no respondió."))
            .when(auditoriaClient).registrar(
                anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    @Test
    void suspender_siAuditoriaFalla_propagaExcepcion() {
        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID)).thenReturn("ACTIVO");
        auditoriaFalla();

        assertThrows(IllegalStateException.class, () ->
            service.suspenderCuenta(USUARIO_ID, LocalDateTime.now().plusDays(1), ADMIN, IP));
    }

    @Test
    void banear_siAuditoriaFalla_propagaExcepcion() {
        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID)).thenReturn("ACTIVO");
        auditoriaFalla();

        assertThrows(IllegalStateException.class, () ->
            service.banearCuenta(USUARIO_ID, ADMIN, IP));
    }

    @Test
    void reactivar_siAuditoriaFalla_propagaExcepcion() {
        when(authAdminService.obtenerEstadoCuenta(USUARIO_ID)).thenReturn("SUSPENDIDA");
        auditoriaFalla();

        assertThrows(IllegalStateException.class, () ->
            service.reactivarCuenta(USUARIO_ID, ADMIN, IP));
    }

    @Test
    void restablecerPassword_siAuditoriaFalla_propagaExcepcion() {
        auditoriaFalla();

        assertThrows(IllegalStateException.class, () ->
            service.restablecerPassword(USUARIO_ID, ADMIN, IP));
    }

    @Test
    void editarPerfil_siAuditoriaFalla_propagaExcepcion() {
        when(perfilUsuarioService.actualizarPerfilPropio(
            anyLong(), any(), any(), any(), any(), any()))
            .thenReturn(new PerfilUsuario());
        auditoriaFalla();

        assertThrows(IllegalStateException.class, () ->
            service.editarPerfilDeUsuario(
                USUARIO_ID, "Nombre", "Apellido", null, "prefs", null, ADMIN, IP));
    }
}
