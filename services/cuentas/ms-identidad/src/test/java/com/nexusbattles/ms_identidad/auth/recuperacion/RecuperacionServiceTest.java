package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion.Resultado;
import com.nexusbattles.ms_identidad.auth.codigos.ContrasenaRestablecida;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import com.nexusbattles.ms_identidad.auth.codigos.IgualadorDeTiempo;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Recuperacion endurecida (B1, 7.1.1): solicitud neutra, preguntas y canje")
class RecuperacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID UID = UUID.fromString("7e7e7e7e-1111-4222-8333-444444444444");
    private static final String CORREO = "ada@upb.edu.co";
    private static final String NUEVA = "Nueva.Clave-9";

    private final PasswordEncoder cifrador = new BCryptPasswordEncoder(4);
    private UsuarioRepository usuarios;
    private CodigosDeCorreo codigos;
    private IgualadorDeTiempo igualador;
    private PreguntasDeSeguridadService preguntas;
    private ApplicationEventPublisher eventos;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        codigos = mock(CodigosDeCorreo.class);
        igualador = mock(IgualadorDeTiempo.class);
        preguntas = mock(PreguntasDeSeguridadService.class);
        eventos = mock(ApplicationEventPublisher.class);
        cuenta = new Usuario();
        cuenta.setId(4L);
        cuenta.setPublicId(UID);
        cuenta.setApodo("ada");
        cuenta.setEmail(CORREO);
        cuenta.setEstado(EstadoCuenta.ACTIVO);
        cuenta.setPassword("vieja");
        cuenta.setVersionToken(2);
        cuenta.setIntentosFallidos(3);
        cuenta.setBloqueadoHasta(HOY.plusMinutes(10));
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(cuenta));
        when(usuarios.bloquear(4L)).thenReturn(Optional.of(cuenta));
    }

    private RecuperacionService servicio(boolean obligatorias) {
        return new RecuperacionService(usuarios, codigos, igualador, preguntas, new PasswordPolicyValidator(), eventos,
                obligatorias, cifrador, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void codigo(Resultado resultado, TipoCodigo tipo) {
        when(codigos.comprobar(4L, TipoCodigo.RESTABLECIMIENTO, "K7QX2M9P"))
                .thenReturn(new Comprobacion(resultado, resultado == Resultado.VALIDO ? 21L : null, tipo));
    }

    // ---------------------------------------------------------------- solicitar

    @Test
    @DisplayName("cuenta activa dentro del limite: codigo nuevo (que anula los anteriores)")
    void solicita() {
        when(codigos.admiteOtroEnvio(4L, TipoCodigo.RESTABLECIMIENTO)).thenReturn(true);

        servicio(false).solicitar(CORREO);

        verify(codigos).emitir(cuenta, TipoCodigo.RESTABLECIMIENTO);
        verify(igualador, never()).resumir();
    }

    @Test
    @DisplayName("sin cuenta, pendiente, baneada, suspendida o pasado el limite: nada, y el mismo trabajo")
    void solicitudNeutra() {
        RecuperacionService servicio = servicio(false);
        when(usuarios.buscarPorCorreo("nadie@upb.edu.co")).thenReturn(Optional.empty());
        servicio.solicitar("nadie@upb.edu.co");

        for (String estado : List.of(EstadoCuenta.PENDIENTE_VERIFICACION, EstadoCuenta.BANEADO, EstadoCuenta.INACTIVO)) {
            cuenta.setEstado(estado);
            servicio.solicitar(CORREO);
        }
        cuenta.setEstado(EstadoCuenta.SUSPENDIDO);
        cuenta.setSuspendidoHasta(HOY.plusHours(1));
        servicio.solicitar(CORREO);

        cuenta.setEstado(EstadoCuenta.ACTIVO);
        when(codigos.admiteOtroEnvio(4L, TipoCodigo.RESTABLECIMIENTO)).thenReturn(false);
        servicio.solicitar(CORREO);

        verify(codigos, never()).emitir(any(), any());
        verify(igualador, times(6)).resumir();
    }

    @Test
    @DisplayName("una suspension ya vencida no impide recuperar la cuenta")
    void suspensionVencida() {
        cuenta.setEstado("SUSPENDIDA");
        cuenta.setSuspendidoHasta(HOY.minusMinutes(1));
        when(codigos.admiteOtroEnvio(4L, TipoCodigo.RESTABLECIMIENTO)).thenReturn(true);

        servicio(false).solicitar(CORREO);

        verify(codigos).emitir(cuenta, TipoCodigo.RESTABLECIMIENTO);
    }

    // ---------------------------------------------------------------- preguntas

    @Test
    @DisplayName("preguntas: con el codigo, las de la cuenta; de una activacion, ninguna")
    void preguntas() {
        PreguntasDeRecuperacion configuradas = new PreguntasDeRecuperacion(true,
                List.of(new PreguntasDeRecuperacion.Pregunta(UUID.randomUUID(), "¿Primera mascota?")));
        when(preguntas.deLaCuenta(4L)).thenReturn(configuradas);

        codigo(Resultado.VALIDO, TipoCodigo.RESTABLECIMIENTO);
        assertThat(servicio(false).preguntas(CORREO, "K7QX2M9P")).isEqualTo(configuradas);

        codigo(Resultado.VALIDO, TipoCodigo.ACTIVACION);
        assertThat(servicio(false).preguntas(CORREO, "K7QX2M9P")).isEqualTo(PreguntasDeRecuperacion.ninguna());
    }

    @Test
    @DisplayName("preguntas: sin cuenta o con el codigo mal, 400 (o 429), sin enseñar nada")
    void preguntasSinCodigo() {
        when(usuarios.buscarPorCorreo("nadie@upb.edu.co")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio(false).preguntas("nadie@upb.edu.co", "K7QX2M9P"))
                .isInstanceOf(CodigoInvalidoException.class);
        verify(igualador).comparar("K7QX2M9P");

        codigo(Resultado.DEMASIADOS_INTENTOS, null);
        assertThatThrownBy(() -> servicio(false).preguntas(CORREO, "K7QX2M9P"))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(preguntas, never()).deLaCuenta(anyLong());
    }

    // ---------------------------------------------------------------- confirmar

    @Test
    @DisplayName("canje sin preguntas configuradas: contrasena nueva, sesiones cerradas, desbloqueo y aviso")
    void canjeSinPreguntas() {
        codigo(Resultado.VALIDO, TipoCodigo.RESTABLECIMIENTO);
        when(codigos.marcarUsado(21L)).thenReturn(true);

        servicio(false).confirmar(CORREO, "K7QX2M9P", null, NUEVA, "10.0.0.7");

        assertThat(cifrador.matches(NUEVA, cuenta.getPassword())).isTrue();
        assertThat(cuenta.getVersionToken()).as("cierra las sesiones abiertas").isEqualTo(3);
        assertThat(cuenta.getIntentosFallidos()).isZero();
        assertThat(cuenta.getBloqueadoHasta()).isNull();
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.ACTIVO);
        verify(usuarios).save(cuenta);
        verify(eventos).publishEvent(new ContrasenaRestablecida(21L, TipoCodigo.RESTABLECIMIENTO, UID, 4L, CORREO,
                "ada", "10.0.0.7"));
    }

    @Test
    @DisplayName("activacion de una cuenta administrativa: el mismo canje la deja ACTIVA y nunca pide preguntas")
    void activacion() {
        cuenta.setEstado(EstadoCuenta.INACTIVO);
        codigo(Resultado.VALIDO, TipoCodigo.ACTIVACION);
        when(codigos.marcarUsado(21L)).thenReturn(true);

        servicio(true).confirmar(CORREO, "K7QX2M9P", null, NUEVA, null);

        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.ACTIVO);
        verifyNoInteractions(preguntas);
    }

    @Test
    @DisplayName("contrasena fuera de politica: 422 con la regla, sin quemar el codigo ni contar intento")
    void politica() {
        codigo(Resultado.VALIDO, TipoCodigo.RESTABLECIMIENTO);

        assertThatThrownBy(() -> servicio(false).confirmar(CORREO, "K7QX2M9P", null, "cortita", null))
                .isInstanceOf(RecuperacionRechazadaException.class)
                .hasMessageContaining("más de 8 caracteres")
                .extracting(e -> ((RecuperacionRechazadaException) e).getMotivo())
                .isEqualTo(RecuperacionRechazadaException.Motivo.POLITICA);
        verify(codigos, never()).marcarUsado(anyLong());
        verify(codigos, never()).registrarFallo(anyLong());
        assertThat(cuenta.getPassword()).isEqualTo("vieja");
    }

    @Test
    @DisplayName("con preguntas configuradas: respuestas bien -> canje; mal -> 422 que cuenta intento; al quinto, 429")
    void conPreguntas() {
        codigo(Resultado.VALIDO, TipoCodigo.RESTABLECIMIENTO);
        when(preguntas.tieneConfiguradas(4L)).thenReturn(true);
        List<RespuestaDeSeguridad> respuestas = List.of(new RespuestaDeSeguridad(UUID.randomUUID(), "x"));

        when(preguntas.respuestasCorrectas(4L, respuestas)).thenReturn(false);
        when(codigos.registrarFallo(21L)).thenReturn(new Comprobacion(Resultado.INVALIDO, null, null));
        assertThatThrownBy(() -> servicio(false).confirmar(CORREO, "K7QX2M9P", respuestas, NUEVA, null))
                .isInstanceOf(RecuperacionRechazadaException.class)
                .hasMessage("Las respuestas de seguridad no coinciden.");

        when(codigos.registrarFallo(21L)).thenReturn(new Comprobacion(Resultado.DEMASIADOS_INTENTOS, null, null));
        assertThatThrownBy(() -> servicio(false).confirmar(CORREO, "K7QX2M9P", respuestas, NUEVA, null))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(codigos, never()).marcarUsado(anyLong());

        when(preguntas.respuestasCorrectas(4L, respuestas)).thenReturn(true);
        when(codigos.marcarUsado(21L)).thenReturn(true);
        servicio(false).confirmar(CORREO, "K7QX2M9P", respuestas, NUEVA, null);
        assertThat(cifrador.matches(NUEVA, cuenta.getPassword())).isTrue();
    }

    @Test
    @DisplayName("preguntas obligatorias (decision del PO) y ninguna configurada: no hay autoservicio")
    void obligatoriasSinConfigurar() {
        codigo(Resultado.VALIDO, TipoCodigo.RESTABLECIMIENTO);
        when(preguntas.tieneConfiguradas(4L)).thenReturn(false);
        when(preguntas.respuestasCorrectas(4L, null)).thenReturn(false);
        when(codigos.registrarFallo(21L)).thenReturn(new Comprobacion(Resultado.INVALIDO, null, null));

        assertThatThrownBy(() -> servicio(true).confirmar(CORREO, "K7QX2M9P", null, NUEVA, null))
                .isInstanceOf(RecuperacionRechazadaException.class)
                .hasMessageContaining("no tiene preguntas de seguridad configuradas");
    }

    @Test
    @DisplayName("sin cuenta, codigo malo o canjeado por otro a la vez: 400, y nada cambia")
    void canjeInvalido() {
        when(usuarios.buscarPorCorreo("nadie@upb.edu.co")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio(false).confirmar("nadie@upb.edu.co", "K7QX2M9P", null, NUEVA, null))
                .isInstanceOf(CodigoInvalidoException.class);

        codigo(Resultado.INVALIDO, null);
        assertThatThrownBy(() -> servicio(false).confirmar(CORREO, "K7QX2M9P", null, NUEVA, null))
                .isInstanceOf(CodigoInvalidoException.class);

        codigo(Resultado.VALIDO, TipoCodigo.RESTABLECIMIENTO);
        when(codigos.marcarUsado(21L)).thenReturn(false);
        assertThatThrownBy(() -> servicio(false).confirmar(CORREO, "K7QX2M9P", null, NUEVA, null))
                .isInstanceOf(CodigoInvalidoException.class);

        assertThat(cuenta.getPassword()).isEqualTo("vieja");
        verify(usuarios, never()).save(any());
        verifyNoInteractions(eventos);
    }
}
