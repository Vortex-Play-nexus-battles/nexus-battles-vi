package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.dto.LoginRequest;
import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.exception.CredencialesInvalidasException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBloqueadaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaInactivaException;
import com.nexusbattles.ms_identidad.auth.exception.CuentaSuspendidaException;
import com.nexusbattles.ms_identidad.auth.model.DispositivoConocido;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.DispositivoConocidoRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AuditoriaLoginClient;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.auth.service.LoginService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DispositivoConocidoRepository dispositivoConocidoRepository;

    @Mock
    private IntentosFallidosService intentosFallidosService;

    @Mock
    private CorreoClient correoClient;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditoriaLoginClient auditoriaLoginClient;

    @InjectMocks
    private LoginService loginService;

    private static final String PASSWORD_PLANA = "MiClave123!";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setApodo("cristianc");
        usuario.setEmail("cristian@test.com");
        usuario.setPassword(encoder.encode(PASSWORD_PLANA));
        usuario.setEstado("ACTIVO");

        RolEntity rol = new RolEntity();
        rol.setNombre("JUGADOR");
        usuario.setRol(rol);

        return usuario;
    }

    private LoginRequest datosValidos() {
        LoginRequest datos = new LoginRequest();
        datos.setEmail("cristian@test.com");
        datos.setPassword(PASSWORD_PLANA);
        return datos;
    }

    @Test
    void debeRechazarSiCorreoNoExiste() {

        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        CredencialesInvalidasException exception = assertThrows(
            CredencialesInvalidasException.class,
            () -> loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente")
        );

        assertEquals("Correo o contraseña incorrectos.", exception.getMessage());

        verify(auditoriaLoginClient).registrarLoginFallido(
            "cristian@test.com",
            "127.0.0.1"
        );
    }

    @Test
    void debeRechazarYRegistrarIntentoSiContrasenaIncorrecta() {

        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));

        LoginRequest datos = datosValidos();
        datos.setPassword("ClaveIncorrecta1!");

        assertThrows(
            CredencialesInvalidasException.class,
            () -> loginService.iniciarSesion(datos, "127.0.0.1", "agente")
        );

        verify(intentosFallidosService).registrarIntentoFallido(1L);

        verify(auditoriaLoginClient).registrarLoginFallido(
            "cristian@test.com",
            "127.0.0.1"
        );
    }

    @Test
    void debeRechazarCuentaBaneada() {

        Usuario usuario = usuarioActivo();
        usuario.setEstado("BANEADA");
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));

        CuentaBaneadaException exception = assertThrows(
            CuentaBaneadaException.class,
            () -> loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente")
        );

        assertEquals("Esta cuenta ha sido baneada permanentemente.", exception.getMessage());
        verify(intentosFallidosService, never()).registrarIntentoFallido(anyLong());
    }

    @Test
    void debeRechazarCuentaInactiva() {

        Usuario usuario = usuarioActivo();
        usuario.setEstado("INACTIVO");
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));

        CuentaInactivaException exception = assertThrows(
            CuentaInactivaException.class,
            () -> loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente")
        );

        assertTrue(exception.getMessage().contains("no ha sido activada"));
    }

    @Test
    void debeRechazarCuentaSuspendidaVigente() {

        Usuario usuario = usuarioActivo();
        usuario.setEstado("SUSPENDIDA");
        usuario.setSuspendidoHasta(LocalDateTime.now().plusHours(3));
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));

        CuentaSuspendidaException exception = assertThrows(
            CuentaSuspendidaException.class,
            () -> loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente")
        );

        assertTrue(exception.getMessage().contains("Tiempo restante"));
    }

    @Test
    void debePermitirLoginSiSuspensionYaVencio() {
        // Comportamiento actual documentado: al vencer suspendidoHasta, el
        // login no queda bloqueado, aunque el campo "estado" siga en
        // SUSPENDIDA (nadie lo revierte a ACTIVO automáticamente aquí).

        Usuario usuario = usuarioActivo();
        usuario.setEstado("SUSPENDIDA");
        usuario.setSuspendidoHasta(LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));
        when(dispositivoConocidoRepository.findByUsuarioAndHuella(eq(usuario), anyString()))
            .thenReturn(Optional.of(new DispositivoConocido()));
        when(jwtService.generarToken(anyString(), anyString(), anyInt())).thenReturn("token-de-prueba");

        LoginResponse respuesta = loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente");

        assertNotNull(respuesta);
        assertEquals("cristianc", respuesta.getApodo());
    }

    @Test
    void debeRechazarCuentaBloqueadaPorIntentosIncusoConContrasenaCorrecta() {

        Usuario usuario = usuarioActivo();
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));

        CuentaBloqueadaException exception = assertThrows(
            CuentaBloqueadaException.class,
            () -> loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente")
        );

        assertTrue(exception.getMessage().contains("intentos fallidos"));
    }

    @Test
    void debeIniciarSesionYResetearContadoresConDispositivoNuevo() {

        Usuario usuario = usuarioActivo();
        usuario.setIntentosFallidos(3);
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));
        when(dispositivoConocidoRepository.findByUsuarioAndHuella(eq(usuario), anyString()))
            .thenReturn(Optional.empty());
        when(jwtService.generarToken(anyString(), anyString(), anyInt())).thenReturn("token-de-prueba");

        LoginResponse respuesta = loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente");

        assertEquals(1L, respuesta.getUsuarioId());
        assertEquals("cristianc", respuesta.getApodo());
        assertEquals("cristian@test.com", respuesta.getEmail());
        assertEquals("JUGADOR", respuesta.getRol());
        assertTrue(respuesta.isDispositivoNuevo());
        assertEquals("token-de-prueba", respuesta.getToken());

        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
        verify(dispositivoConocidoRepository).save(any(DispositivoConocido.class));
        verify(correoClient).enviarAvisoAcceso(any());
    }

    @Test
    void debeIniciarSesionSinMarcarDispositivoNuevoSiYaEsConocido() {

        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(usuario));
        when(dispositivoConocidoRepository.findByUsuarioAndHuella(eq(usuario), anyString()))
            .thenReturn(Optional.of(new DispositivoConocido()));
        when(jwtService.generarToken(anyString(), anyString(), anyInt())).thenReturn("token-de-prueba");

        LoginResponse respuesta = loginService.iniciarSesion(datosValidos(), "127.0.0.1", "agente");

        assertFalse(respuesta.isDispositivoNuevo());
        verify(dispositivoConocidoRepository, never()).save(any());
        verify(correoClient, never()).enviarAvisoAcceso(any());
    }
}
