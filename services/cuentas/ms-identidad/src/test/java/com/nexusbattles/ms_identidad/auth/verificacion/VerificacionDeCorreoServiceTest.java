package com.nexusbattles.ms_identidad.auth.verificacion;

import com.nexusbattles.ms_identidad.auth.codigos.CodigoInvalidoException;
import com.nexusbattles.ms_identidad.auth.codigos.CodigosDeCorreo;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion;
import com.nexusbattles.ms_identidad.auth.codigos.Comprobacion.Resultado;
import com.nexusbattles.ms_identidad.auth.codigos.CorreoVerificado;
import com.nexusbattles.ms_identidad.auth.codigos.DemasiadosIntentosException;
import com.nexusbattles.ms_identidad.auth.codigos.IgualadorDeTiempo;
import com.nexusbattles.ms_identidad.auth.codigos.TipoCodigo;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.onboarding.service.OnboardingService;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Verificacion del correo (B1): confirmacion atomica y reenvio neutro")
class VerificacionDeCorreoServiceTest {

    private static final UUID UID = UUID.fromString("5a5a5a5a-1111-4222-8333-444444444444");
    private static final String CORREO = "ada@upb.edu.co";

    private UsuarioRepository usuarios;
    private PerfilUsuarioRepository perfiles;
    private CodigosDeCorreo codigos;
    private IgualadorDeTiempo igualador;
    private OnboardingService onboarding;
    private ApplicationEventPublisher eventos;
    private VerificacionDeCorreoService servicio;
    private Usuario pendiente;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        perfiles = mock(PerfilUsuarioRepository.class);
        codigos = mock(CodigosDeCorreo.class);
        igualador = mock(IgualadorDeTiempo.class);
        onboarding = mock(OnboardingService.class);
        eventos = mock(ApplicationEventPublisher.class);
        servicio = new VerificacionDeCorreoService(usuarios, perfiles, codigos, igualador, onboarding, eventos);

        pendiente = new Usuario();
        pendiente.setId(3L);
        pendiente.setPublicId(UID);
        pendiente.setApodo("ada");
        pendiente.setEmail(CORREO);
        pendiente.setEstado(EstadoCuenta.PENDIENTE_VERIFICACION);
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(pendiente));
    }

    @AfterEach
    void cerrarTraza() {
        Traza.cerrar();
    }

    private void codigo(Resultado resultado) {
        when(codigos.comprobar(3L, TipoCodigo.VERIFICACION, "K7QX2M9P"))
                .thenReturn(new Comprobacion(resultado, resultado == Resultado.VALIDO ? 11L : null,
                        resultado == Resultado.VALIDO ? TipoCodigo.VERIFICACION : null));
    }

    @Test
    @DisplayName("codigo correcto: codigo usado, PENDIENTE -> ACTIVO, alta del jugador, auditoria y bienvenida")
    void confirma() {
        codigo(Resultado.VALIDO);
        when(codigos.marcarUsado(11L)).thenReturn(true);
        when(usuarios.activarSiPendiente(3L, EstadoCuenta.PENDIENTE_VERIFICACION, EstadoCuenta.ACTIVO)).thenReturn(1);
        PerfilUsuario perfil = new PerfilUsuario();
        perfil.setNombres("Ada");
        perfil.setApellidos("Lovelace");
        when(perfiles.findByIdConUsuario(3L)).thenReturn(Optional.of(perfil));
        String traceId = Traza.abrir("4bf92f3577b34da6a3ce929d0e0e4736");

        VerificacionResponse respuesta = servicio.confirmar(CORREO, "K7QX2M9P", "10.0.0.1");

        assertThat(respuesta.estado()).isEqualTo("ACTIVO");
        assertThat(respuesta.mensaje()).contains("verificado");
        verify(onboarding).iniciar(UID, "ada", traceId, "10.0.0.1");
        ArgumentCaptor<CorreoVerificado> evento = ArgumentCaptor.forClass(CorreoVerificado.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new CorreoVerificado(UID, CORREO, "ada", "Ada", "Lovelace", "10.0.0.1"));
    }

    @Test
    @DisplayName("sin cuenta o con la cuenta ya activa: el mismo 400, sin mirar codigos y con el mismo BCrypt")
    void sinCuentaPendiente() {
        when(usuarios.buscarPorCorreo("nadie@upb.edu.co")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.confirmar("nadie@upb.edu.co", "K7QX2M9P", null))
                .isInstanceOf(CodigoInvalidoException.class);

        pendiente.setEstado(EstadoCuenta.ACTIVO);
        assertThatThrownBy(() -> servicio.confirmar(CORREO, "K7QX2M9P", null))
                .isInstanceOf(CodigoInvalidoException.class);

        verify(igualador, org.mockito.Mockito.times(2)).comparar("K7QX2M9P");
        verifyNoInteractions(codigos, onboarding, eventos);
    }

    @Test
    @DisplayName("codigo incorrecto: 400; agotado: 429; y el alta no empieza")
    void codigoMalo() {
        codigo(Resultado.INVALIDO);
        assertThatThrownBy(() -> servicio.confirmar(CORREO, "K7QX2M9P", null)).isInstanceOf(CodigoInvalidoException.class);
        codigo(Resultado.DEMASIADOS_INTENTOS);
        assertThatThrownBy(() -> servicio.confirmar(CORREO, "K7QX2M9P", null))
                .isInstanceOf(DemasiadosIntentosException.class);

        verify(codigos, never()).marcarUsado(anyLong());
        verifyNoInteractions(onboarding, eventos);
    }

    @Test
    @DisplayName("dos confirmaciones a la vez: la que pierde (codigo ya usado o cuenta ya activa) no lanza otra alta")
    void carreras() {
        codigo(Resultado.VALIDO);
        when(codigos.marcarUsado(11L)).thenReturn(false);
        assertThatThrownBy(() -> servicio.confirmar(CORREO, "K7QX2M9P", null)).isInstanceOf(CodigoInvalidoException.class);

        when(codigos.marcarUsado(11L)).thenReturn(true);
        when(usuarios.activarSiPendiente(3L, EstadoCuenta.PENDIENTE_VERIFICACION, EstadoCuenta.ACTIVO)).thenReturn(0);
        assertThatThrownBy(() -> servicio.confirmar(CORREO, "K7QX2M9P", null)).isInstanceOf(CodigoInvalidoException.class);

        verifyNoInteractions(onboarding, eventos);
    }

    @Test
    @DisplayName("una cuenta sin uid confirma igual, sin alta que arrancar y con una traza nueva si no la hay")
    void sinUid() {
        pendiente.setPublicId(null);
        codigo(Resultado.VALIDO);
        when(codigos.marcarUsado(11L)).thenReturn(true);
        when(usuarios.activarSiPendiente(3L, EstadoCuenta.PENDIENTE_VERIFICACION, EstadoCuenta.ACTIVO)).thenReturn(1);
        when(perfiles.findByIdConUsuario(3L)).thenReturn(Optional.empty());

        assertThat(servicio.confirmar(CORREO, "K7QX2M9P", null).estado()).isEqualTo("ACTIVO");

        verify(onboarding, never()).iniciar(any(), anyString(), anyString(), any());
        verify(eventos).publishEvent(new CorreoVerificado(null, CORREO, "ada", null, null, null));
    }

    @Test
    @DisplayName("reenvio: con cuenta pendiente y dentro del limite, codigo nuevo (y la fila bloqueada)")
    void reenvia() {
        when(usuarios.bloquear(3L)).thenReturn(Optional.of(pendiente));
        when(codigos.admiteOtroEnvio(3L, TipoCodigo.VERIFICACION)).thenReturn(true);

        servicio.reenviar(CORREO);

        verify(usuarios).bloquear(3L);
        verify(codigos).emitir(pendiente, TipoCodigo.VERIFICACION);
        verify(igualador, never()).resumir();
    }

    @Test
    @DisplayName("reenvio: sin cuenta, ya activa o pasado el limite no envia nada, pagando el mismo BCrypt")
    void reenvioNeutro() {
        when(usuarios.buscarPorCorreo("nadie@upb.edu.co")).thenReturn(Optional.empty());
        servicio.reenviar("nadie@upb.edu.co");

        when(usuarios.bloquear(3L)).thenReturn(Optional.of(pendiente));
        when(codigos.admiteOtroEnvio(3L, TipoCodigo.VERIFICACION)).thenReturn(false);
        servicio.reenviar(CORREO);

        pendiente.setEstado(EstadoCuenta.ACTIVO);
        servicio.reenviar(CORREO);

        verify(igualador, org.mockito.Mockito.times(3)).resumir();
        verify(codigos, never()).emitir(any(), any());
    }
}
