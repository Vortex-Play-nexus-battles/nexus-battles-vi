package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminServiceImpl;
import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthAdminServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApodoBlacklistValidator apodoBlacklistValidator;

    @Mock
    private RolService rolService;

    @Mock
    private CodigosDeCorreo codigos;

    private AuthAdminServiceImpl authAdminService;

    private AuthAdminServiceImpl construir() {
        return new AuthAdminServiceImpl(
            usuarioRepository, apodoBlacklistValidator, rolService, codigos
        );
    }

    private Usuario usuarioConId(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    @Test
    void debeCrearCuentaEnEstadoInactivoYGenerarTokenDeActivacion() {

        authAdminService = construir();

        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo(anyString())).thenReturn(Optional.empty());
        when(rolService.obtenerRolPorNombre("JUGADOR")).thenReturn(new RolEntity());
        when(usuarioRepository.save(any(Usuario.class)))
            .thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario resultado = authAdminService.crearCuentaConRol(
            "Cristian", "Chaparro", "cristian@test.com",
            "cristianc", "avatar.jpg", Role.JUGADOR
        );

        assertEquals("INACTIVO", resultado.getEstado());
        assertNotNull(resultado.getPassword());
        verify(codigos).emitir(resultado, TipoCodigo.ACTIVACION);
    }

    @Test
    void debeLanzarExcepcionAlCrearCuentaSiCorreoYaExiste() {

        authAdminService = construir();

        when(usuarioRepository.findByEmail("cristian@test.com"))
            .thenReturn(Optional.of(new Usuario()));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authAdminService.crearCuentaConRol(
                "Cristian", "Chaparro", "cristian@test.com",
                "cristianc", "avatar.jpg", Role.JUGADOR
            )
        );

        assertEquals("El correo electrónico ya está registrado.", exception.getMessage());
        verify(codigos, never()).emitir(any(), any());
    }

    @Test
    void debeRechazarEstadoInvalidoAlActualizar() {

        authAdminService = construir();

        // B2: las formas en femenino anteriores al contrato ya no se escriben.
        assertThrows(IllegalArgumentException.class,
            () -> authAdminService.actualizarEstadoCuenta(1L, "SUSPENDIDA", null));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> authAdminService.actualizarEstadoCuenta(1L, "ACTIVA", null)
        );

        assertTrue(exception.getMessage().contains("Estado inválido"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debeActualizarEstadoASuspendidaConFecha() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);
        LocalDateTime hasta = LocalDateTime.now().plusDays(3);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authAdminService.actualizarEstadoCuenta(1L, "SUSPENDIDO", hasta);

        assertEquals("SUSPENDIDO", usuario.getEstado());
        assertEquals(hasta, usuario.getSuspendidoHasta());
        assertEquals(1, usuario.getVersionToken(), "B2: suspender cierra las sesiones abiertas");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void debeLimpiarSuspendidoHastaAlReactivarCuenta() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);
        usuario.setEstado("SUSPENDIDO");
        usuario.setSuspendidoHasta(LocalDateTime.now().plusDays(1));

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authAdminService.actualizarEstadoCuenta(1L, "ACTIVO", null);

        assertEquals("ACTIVO", usuario.getEstado());
        assertNull(usuario.getSuspendidoHasta());
        assertEquals(0, usuario.getVersionToken(), "reactivar no revoca nada");
    }

    @Test
    void debeGenerarTokenDeRestablecimientoAlRestablecerContrasena() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authAdminService.restablecerContrasena(1L);

        assertNotNull(usuario.getPassword());
        assertEquals(1, usuario.getVersionToken(), "la contraseña anterior deja de valer, y sus sesiones tambien");
        verify(usuarioRepository).save(usuario);
        verify(codigos).emitir(usuario, TipoCodigo.RESTABLECIMIENTO);
    }

    @Test
    void debeBanearYCerrarLasSesiones() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);
        usuario.setSuspendidoHasta(LocalDateTime.now().plusDays(1));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authAdminService.actualizarEstadoCuenta(1L, "BANEADO", null);

        assertEquals("BANEADO", usuario.getEstado());
        assertNull(usuario.getSuspendidoHasta());
        assertEquals(1, usuario.getVersionToken());
    }

    @Test
    void debeDevolverEstadoActualDeLaCuenta() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);
        usuario.setEstado("BANEADA");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        String estado = authAdminService.obtenerEstadoCuenta(1L);

        assertEquals("BANEADA", estado);
    }

    @Test
    void debeLanzarExcepcionSiUsuarioNoExisteAlConsultarEstado() {

        authAdminService = construir();

        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
            IllegalStateException.class,
            () -> authAdminService.obtenerEstadoCuenta(99L)
        );
    }
}
