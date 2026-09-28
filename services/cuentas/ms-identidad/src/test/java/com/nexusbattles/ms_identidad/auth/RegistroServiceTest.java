package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.CuentaRegistrada;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.dto.RegistroRequest;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.RegistroRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import com.nexusbattles.ms_identidad.auth.service.RegistroService;
import com.nexusbattles.ms_identidad.auth.validation.ApodoBlacklistValidator;
import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.perfiles.service.PerfilUsuarioService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.service.RolService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    private static final UUID UID = UUID.fromString("11111111-2222-3333-4444-555555555555");

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
    private AvatarStorageService avatarStorageService;

    @Mock
    private CodigosDeCorreo codigos;

    @Mock
    private ApplicationEventPublisher eventos;

    @InjectMocks
    private RegistroService registroService;

    private RegistroRequest datosValidos() {
        RegistroRequest datos = new RegistroRequest();
        datos.setNombres("Cristian");
        datos.setApellidos("Chaparro");
        datos.setEmail("cristian@test.com");
        datos.setPassword("MiClave123!");
        datos.setApodo("cristianc");
        // Sin archivo de avatar en las pruebas unitarias: sigue siendo
        // opcional, y AvatarStorageService.guardarAvatar(null) ya maneja
        // ese caso devolviendo null en el código real.
        return datos;
    }

    /** Lo que hace la base de datos al guardar: el uid lo pone @PrePersist. */
    private void guardadoConUid() {
        when(rolService.obtenerRolPorNombre("JUGADOR")).thenReturn(new RolEntity());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            usuario.setPublicId(UID);
            return usuario;
        });
    }

    @Test
    void debeLanzarExcepcionSiCorreoYaExiste() {

        when(usuarioRepository.existsByEmailIgnoreCase("cristian@test.com")).thenReturn(true);

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("El correo electrónico ya está registrado.", exception.getMessage());
        assertEquals(Motivo.CORREO_EN_USO, exception.getMotivo());
        assertEquals("email", exception.getCampo());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(codigos, eventos);
    }

    @Test
    void debeLanzarExcepcionSiApodoYaExiste() {

        when(usuarioRepository.existsByEmailIgnoreCase("cristian@test.com")).thenReturn(false);
        when(usuarioRepository.existsByApodoIgnoreCase("cristianc")).thenReturn(true);

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("El apodo ya está en uso.", exception.getMessage());
        assertEquals(Motivo.APODO_EN_USO, exception.getMotivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("R17: el correo repetido se detecta aunque cambien las mayusculas o sobren espacios")
    void correoRepetidoConOtrasMayusculas() {
        RegistroRequest datos = datosValidos();
        datos.setEmail("  Cristian@TEST.com ");
        when(usuarioRepository.existsByEmailIgnoreCase("cristian@test.com")).thenReturn(true);

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class, () -> registroService.registrarUsuario(datos));

        assertEquals(Motivo.CORREO_EN_USO, exception.getMotivo());
    }

    @Test
    void debePropagarRechazoDeListaNegra() {

        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        doThrow(new IllegalArgumentException("El apodo contiene términos prohibidos."))
            .when(apodoBlacklistValidator).validar(anyString());

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("El apodo contiene términos prohibidos.", exception.getMessage());
        assertEquals(Motivo.APODO_NO_PERMITIDO, exception.getMotivo());
        verify(passwordPolicyValidator, never()).validar(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    // B13, la prueba del profesor en DEV: «SpiderMan» ya existia (se creo
    // antes de la lista negra) y el registro respondia «El apodo ya está en
    // uso». Quien prueba la lista negra concluia que no funcionaba. Un apodo
    // prohibido es prohibido exista o no una cuenta con el: la lista negra va
    // antes que la unicidad, y de paso no revela que la cuenta existe.
    @Test
    @DisplayName("B13: un apodo prohibido que ya existe responde apodo-no-permitido, no apodo-en-uso")
    void apodoProhibidoQueYaExiste() {
        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        doThrow(new IllegalArgumentException("El apodo no está permitido."))
            .when(apodoBlacklistValidator).validar("cristianc");

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class, () -> registroService.registrarUsuario(datosValidos()));

        assertEquals(Motivo.APODO_NO_PERMITIDO, exception.getMotivo());
        verify(usuarioRepository, never()).existsByApodoIgnoreCase(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void debePropagarRechazoDePoliticaDeContrasena() {

        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(usuarioRepository.existsByApodoIgnoreCase(anyString())).thenReturn(false);
        doThrow(new IllegalArgumentException("La contraseña no cumple la política."))
            .when(passwordPolicyValidator).validar(anyString());

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class,
            () -> registroService.registrarUsuario(datosValidos())
        );

        assertEquals("La contraseña no cumple la política.", exception.getMessage());
        assertEquals(Motivo.CONTRASENA_DEBIL, exception.getMotivo());
        assertEquals("password", exception.getCampo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("R17: un apodo o un correo mas largos que su columna se rechazan con motivo, no con un 500")
    void longitudesDeColumna() {
        RegistroRequest apodoLargo = datosValidos();
        apodoLargo.setApodo("a".repeat(51));
        RegistroRechazadoException porApodo = assertThrows(
            RegistroRechazadoException.class, () -> registroService.registrarUsuario(apodoLargo));
        assertEquals(Motivo.DATOS_INVALIDOS, porApodo.getMotivo());
        assertEquals("apodo", porApodo.getCampo());
        assertTrue(porApodo.getMessage().contains("50"));

        RegistroRequest correoLargo = datosValidos();
        correoLargo.setEmail("a".repeat(95) + "@x.com");
        RegistroRechazadoException porCorreo = assertThrows(
            RegistroRechazadoException.class, () -> registroService.registrarUsuario(correoLargo));
        assertEquals("email", porCorreo.getCampo());

        RegistroRequest nombreLargo = datosValidos();
        nombreLargo.setNombres("n".repeat(256));
        assertEquals("nombres", assertThrows(RegistroRechazadoException.class,
            () -> registroService.registrarUsuario(nombreLargo)).getCampo());

        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("R17: un avatar invalido es un rechazo del formulario, no un fallo del servidor")
    void avatarInvalido() {
        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(usuarioRepository.existsByApodoIgnoreCase(anyString())).thenReturn(false);
        guardadoConUid();
        when(avatarStorageService.guardarAvatar(null))
            .thenThrow(new IllegalArgumentException("Formato de imagen no permitido."));

        RegistroRechazadoException exception = assertThrows(
            RegistroRechazadoException.class, () -> registroService.registrarUsuario(datosValidos()));

        assertEquals(Motivo.AVATAR_INVALIDO, exception.getMotivo());
        verifyNoInteractions(codigos, eventos);
    }

    @Test
    void debeRegistrarUsuarioCorrectamente() {

        RegistroRequest datos = datosValidos();

        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(usuarioRepository.existsByApodoIgnoreCase(anyString())).thenReturn(false);
        guardadoConUid();
        when(avatarStorageService.guardarAvatar(null)).thenReturn(null);

        Usuario resultado = registroService.registrarUsuario(datos);

        assertEquals("cristianc", resultado.getApodo());
        assertEquals("cristian@test.com", resultado.getEmail());
        assertEquals("PENDIENTE_VERIFICACION", resultado.getEstado(), "B1: nace sin verificar");
        assertNotEquals("MiClave123!", resultado.getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("MiClave123!", resultado.getPassword()));

        verify(perfilUsuarioService).crearPerfil(
            resultado, "Cristian", "Chaparro", null
        );
        verify(codigos).emitir(resultado, TipoCodigo.VERIFICACION);
        verify(eventos).publishEvent(new CuentaRegistrada(UID, "cristianc", null));
    }

    @Test
    @DisplayName("B2: si la lista negra no responde, la excepcion sube tal cual (503) y no se guarda nada")
    void listaNegraCaida() {
        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        doThrow(new ModeracionNoDisponibleException("sin respuesta"))
            .when(apodoBlacklistValidator).validar(anyString());

        assertThrows(ModeracionNoDisponibleException.class,
            () -> registroService.registrarUsuario(datosValidos(), "10.0.0.1"));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(codigos, eventos);
    }

    @Test
    @DisplayName("B1: la cuenta nace pendiente, con su codigo de verificacion y SIN alta del jugador")
    void naceSinAltaYConCodigo() {
        RegistroRequest datos = datosValidos();
        datos.setEmail("  Profe@UPB.edu.CO ");
        datos.setApodo("  profe  ");
        when(usuarioRepository.existsByEmailIgnoreCase("profe@upb.edu.co")).thenReturn(false);
        when(usuarioRepository.existsByApodoIgnoreCase("profe")).thenReturn(false);
        guardadoConUid();

        Usuario resultado = registroService.registrarUsuario(datos, "10.1.2.3");

        assertEquals("profe@upb.edu.co", resultado.getEmail(), "se guarda como lo encontrara el login");
        assertEquals("profe", resultado.getApodo());
        assertEquals("PENDIENTE_VERIFICACION", resultado.getEstado());
        verify(codigos).emitir(resultado, TipoCodigo.VERIFICACION);
        ArgumentCaptor<CuentaRegistrada> evento = ArgumentCaptor.forClass(CuentaRegistrada.class);
        verify(eventos).publishEvent(evento.capture());
        assertEquals(new CuentaRegistrada(UID, "profe", "10.1.2.3"), evento.getValue());
    }
}
