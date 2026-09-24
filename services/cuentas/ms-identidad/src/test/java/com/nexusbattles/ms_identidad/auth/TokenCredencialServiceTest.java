package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.exception.TokenInvalidoException;
import com.nexusbattles.ms_identidad.auth.model.TokenCredencial;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.TokenCredencialRepository;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoConfirmacionCuentaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoRecuperacionClaveRequest;
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

    @Mock
    private CorreoClient correoClient;

    /** La politica se prueba aparte (PasswordPolicyValidatorTest); aqui solo se afirma que se consulta. */
    @Mock
    private com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator passwordPolicyValidator;

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
        usuario.setApodo("ElGuerrero");
        usuario.setEstado("INACTIVO");
        return usuario;
    }

    @Test
    void debeGenerarYGuardarTokenConExpiracionCorrecta() {
        Usuario usuario = usuarioDePrueba();
        when(tokenCredencialRepository.findByToken(anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<TokenCredencial> captor = ArgumentCaptor.forClass(TokenCredencial.class);

        LocalDateTime antes = LocalDateTime.now();
        tokenCredencialService.generarYRegistrarToken(usuario, "ACTIVACION");
        LocalDateTime despues = LocalDateTime.now();

        verify(tokenCredencialRepository).save(captor.capture());
        TokenCredencial guardado = captor.getValue();

        assertEquals(usuario, guardado.getUsuario());
        assertEquals("ACTIVACION", guardado.getTipo());
        assertNotNull(guardado.getToken());
        assertFalse(guardado.isUsado());
        assertTrue(!guardado.getFechaExpiracion().isBefore(antes.plusHours(HORAS_EXPIRACION)));
        assertTrue(!guardado.getFechaExpiracion().isAfter(despues.plusHours(HORAS_EXPIRACION)));
    }

    /**
     * HALLAZGO: el codigo/token se manda tal cual al servicio de correo, y
     * ese contrato (contracts/openapi/correo.yaml) exige maxLength: 12.
     * Un UUID (32 caracteres, el valor original) violaba ese limite en
     * silencio -- el fallback de CorreoClient se activaba sin que nadie
     * lo notara, y ningun correo de activacion ni recuperacion llegaba a
     * enviarse nunca. Esta asercion es la que habria atrapado el bug
     * antes de production.
     */
    @Test
    void elTokenGeneradoNuncaExcedeElLimiteDelContratoDeCorreo() {
        Usuario usuario = usuarioDePrueba();
        when(tokenCredencialRepository.findByToken(anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<TokenCredencial> captor = ArgumentCaptor.forClass(TokenCredencial.class);

        tokenCredencialService.generarYRegistrarToken(usuario, "ACTIVACION");

        verify(tokenCredencialRepository).save(captor.capture());
        String token = captor.getValue().getToken();

        assertTrue(token.length() <= 12,
            "El token debe caber en CorreoConfirmacionCuentaRequest.codigo (maxLength: 12), midio: " + token.length());
    }

    /**
     * El alfabeto se eligio a proposito sin 0/O ni 1/I, para que un
     * usuario transcribiendo el codigo a mano desde un correo no los
     * confunda. Esta prueba fija ese contrato explicitamente.
     */
    @Test
    void elTokenGeneradoSoloUsaElAlfabetoSinCaracteresAmbiguos() {
        Usuario usuario = usuarioDePrueba();
        when(tokenCredencialRepository.findByToken(anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<TokenCredencial> captor = ArgumentCaptor.forClass(TokenCredencial.class);

        tokenCredencialService.generarYRegistrarToken(usuario, "ACTIVACION");

        verify(tokenCredencialRepository).save(captor.capture());
        String token = captor.getValue().getToken();

        assertTrue(token.matches("[A-HJ-NP-Z2-9]+"),
            "El token no debe contener 0, O, 1 ni I: " + token);
    }

    @Test
    void siYaExisteUnTokenIgualVuelveAGenerarUnoDistinto() {
        Usuario usuario = usuarioDePrueba();
        // Simula una colision: la primera consulta de unicidad dice que
        // ya existe, la segunda dice que no -- el metodo debe reintentar
        // en vez de guardar un token duplicado.
        when(tokenCredencialRepository.findByToken(anyString()))
            .thenReturn(Optional.of(mock(TokenCredencial.class)))
            .thenReturn(Optional.empty());

        tokenCredencialService.generarYRegistrarToken(usuario, "ACTIVACION");

        verify(tokenCredencialRepository, times(2)).findByToken(anyString());
        verify(tokenCredencialRepository, times(1)).save(any(TokenCredencial.class));
    }

    @Test
    void generarTokenDeActivacionEnviaCorreoDeConfirmacionCuenta() {
        Usuario usuario = usuarioDePrueba();
        when(tokenCredencialRepository.findByToken(anyString())).thenReturn(Optional.empty());

        tokenCredencialService.generarYRegistrarToken(usuario, "ACTIVACION");

        ArgumentCaptor<CorreoConfirmacionCuentaRequest> captor =
            ArgumentCaptor.forClass(CorreoConfirmacionCuentaRequest.class);
        verify(correoClient).enviarConfirmacionCuenta(captor.capture());
        verify(correoClient, never()).enviarRecuperacionClave(any());

        CorreoConfirmacionCuentaRequest correo = captor.getValue();
        assertEquals(usuario.getEmail(), correo.getEmail());
        assertEquals(usuario.getApodo(), correo.getApodo());
        assertEquals(HORAS_EXPIRACION * 60, correo.getMinutosVigencia());
    }

    @Test
    void generarTokenDeRestablecimientoEnviaCorreoDeRecuperacionClave() {
        Usuario usuario = usuarioDePrueba();
        when(tokenCredencialRepository.findByToken(anyString())).thenReturn(Optional.empty());

        tokenCredencialService.generarYRegistrarToken(usuario, "RESTABLECIMIENTO");

        ArgumentCaptor<CorreoRecuperacionClaveRequest> captor =
            ArgumentCaptor.forClass(CorreoRecuperacionClaveRequest.class);
        verify(correoClient).enviarRecuperacionClave(captor.capture());
        verify(correoClient, never()).enviarConfirmacionCuenta(any());

        CorreoRecuperacionClaveRequest correo = captor.getValue();
        assertEquals(usuario.getEmail(), correo.getEmail());
        assertEquals(HORAS_EXPIRACION * 60, correo.getMinutosVigencia());
    }

    // --- solicitarRestablecimiento ---

    @Test
    void solicitarRestablecimientoGeneraTokenSiElCorreoExiste() {
        Usuario usuario = usuarioDePrueba();
        when(usuarioRepository.buscarPorCorreo("cristian@test.com")).thenReturn(Optional.of(usuario));
        when(tokenCredencialRepository.findByToken(anyString())).thenReturn(Optional.empty());

        tokenCredencialService.solicitarRestablecimiento("cristian@test.com");

        ArgumentCaptor<TokenCredencial> captor = ArgumentCaptor.forClass(TokenCredencial.class);
        verify(tokenCredencialRepository).save(captor.capture());
        assertEquals("RESTABLECIMIENTO", captor.getValue().getTipo());
        verify(correoClient).enviarRecuperacionClave(any());
    }

    /**
     * Deliberado, por seguridad: si el correo no existe, no se genera
     * token ni se envia nada -- pero tampoco se lanza ninguna excepcion.
     * AuthController responde siempre el mismo mensaje generico sin
     * importar el resultado, para no permitir enumerar correos
     * registrados probando uno por uno.
     */
    @Test
    void solicitarRestablecimientoNoHaceNadaSiElCorreoNoExiste() {
        when(usuarioRepository.buscarPorCorreo("noexiste@test.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> tokenCredencialService.solicitarRestablecimiento("noexiste@test.com"));

        verify(tokenCredencialRepository, never()).save(any());
        verify(correoClient, never()).enviarRecuperacionClave(any());
        verify(correoClient, never()).enviarConfirmacionCuenta(any());
    }

    // --- canjearToken (sin cambios respecto al archivo original) ---

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
        verify(passwordPolicyValidator).validar("NuevaClave123!");
    }

    /**
     * HU-AUT-006 / hallazgo de #441: el canje aplica la politica de RF-AUT-002.
     * Y un rechazo por politica no quema el codigo: el usuario puede volver a
     * intentarlo con una contraseña valida y el mismo enlace.
     */
    @Test
    void unaContrasenaQueIncumpleLaPoliticaNoSeGuardaNiQuemaElCodigo() {
        Usuario usuario = usuarioDePrueba();
        usuario.setPassword("hash-anterior");
        TokenCredencial tokenCredencial = new TokenCredencial(
            usuario, "token-valido", "RESTABLECIMIENTO", LocalDateTime.now().plusHours(1)
        );
        when(tokenCredencialRepository.findByToken("token-valido"))
            .thenReturn(Optional.of(tokenCredencial));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("La contraseña no cumple la política: debe incluir al menos un número."))
            .when(passwordPolicyValidator).validar("sinNumeros!!");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> tokenCredencialService.canjearToken("token-valido", "sinNumeros!!"));

        assertTrue(error.getMessage().contains("un número"));
        assertEquals("hash-anterior", usuario.getPassword());
        assertFalse(tokenCredencial.isUsado());
        verify(usuarioRepository, org.mockito.Mockito.never()).save(usuario);
        verify(tokenCredencialRepository, org.mockito.Mockito.never()).save(tokenCredencial);
    }
}
