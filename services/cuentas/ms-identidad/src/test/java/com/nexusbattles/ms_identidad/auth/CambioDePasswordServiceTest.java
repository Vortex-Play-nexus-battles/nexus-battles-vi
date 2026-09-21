package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaClient;
import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoCambioClaveRequest;
import com.nexusbattles.ms_identidad.auth.dto.CambiarPasswordRequest;
import com.nexusbattles.ms_identidad.auth.dto.CambioDePasswordResponse;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException;
import com.nexusbattles.ms_identidad.auth.exception.CambioDePasswordRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.CambioDePasswordService;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-AUT-006 · cambiar mi contraseña. Bcrypt y JWT reales; solo se doblan
 * el repositorio y los clientes HTTP. Se afirma el estado del usuario
 * (hash, versión, intentos) y el contenido del token nuevo, no solo llamadas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CambioDePasswordService · HU-AUT-006")
class CambioDePasswordServiceTest {

    private static final String ACTUAL = "Actual-Segura-1!";
    private static final String NUEVA = "Nueva-Segura-2!";
    private static final Instant AHORA = Instant.parse("2026-09-21T15:00:00Z");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private IntentosFallidosService intentosFallidos;
    @Mock private CorreoClient correoClient;
    @Mock private AuditoriaClient auditoriaClient;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // rondas bajas: es una prueba
    private JwtService jwtService;
    private CambioDePasswordService servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");

        RolEntity jugador = new RolEntity();
        jugador.setNombre("JUGADOR");
        ana = new Usuario();
        ana.setId(7L);
        ana.setPublicId(UUID.randomUUID());
        ana.setApodo("ana");
        ana.setEmail("ana@nexus.test");
        ana.setRol(jugador);
        ana.setPassword(encoder.encode(ACTUAL));
        ana.setVersionToken(3);
        ana.setIntentosFallidos(2);

        servicio = new CambioDePasswordService(usuarioRepository, new PasswordPolicyValidator(),
                intentosFallidos, jwtService, correoClient, auditoriaClient, encoder,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static CambiarPasswordRequest peticion(String actual, String nueva, String confirmacion) {
        CambiarPasswordRequest datos = new CambiarPasswordRequest();
        datos.setPasswordActual(actual);
        datos.setNuevaPassword(nueva);
        datos.setConfirmacion(confirmacion);
        return datos;
    }

    private CambioDePasswordRechazadoException rechazo(CambiarPasswordRequest datos) {
        when(usuarioRepository.findByApodo("ana")).thenReturn(Optional.of(ana));
        return assertThrows(CambioDePasswordRechazadoException.class,
                () -> servicio.cambiar("ana", datos, "10.0.0.1"));
    }

    @Test
    @DisplayName("CA-01: con la actual correcta y una nueva valida, se guarda irreversible, se avisa por correo y se audita")
    void cambioCorrecto() {
        when(usuarioRepository.findByApodo("ana")).thenReturn(Optional.of(ana));

        CambioDePasswordResponse respuesta = servicio.cambiar("ana", peticion(ACTUAL, NUEVA, NUEVA), "10.0.0.1");

        ArgumentCaptor<CorreoCambioClaveRequest> correo = ArgumentCaptor.forClass(CorreoCambioClaveRequest.class);
        verify(correoClient).enviarCambioClave(correo.capture());
        assertAll(
                () -> assertTrue(encoder.matches(NUEVA, ana.getPassword()), "la nueva queda con bcrypt"),
                () -> assertFalse(encoder.matches(ACTUAL, ana.getPassword()), "la anterior deja de valer"),
                () -> assertFalse(ana.getPassword().contains(NUEVA), "nunca en claro"),
                () -> assertEquals(0, ana.getIntentosFallidos(), "un cambio correcto limpia los intentos"),
                () -> assertEquals("ana@nexus.test", correo.getValue().getEmail()),
                () -> assertEquals("10.0.0.1", correo.getValue().getIp()),
                () -> assertTrue(correo.getValue().getFechaHora().startsWith("2026-09-21T15:00")),
                () -> assertTrue(respuesta.mensaje().contains("actualizada")));
        verify(usuarioRepository).save(ana);
        verify(auditoriaClient).registrar(eq("ACTUALIZACION"), eq("ana"), eq("ana"), isNull(), isNull(),
                anyString(), eq("10.0.0.1"));
        verify(intentosFallidos, never()).registrarIntentoFallido(anyLong());
    }

    @Test
    @DisplayName("CA-04: sube la version de token (caducan las demas sesiones) y esta sesion recibe un token de la version nueva")
    void invalidaLasDemasSesionesYRenuevaEsta() {
        when(usuarioRepository.findByApodo("ana")).thenReturn(Optional.of(ana));
        String tokenViejo = jwtService.generarToken("ana", "JUGADOR", 3, ana.getPublicId());

        CambioDePasswordResponse respuesta = servicio.cambiar("ana", peticion(ACTUAL, NUEVA, NUEVA), null);

        Claims nuevas = jwtService.validarYObtenerClaims(respuesta.token());
        Claims viejas = jwtService.validarYObtenerClaims(tokenViejo);
        assertAll(
                () -> assertEquals(4, ana.getVersionToken()),
                () -> assertEquals("ana", nuevas.getSubject()),
                () -> assertTrue(jwtService.esVersionVigente(nuevas, ana.getVersionToken()), "el token nuevo sigue valiendo"),
                () -> assertFalse(jwtService.esVersionVigente(viejas, ana.getVersionToken()), "el de antes ya no"));
    }

    @Test
    @DisplayName("CA-02: con la actual incorrecta no cambia nada, cuenta como intento fallido y no revela mas")
    void actualIncorrecta() {
        String hashAntes = ana.getPassword();

        CambioDePasswordRechazadoException error = rechazo(peticion("otra-cosa", NUEVA, NUEVA));

        assertAll(
                () -> assertEquals(Motivo.ACTUAL_INCORRECTA, error.getMotivo()),
                () -> assertEquals(422, error.getMotivo().estado()),
                () -> assertEquals("La contraseña actual es incorrecta.", error.getMessage()),
                () -> assertEquals(hashAntes, ana.getPassword()),
                () -> assertEquals(3, ana.getVersionToken()));
        verify(intentosFallidos).registrarIntentoFallido(7L);
        verify(usuarioRepository, never()).save(any());
        verify(correoClient, never()).enviarCambioClave(any());
    }

    @Test
    @DisplayName("CA-02: con la actual incorrecta NO se dice nada sobre la nueva, aunque tambien este mal")
    void laActualSeComprebaAntesQueLaNueva() {
        CambioDePasswordRechazadoException error = rechazo(peticion("otra-cosa", "corta", "corta"));

        assertEquals(Motivo.ACTUAL_INCORRECTA, error.getMotivo());
    }

    @Test
    @DisplayName("CA-03: si la nueva incumple la politica, el rechazo dice QUE regla falla")
    void politica() {
        CambioDePasswordRechazadoException error = rechazo(peticion(ACTUAL, "solominusculas", "solominusculas"));

        assertAll(
                () -> assertEquals(Motivo.POLITICA, error.getMotivo()),
                () -> assertTrue(error.getMessage().contains("mayúscula"), error.getMessage()),
                () -> assertTrue(error.getMessage().contains("número"), error.getMessage()),
                () -> assertTrue(error.getMessage().contains("símbolo"), error.getMessage()),
                () -> assertFalse(error.getMessage().contains("minúscula"), "la minuscula si la cumple"));
        verify(usuarioRepository, never()).save(any());
        verify(intentosFallidos, never()).registrarIntentoFallido(anyLong());
    }

    @Test
    @DisplayName("CA-03: la nueva no puede ser igual a la anterior")
    void repetida() {
        CambioDePasswordRechazadoException error = rechazo(peticion(ACTUAL, ACTUAL, ACTUAL));

        assertEquals(Motivo.REPETIDA, error.getMotivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("la confirmacion tiene que coincidir con la nueva")
    void confirmacion() {
        CambioDePasswordRechazadoException error = rechazo(peticion(ACTUAL, NUEVA, NUEVA + "x"));

        assertEquals(Motivo.CONFIRMACION, error.getMotivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF-AUT-009: con la cuenta bloqueada por intentos ni se compara la actual")
    void cuentaBloqueada() {
        ana.setBloqueadoHasta(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusMinutes(10));

        CambioDePasswordRechazadoException error = rechazo(peticion(ACTUAL, NUEVA, NUEVA));

        assertEquals(Motivo.CUENTA_BLOQUEADA, error.getMotivo());
        assertEquals(423, error.getMotivo().estado());
        verify(intentosFallidos, never()).registrarIntentoFallido(anyLong());
    }

    @Test
    @DisplayName("un bloqueo ya vencido no estorba")
    void bloqueoVencido() {
        ana.setBloqueadoHasta(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC).minusMinutes(1));
        when(usuarioRepository.findByApodo("ana")).thenReturn(Optional.of(ana));

        servicio.cambiar("ana", peticion(ACTUAL, NUEVA, NUEVA), "10.0.0.1");

        assertTrue(encoder.matches(NUEVA, ana.getPassword()));
    }

    @Test
    @DisplayName("si la bitacora de auditoria no responde, el cambio se mantiene: es el usuario con SU contraseña")
    void auditoriaCaidaNoDeshaceElCambio() {
        when(usuarioRepository.findByApodo("ana")).thenReturn(Optional.of(ana));
        doThrow(new IllegalStateException("auditoria caida")).when(auditoriaClient)
                .registrar(any(), any(), any(), any(), any(), any(), any());

        CambioDePasswordResponse respuesta = servicio.cambiar("ana", peticion(ACTUAL, NUEVA, NUEVA), "10.0.0.1");

        assertTrue(encoder.matches(NUEVA, ana.getPassword()));
        assertTrue(respuesta.token() != null && !respuesta.token().isBlank());
    }
}
