package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.RegistroService;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApodoBlacklistValidator apodoBlacklistValidator;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private RolService rolService;

    @Mock
    private PerfilUsuarioService perfilUsuarioService;

    @Mock
    private CorreoClient correoClient;

    @InjectMocks
    private RegistroService registroService;

    private RegistroRequest datosValidos() {
        RegistroRequest datos = new RegistroRequest();
        datos.setNombres("Cristian");
        datos.setApellidos("Chaparro");
        datos.setEmail("cristian@test.com");
        datos.setPassword("MiClave123!");
        datos.setApodo("cristianc");
        datos.setAvatar("https://ejemplo.com/avatar.png");
        return datos;
    }

    @Test
    void debeLanzarExcepcionSiCorreoYaExiste() {

        when(usuarioRepository.findByEmail("cristian@test.com"))
            .thenReturn(Optional.of(new Usuario()));

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("El correo electrónico ya está registrado.", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debeLanzarExcepcionSiApodoYaExiste() {

        when(usuarioRepository.findByEmail("cristian@test.com"))
            .thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo("cristianc"))
            .thenReturn(Optional.of(new Usuario()));

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("El apodo ya está en uso.", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debePropagarRechazoDeListaNegra() {

        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("El apodo contiene términos prohibidos."))
            .when(apodoBlacklistValidator).validar(anyString());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("El apodo contiene términos prohibidos.", exception.getMessage());
        verify(passwordPolicyValidator, never()).validar(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debePropagarRechazoDePoliticaDeContrasena() {

        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo(anyString())).thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("La contraseña no cumple la política."))
            .when(passwordPolicyValidator).validar(anyString());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("La contraseña no cumple la política.", exception.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debeRegistrarUsuarioCorrectamente() {

        RegistroRequest datos = datosValidos();
        RolEntity rolJugador = new RolEntity();

        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByApodo(anyString())).thenReturn(Optional.empty());
        when(rolService.obtenerRolPorNombre("JUGADOR")).thenReturn(rolJugador);
        when(usuarioRepository.save(any(Usuario.class)))
            .thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario resultado = registroService.registrarUsuario(datos);

        assertEquals("cristianc", resultado.getApodo());
        assertEquals("cristian@test.com", resultado.getEmail());
        assertEquals("ACTIVO", resultado.getEstado());
        assertEquals(rolJugador, resultado.getRol());
        assertNotEquals("MiClave123!", resultado.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("MiClave123!", resultado.getPassword()));

        verify(perfilUsuarioService).crearPerfil(
            resultado, "Cristian", "Chaparro", "https://ejemplo.com/avatar.png"
        );
        verify(correoClient).enviarBienvenida(any());
    }
}
