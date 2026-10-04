package com.nexusbattles.ms_identidad.auth.codigos;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoCambioClaveRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoConfirmacionCuentaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoRecuperacionClaveRequest;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Correos y auditoria de la cuenta, despues del commit (B1)")
class CorreosDeCuentaTest {

    private static final UUID UID = UUID.fromString("0b0b0b0b-1111-4222-8333-444444444444");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

    private CorreoClient correo;
    private AuditoriaDeCuenta auditoria;
    private final List<Runnable> encolados = new ArrayList<>();
    private CorreosDeCuenta correos;

    @BeforeEach
    void preparar() {
        correo = mock(CorreoClient.class);
        auditoria = mock(AuditoriaDeCuenta.class);
        Executor enCola = encolados::add;
        correos = new CorreosDeCuenta(correo, auditoria, enCola, RELOJ);
    }

    @AfterEach
    void limpiar() {
        Traza.cerrar();
    }

    private void ejecutarEncolados() {
        encolados.forEach(Runnable::run);
    }

    @Test
    @DisplayName("verificacion: confirmacion-cuenta con proposito VERIFICACION y clave verificacion-<id>, sin esperar")
    void codigoDeVerificacion() {
        correos.codigoEmitido(new CodigoParaEnviar(5L, TipoCodigo.VERIFICACION, "ada@upb.edu.co", "ada",
                "K7QX2M9P", 1440));

        verify(correo, never()).enviarConfirmacionCuenta(any(), anyString());
        ejecutarEncolados();

        ArgumentCaptor<CorreoConfirmacionCuentaRequest> enviado =
                ArgumentCaptor.forClass(CorreoConfirmacionCuentaRequest.class);
        verify(correo).enviarConfirmacionCuenta(enviado.capture(), eq("verificacion-5"));
        assertThat(enviado.getValue()).extracting(CorreoConfirmacionCuentaRequest::getEmail,
                CorreoConfirmacionCuentaRequest::getCodigo, CorreoConfirmacionCuentaRequest::getMinutosVigencia,
                CorreoConfirmacionCuentaRequest::getProposito)
                .containsExactly("ada@upb.edu.co", "K7QX2M9P", 1440, "VERIFICACION");
    }

    @Test
    @DisplayName("activacion: el mismo correo con proposito ACTIVACION; restablecimiento: recuperacion-clave")
    void otrosCodigos() {
        correos.codigoEmitido(new CodigoParaEnviar(6L, TipoCodigo.ACTIVACION, "mod@upb.edu.co", "mod", "AAAA2222", 1440));
        correos.codigoEmitido(new CodigoParaEnviar(7L, TipoCodigo.RESTABLECIMIENTO, "ada@upb.edu.co", "ada",
                "BBBB3333", 30));
        ejecutarEncolados();

        ArgumentCaptor<CorreoConfirmacionCuentaRequest> activacion =
                ArgumentCaptor.forClass(CorreoConfirmacionCuentaRequest.class);
        verify(correo).enviarConfirmacionCuenta(activacion.capture(), eq("activacion-6"));
        assertThat(activacion.getValue().getProposito()).isEqualTo("ACTIVACION");
        ArgumentCaptor<CorreoRecuperacionClaveRequest> recuperacion =
                ArgumentCaptor.forClass(CorreoRecuperacionClaveRequest.class);
        verify(correo).enviarRecuperacionClave(recuperacion.capture(), eq("restablecimiento-7"));
        assertThat(recuperacion.getValue().getCodigo()).isEqualTo("BBBB3333");
        assertThat(recuperacion.getValue().getMinutosVigencia()).isEqualTo(30);
    }

    @Test
    @DisplayName("correo verificado: auditoria de la verificacion y bienvenida (una por cuenta)")
    void verificado() {
        correos.correoVerificado(new CorreoVerificado(UID, "ada@upb.edu.co", "ada", "Ada", "Lovelace", "10.0.0.1"));
        ejecutarEncolados();

        verify(auditoria).correoVerificado(UID, "10.0.0.1");
        ArgumentCaptor<CorreoBienvenidaRequest> bienvenida = ArgumentCaptor.forClass(CorreoBienvenidaRequest.class);
        verify(correo).enviarBienvenida(bienvenida.capture(), eq("bienvenida-" + UID));
        assertThat(bienvenida.getValue().getNombres()).isEqualTo("Ada");
    }

    @Test
    @DisplayName("contrasena restablecida: aviso cambio-clave con clave del codigo y auditoria segun el tipo")
    void restablecida() {
        correos.contrasenaRestablecida(new ContrasenaRestablecida(9L, TipoCodigo.RESTABLECIMIENTO, UID, 1L,
                "ada@upb.edu.co", "ada", null));
        correos.contrasenaRestablecida(new ContrasenaRestablecida(10L, TipoCodigo.ACTIVACION, null, 2L,
                "mod@upb.edu.co", "mod", "10.0.0.2"));
        ejecutarEncolados();

        verify(auditoria).contrasenaRestablecida(UID.toString(), "RESTABLECIMIENTO_CONTRASENA", null);
        verify(auditoria).contrasenaRestablecida("usuario-2", "ACTIVACION_CUENTA", "10.0.0.2");
        ArgumentCaptor<CorreoCambioClaveRequest> aviso = ArgumentCaptor.forClass(CorreoCambioClaveRequest.class);
        verify(correo).enviarCambioClave(aviso.capture(), eq("cambio-clave-9"));
        assertThat(aviso.getValue().getIp()).isEqualTo("desconocida");
        assertThat(aviso.getValue().getFechaHora()).isEqualTo("2026-09-25T12:00Z");
    }

    @Test
    @DisplayName("alta de la cuenta y preguntas configuradas: solo auditoria")
    void auditorias() {
        correos.cuentaRegistrada(new CuentaRegistrada(UID, "ada", "10.0.0.3"));
        correos.preguntasConfiguradas(new PreguntasConfiguradas(UID.toString(), 3, "10.0.0.3"));

        verify(auditoria).registro(UID, "ada", "10.0.0.3");
        verify(auditoria).preguntasConfiguradas(UID.toString(), 3, "10.0.0.3");
        assertThat(encolados).isEmpty();
    }

    @Test
    @DisplayName("si correo revienta o no hay hilo, la peticion que lo pidio no se entera")
    void fallosNoSePropagan() {
        doThrow(new IllegalStateException("correo caido")).when(correo).enviarBienvenida(any(), anyString());
        CorreosDeCuenta sincrono = new CorreosDeCuenta(correo, auditoria, CorreosDeCuenta.ejecutorPara("sincrono"), RELOJ);
        assertThatCode(() -> sincrono.correoVerificado(new CorreoVerificado(UID, "a@b.co", "a", null, null, null)))
                .doesNotThrowAnyException();

        CorreosDeCuenta sinHilos = new CorreosDeCuenta(correo, auditoria, tarea -> {
            throw new IllegalStateException("sin hilos");
        }, RELOJ);
        assertThatCode(() -> sinHilos.codigoEmitido(new CodigoParaEnviar(1L, TipoCodigo.VERIFICACION, "a@b.co",
                "a", "X", 1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el envio en segundo plano lleva la traza de la peticion y no la deja abierta en el hilo")
    void traza() throws Exception {
        CountDownLatch enviado = new CountDownLatch(1);
        String[] trazaVista = new String[1];
        doAnswer(invocacion -> {
            trazaVista[0] = Traza.actual().orElse(null);
            enviado.countDown();
            return null;
        }).when(correo).enviarConfirmacionCuenta(any(), anyString());
        CorreosDeCuenta enHilo = new CorreosDeCuenta(correo, auditoria, "segundo-plano");

        String traceId = Traza.abrir("4bf92f3577b34da6a3ce929d0e0e4736");
        enHilo.codigoEmitido(new CodigoParaEnviar(1L, TipoCodigo.VERIFICACION, "a@b.co", "a", "X", 1));

        assertThat(enviado.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(trazaVista[0]).isEqualTo(traceId);

        // Sincrono y con la traza ya abierta: no se la cierra a la peticion.
        CorreosDeCuenta sincrono = new CorreosDeCuenta(correo, auditoria, CorreosDeCuenta.ejecutorPara(" SINCRONO "), RELOJ);
        sincrono.codigoEmitido(new CodigoParaEnviar(2L, TipoCodigo.VERIFICACION, "a@b.co", "a", "X", 1));
        assertThat(Traza.actual()).contains(traceId);
    }
}
