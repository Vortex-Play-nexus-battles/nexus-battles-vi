package com.nexusbattles.ms_identidad.admin.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminService;
import com.nexusbattles.ms_identidad.notificaciones.client.NotificacionClient;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient;
import com.nexusbattles.ms_identidad.sanciones.ProyeccionDeSancionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

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
 * B2: esto aplica a las operaciones que se hacen AQUI (edicion de perfil,
 * restablecimiento y las sanciones de cuentas sin uid). Las sanciones de
 * cuentas con uid las decide y registra moderacion-sanciones; su auditoria en
 * ms-cumplimiento no puede deshacerlas (ver AdminGestionUsuarioServiceTest).
 */
class AuditoriaRollbackTest {

    private static final Long USUARIO_ID = 1L;
    private static final String ADMIN = "admin";
    private static final String IP = "127.0.0.1";

    private AuthAdminService authAdminService;
    private PerfilUsuarioService perfilUsuarioService;
    private AuditoriaClient auditoriaClient;
    private AdminGestionUsuarioService service;
    private Usuario cuentaSinUid;

    @BeforeEach
    void preparar() {
        authAdminService = mock(AuthAdminService.class);
        perfilUsuarioService = mock(PerfilUsuarioService.class);
        auditoriaClient = mock(AuditoriaClient.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        service = new AdminGestionUsuarioService(authAdminService, perfilUsuarioService, auditoriaClient,
            mock(NotificacionClient.class), usuarios, mock(ModeracionSancionesClient.class),
            mock(ProyeccionDeSancionService.class));
        cuentaSinUid = new Usuario();
        cuentaSinUid.setId(USUARIO_ID);
        cuentaSinUid.setEstado("ACTIVO");
        when(usuarios.findById(USUARIO_ID)).thenReturn(Optional.of(cuentaSinUid));
        doThrow(new IllegalStateException(
            "No se pudo completar la operación: el servicio de auditoría no respondió."))
            .when(auditoriaClient).registrar(
                anyString(), anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    @Test
    void suspender_siAuditoriaFalla_propagaExcepcion() {
        assertThrows(IllegalStateException.class, () ->
            service.suspenderCuenta(USUARIO_ID, LocalDateTime.now().plusDays(1), null, null, ADMIN, IP));
    }

    @Test
    void banear_siAuditoriaFalla_propagaExcepcion() {
        assertThrows(IllegalStateException.class, () ->
            service.banearCuenta(USUARIO_ID, null, null, ADMIN, IP));
    }

    @Test
    void reactivar_siAuditoriaFalla_propagaExcepcion() {
        cuentaSinUid.setEstado("SUSPENDIDO");
        assertThrows(IllegalStateException.class, () ->
            service.reactivarCuenta(USUARIO_ID, null, ADMIN, IP));
    }

    @Test
    void restablecerPassword_siAuditoriaFalla_propagaExcepcion() {
        assertThrows(IllegalStateException.class, () ->
            service.restablecerPassword(USUARIO_ID, ADMIN, IP));
    }

    @Test
    void editarPerfil_siAuditoriaFalla_propagaExcepcion() {
        when(perfilUsuarioService.actualizarPerfilPropio(
            anyLong(), any(), any(), any(), any(), any()))
            .thenReturn(new PerfilUsuario());

        assertThrows(IllegalStateException.class, () ->
            service.editarPerfilDeUsuario(
                USUARIO_ID, "Nombre", "Apellido", null, "prefs", null, ADMIN, IP));
    }
}
