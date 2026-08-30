package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuthAdminServiceImpl;
import com.nexusbattles.ms_identidad.auth.service.TokenCredencialService;
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
    private TokenCredencialService tokenCredencialService;

    private AuthAdminServiceImpl authAdminService;

    private AuthAdminServiceImpl construir() {
        return new AuthAdminServiceImpl(
            usuarioRepository, apodoBlacklistValidator, rolService, tokenCredencialService
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
        verify(tokenCredencialService).generarYRegistrarToken(resultado, "ACTIVACION");
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
        verify(tokenCredencialService, never()).generarYRegistrarToken(any(), anyString());
    }

    @Test
    void debeRechazarEstadoInvalidoAlActualizar() {

        authAdminService = construir();

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

        authAdminService.actualizarEstadoCuenta(1L, "SUSPENDIDA", hasta);

        assertEquals("SUSPENDIDA", usuario.getEstado());
        assertEquals(hasta, usuario.getSuspendidoHasta());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void debeLimpiarSuspendidoHastaAlReactivarCuenta() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);
        usuario.setEstado("SUSPENDIDA");
        usuario.setSuspendidoHasta(LocalDateTime.now().plusDays(1));

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authAdminService.actualizarEstadoCuenta(1L, "ACTIVO", null);

        assertEquals("ACTIVO", usuario.getEstado());
        assertNull(usuario.getSuspendidoHasta());
    }

    @Test
    void debeGenerarTokenDeRestablecimientoAlRestablecerContrasena() {

        authAdminService = construir();
        Usuario usuario = usuarioConId(1L);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authAdminService.restablecerContrasena(1L);

        assertNotNull(usuario.getPassword());
        verify(usuarioRepository).save(usuario);
        verify(tokenCredencialService).generarYRegistrarToken(usuario, "RESTABLECIMIENTO");
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
