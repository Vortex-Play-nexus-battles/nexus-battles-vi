package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.exception.TokenInvalidoException;
import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.TokenCredencialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCredencialServiceTest {

    @Mock
    private TokenCredencialRepository tokenCredencialRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private TokenCredencialService tokenCredencialService;

    private static final int HORAS_EXPIRACION = 24;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenCredencialService, "horasExpiracion", HORAS_EXPIRACION);
    }

    private Usuario usuarioDePrueba() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("cristian@test.com");
        usuario.setEstado("INACTIVO");
        return usuario;
    }

    @Test
    void debeGenerarYGuardarTokenConExpiracionCorrecta() {

        Usuario usuario = usuarioDePrueba();
        ArgumentCaptor<TokenCredencial> captor = ArgumentCaptor.forClass(TokenCredencial.class);

        LocalDateTime antes = LocalDateTime.now();
        tokenCredencialService.generarYRegistrarToken(usuario, "ACTIVACION");
        LocalDateTime despues = LocalDateTime.now();

        verify(tokenCredencialRepository).save(captor.capture());
        TokenCredencial guardado = captor.getValue();

        assertEquals(usuario, guardado.getUsuario());
        assertEquals("ACTIVACION", guardado.getTipo());
        assertNotNull(guardado.getToken());
        assertFalse(guardado.getToken().contains("-"));
        assertFalse(guardado.isUsado());
        assertTrue(!guardado.getFechaExpiracion().isBefore(antes.plusHours(HORAS_EXPIRACION)));
        assertTrue(!guardado.getFechaExpiracion().isAfter(despues.plusHours(HORAS_EXPIRACION)));
    }

    @Test
    void debeLanzarExcepcionSiTokenNoExiste() {

        when(tokenCredencialRepository.findByToken("token-inexistente"))
            .thenReturn(Optional.empty());

        TokenInvalidoException exception = assertThrows(
            TokenInvalidoException.class,
            () -> tokenCredencialService.canjearToken("token-inexistente", "NuevaClave123!")
        );

        assertEquals("El enlace no es válido.", exception.getMessage());
    }

    @Test
    void debeLanzarExcepcionSiTokenYaFueUsado() {

        TokenCredencial tokenCredencial = new TokenCredencial(
            usuarioDePrueba(), "token-usado", "ACTIVACION", LocalDateTime.now().plusHours(1)
        );
        tokenCredencial.setUsado(true);
        when(tokenCredencialRepository.findByToken("token-usado"))
            .thenReturn(Optional.of(tokenCredencial));

        TokenInvalidoException exception = assertThrows(
            TokenInvalidoException.class,
            () -> tokenCredencialService.canjearToken("token-usado", "NuevaClave123!")
        );

        assertEquals("Este enlace ya fue utilizado.", exception.getMessage());
    }

    @Test
    void debeLanzarExcepcionSiTokenExpiro() {

        TokenCredencial tokenCredencial = new TokenCredencial(
            usuarioDePrueba(), "token-expirado", "ACTIVACION", LocalDateTime.now().minusMinutes(1)
        );
        when(tokenCredencialRepository.findByToken("token-expirado"))
            .thenReturn(Optional.of(tokenCredencial));

        TokenInvalidoException exception = assertThrows(
            TokenInvalidoException.class,
            () -> tokenCredencialService.canjearToken("token-expirado", "NuevaClave123!")
        );

        assertEquals("Este enlace ha expirado.", exception.getMessage());
    }

    @Test
    void debeCanjearTokenDeActivacionYCambiarEstadoAActivo() {

        Usuario usuario = usuarioDePrueba();
        TokenCredencial tokenCredencial = new TokenCredencial(
            usuario, "token-valido", "ACTIVACION", LocalDateTime.now().plusHours(1)
        );
        when(tokenCredencialRepository.findByToken("token-valido"))
            .thenReturn(Optional.of(tokenCredencial));

        tokenCredencialService.canjearToken("token-valido", "NuevaClave123!");

        assertEquals("ACTIVO", usuario.getEstado());
        assertTrue(new BCryptPasswordEncoder().matches("NuevaClave123!", usuario.getPassword()));
        assertTrue(tokenCredencial.isUsado());
        verify(usuarioRepository).save(usuario);
        verify(tokenCredencialRepository).save(tokenCredencial);
    }

    @Test
    void debeCanjearTokenDeRestablecimientoSinCambiarEstado() {

        Usuario usuario = usuarioDePrueba();
        usuario.setEstado("ACTIVO");
        TokenCredencial tokenCredencial = new TokenCredencial(
            usuario, "token-valido", "RESTABLECIMIENTO", LocalDateTime.now().plusHours(1)
        );
        when(tokenCredencialRepository.findByToken("token-valido"))
            .thenReturn(Optional.of(tokenCredencial));

        tokenCredencialService.canjearToken("token-valido", "NuevaClave123!");

        assertEquals("ACTIVO", usuario.getEstado());
        assertTrue(new BCryptPasswordEncoder().matches("NuevaClave123!", usuario.getPassword()));
        assertTrue(tokenCredencial.isUsado());
    }
}
