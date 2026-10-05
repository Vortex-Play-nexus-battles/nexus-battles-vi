package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.exception.CuentaBaneadaException;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.AccesoConSegundoFactorResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.ActivacionConDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.CanjeDeDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import com.nexusbattles.ms_identidad.auth.service.LoginService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El segundo paso del login (HU-AUT-007 CA-01, CA-02, CA-03) y el
 * enrolamiento obligatorio sin sesion: el desafio prueba la contrasena, el
 * codigo prueba el segundo factor, y solo entonces hay sesion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Segundo paso del login")
class SegundoPasoDelLoginTest {

    @Mock
    private DesafiosDeAcceso desafios;
    @Mock
    private SegundoFactorService segundoFactor;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private LoginService login;

    @InjectMocks
    private SegundoPasoDelLogin segundoPaso;

    private static Usuario ana(int version) {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setApodo("ana");
        usuario.setVersionToken(version);
        return usuario;
    }

    private static DesafioDeAcceso desafio(Proposito proposito, int version) {
        return new DesafioDeAcceso(7L, "resumen", proposito, version, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(4));
    }

    private static LoginResponse sesion() {
        return new LoginResponse(7L, "ana", "ana@nexus.test", "ADMINISTRADOR", false, "token-2fa", "uid", true);
    }

    private static Motivo motivoDe(ThrowingCallable llamada) {
        Throwable error = catchThrowable(llamada);
        assertThat(error).isInstanceOf(SegundoFactorRechazadoException.class);
        return ((SegundoFactorRechazadoException) error).getMotivo();
    }

    @Test
    @DisplayName("desafio y codigo correctos: gasta el desafio y abre la sesion con doble factor")
    void canjeCorrecto() {
        DesafioDeAcceso vigente = desafio(Proposito.VERIFICAR, 2);
        Usuario ana = ana(2);
        when(desafios.vigente("valor", Proposito.VERIFICAR)).thenReturn(vigente);
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(segundoFactor.comprobarEnElAcceso(ana, "123456", null, "10.0.0.1"))
                .thenReturn(ComprobacionDelSegundoFactor.conAplicacion());
        when(login.completarAccesoConSegundoFactor(ana, "10.0.0.1", "agente")).thenReturn(sesion());

        AccesoConSegundoFactorResponse respuesta = segundoPaso.canjear(
                new CanjeDeDesafioRequest("valor", "123456", null), "10.0.0.1", "agente");

        assertThat(respuesta.getToken()).isEqualTo("token-2fa");
        assertThat(respuesta.getApodo()).isEqualTo("ana");
        assertThat(respuesta.getCodigosRecuperacionRestantes()).isNull();
        assertThat(respuesta.getCodigosRecuperacion()).isNull();
        verify(desafios).consumir(vigente);
    }

    @Test
    @DisplayName("con un codigo de recuperacion: la sesion y cuantos codigos quedan")
    void canjeConRecuperacion() {
        DesafioDeAcceso vigente = desafio(Proposito.VERIFICAR, 0);
        Usuario ana = ana(0);
        when(desafios.vigente("valor", Proposito.VERIFICAR)).thenReturn(vigente);
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(segundoFactor.comprobarEnElAcceso(ana, null, "K7QX2-M9PRT", null))
                .thenReturn(ComprobacionDelSegundoFactor.conRecuperacion(4));
        when(login.completarAccesoConSegundoFactor(ana, null, null)).thenReturn(sesion());

        AccesoConSegundoFactorResponse respuesta = segundoPaso.canjear(
                new CanjeDeDesafioRequest("valor", null, "K7QX2-M9PRT"), null, null);

        assertThat(respuesta.getCodigosRecuperacionRestantes()).isEqualTo(4L);
    }

    @Test
    @DisplayName("codigo incorrecto: el desafio NO se gasta (se puede volver a intentar) y no hay sesion")
    void codigoIncorrecto() {
        DesafioDeAcceso vigente = desafio(Proposito.VERIFICAR, 0);
        Usuario ana = ana(0);
        when(desafios.vigente("valor", Proposito.VERIFICAR)).thenReturn(vigente);
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(segundoFactor.comprobarEnElAcceso(ana, "000000", null, null)).thenThrow(
                new SegundoFactorRechazadoException(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO, "No."));

        assertThat(motivoDe(() -> segundoPaso.canjear(new CanjeDeDesafioRequest("valor", "000000", null), null,
                null))).isEqualTo(Motivo.CODIGO_INVALIDO_EN_EL_ACCESO);
        verify(desafios, never()).consumir(any());
        verifyNoInteractions(login);
    }

    @Test
    @DisplayName("la cuenta cambio de contrasena entre los dos pasos: 401 desafio-invalido, sin mirar el codigo")
    void versionCambiada() {
        when(desafios.vigente("valor", Proposito.VERIFICAR)).thenReturn(desafio(Proposito.VERIFICAR, 1));
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana(2)));

        assertThat(motivoDe(() -> segundoPaso.canjear(new CanjeDeDesafioRequest("valor", "123456", null), null,
                null))).isEqualTo(Motivo.DESAFIO_INVALIDO);
        verifyNoInteractions(segundoFactor, login);
    }

    @Test
    @DisplayName("la cuenta ya no existe: 401 desafio-invalido")
    void cuentaBorrada() {
        when(desafios.vigente("valor", Proposito.VERIFICAR)).thenReturn(desafio(Proposito.VERIFICAR, 0));
        when(usuarios.findById(7L)).thenReturn(Optional.empty());

        assertThat(motivoDe(() -> segundoPaso.canjear(new CanjeDeDesafioRequest("valor", "123456", null), null,
                null))).isEqualTo(Motivo.DESAFIO_INVALIDO);
    }

    @Test
    @DisplayName("una sancion que llego entre los dos pasos sale tal cual (403 del login), sin sesion")
    void sancionEntreLosDosPasos() {
        DesafioDeAcceso vigente = desafio(Proposito.VERIFICAR, 0);
        Usuario ana = ana(0);
        when(desafios.vigente("valor", Proposito.VERIFICAR)).thenReturn(vigente);
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(segundoFactor.comprobarEnElAcceso(ana, "123456", null, null))
                .thenReturn(ComprobacionDelSegundoFactor.conAplicacion());
        doThrow(new CuentaBaneadaException("Baneada.")).when(login).completarAccesoConSegundoFactor(ana, null, null);

        assertThatThrownBy(() -> segundoPaso.canjear(new CanjeDeDesafioRequest("valor", "123456", null), null, null))
                .isInstanceOf(CuentaBaneadaException.class);
    }

    @Test
    @DisplayName("enrolamiento obligatorio: con el desafio de ENROLAR se pide el secreto, sin sesion")
    void enrolarConDesafio() {
        Usuario ana = ana(0);
        EnrolamientoResponse enrolamiento = new EnrolamientoResponse("SECRETO", "otpauth://totp/x", "Nexus",
                "ana@nexus.test", "SHA1", 6, 30);
        when(desafios.vigente("valor", Proposito.ENROLAR)).thenReturn(desafio(Proposito.ENROLAR, 0));
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(segundoFactor.iniciarEnrolamiento(ana)).thenReturn(enrolamiento);

        assertThat(segundoPaso.enrolarConDesafio(new DesafioRequest("valor"))).isSameAs(enrolamiento);
        verify(desafios, never()).consumir(any());
        verifyNoInteractions(login);
    }

    @Test
    @DisplayName("enrolamiento obligatorio confirmado: activa, gasta el desafio y entrega sesion y codigos una vez")
    void activarConDesafio() {
        DesafioDeAcceso vigente = desafio(Proposito.ENROLAR, 0);
        Usuario ana = ana(0);
        when(desafios.vigente("valor", Proposito.ENROLAR)).thenReturn(vigente);
        when(usuarios.findById(7L)).thenReturn(Optional.of(ana));
        when(segundoFactor.activar(ana, "123456", "10.0.0.9")).thenReturn(List.of("AAAAA-BBBBB", "CCCCC-DDDDD"));
        when(login.completarAccesoConSegundoFactor(ana, "10.0.0.9", "agente")).thenReturn(sesion());

        AccesoConSegundoFactorResponse respuesta = segundoPaso.activarConDesafio(
                new ActivacionConDesafioRequest("valor", "123456"), "10.0.0.9", "agente");

        assertThat(respuesta.getToken()).isEqualTo("token-2fa");
        assertThat(respuesta.getCodigosRecuperacion()).containsExactly("AAAAA-BBBBB", "CCCCC-DDDDD");
        verify(desafios).consumir(vigente);
    }

    @Test
    @DisplayName("un desafio de verificacion no sirve para enrolarse, ni al reves (lo decide DesafiosDeAcceso)")
    void propositoCruzado() {
        when(desafios.vigente("valor", Proposito.ENROLAR)).thenThrow(SegundoFactorRechazadoException.desafioInvalido());

        assertThat(motivoDe(() -> segundoPaso.enrolarConDesafio(new DesafioRequest("valor"))))
                .isEqualTo(Motivo.DESAFIO_INVALIDO);
        verifyNoInteractions(usuarios, segundoFactor, login);
    }
}
