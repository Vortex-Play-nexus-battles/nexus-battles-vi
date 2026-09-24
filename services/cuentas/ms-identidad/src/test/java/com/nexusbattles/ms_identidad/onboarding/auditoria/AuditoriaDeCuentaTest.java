package com.nexusbattles.ms_identidad.onboarding.auditoria;

import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Auditoria de la vida de la cuenta (R17)")
class AuditoriaDeCuentaTest {

    private static final UUID UID = UUID.fromString("abcdefab-cdef-4abc-8def-abcdefabcdef");
    private static final String RUTA = "/api/v1/admin/auditoria/eventos";

    private ServidorFalso servidor;
    private AuditoriaDeCuenta auditoria;

    @BeforeEach
    void preparar() {
        servidor = new ServidorFalso().responder("POST", RUTA, 201, "");
        // Mismo hilo: la prueba afirma sin esperar.
        auditoria = new AuditoriaDeCuenta(servidor.url() + RUTA, null, new InterceptorDeTraza(), Runnable::run);
    }

    @AfterEach
    void cerrar() {
        Traza.cerrar();
        servidor.close();
    }

    private String ultimoCuerpo() {
        return servidor.recibidas("POST", RUTA).get(servidor.recibidas("POST", RUTA).size() - 1).cuerpo();
    }

    @Test
    @DisplayName("registro: CREACION, el jugador es actor y afectado, sin correo ni contrasena")
    void registro() {
        auditoria.registro(UID, "profe", "10.0.0.9");

        assertThat(ultimoCuerpo())
                .contains("\"tipoAccion\":\"CREACION\"")
                .contains("\"administradorId\":\"" + UID + "\"")
                .contains("\"afectado\":\"" + UID + "\"")
                .contains("\"valorNuevo\":\"apodo=profe;rol=JUGADOR\"")
                .contains("\"motivo\":\"REGISTRO_JUGADOR\"")
                .contains("\"ipOrigen\":\"10.0.0.9\"")
                .doesNotContain("@")
                .doesNotContainIgnoringCase("password");
    }

    @Test
    @DisplayName("primer acceso, cierre de sesion y el resultado del alta, cada uno con su motivo")
    void eventos() {
        auditoria.primerAcceso(UID, null);
        assertThat(ultimoCuerpo()).contains("PRIMER_INICIO_SESION").contains("\"ipOrigen\":\"DESCONOCIDA\"");

        auditoria.cierreDeSesion(UID.toString(), " ");
        assertThat(ultimoCuerpo()).contains("CIERRE_SESION");

        auditoria.altaCompletada(UID, "creditos=500");
        assertThat(ultimoCuerpo()).contains("ONBOARDING_COMPLETADO").contains("\"administradorId\":\"ms-identidad\"");

        auditoria.altaFallida(null, "pendientes=CREDITOS");
        assertThat(ultimoCuerpo()).contains("ONBOARDING_FALLIDO").contains("\"afectado\":\"DESCONOCIDO\"");
    }

    @Test
    @DisplayName("la traza del alta viaja con el evento aunque se envie desde otro hilo")
    void trazaEnOtroHilo() throws Exception {
        CountDownLatch enviado = new CountDownLatch(1);
        AuditoriaDeCuenta enOtroHilo = new AuditoriaDeCuenta(servidor.url() + RUTA, null, new InterceptorDeTraza(),
                tarea -> Thread.ofVirtual().start(() -> {
                    tarea.run();
                    enviado.countDown();
                }));
        Traza.abrir("4bf92f3577b34da6a3ce929d0e0e4736");

        enOtroHilo.altaCompletada(UID, "ok");

        assertThat(enviado.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(servidor.recibidas().get(0).cabecera("traceparent")).contains("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("fail-open: cumplimiento caido, 500 o sin hilos no rompen a quien audita")
    void failOpen() {
        servidor.responder("POST", RUTA, 500, "{}");
        assertThatCode(() -> auditoria.registro(UID, "profe", "1.1.1.1")).doesNotThrowAnyException();

        AuditoriaDeCuenta sinServicio = new AuditoriaDeCuenta("http://127.0.0.1:1" + RUTA, null, null, Runnable::run);
        assertThatCode(() -> sinServicio.primerAcceso(UID, "1.1.1.1")).doesNotThrowAnyException();

        AuditoriaDeCuenta sinHilos = new AuditoriaDeCuenta(servidor.url() + RUTA, null, null, tarea -> {
            throw new IllegalStateException("sin hilos");
        });
        assertThatCode(() -> sinHilos.cierreDeSesion("x", "1.1.1.1")).doesNotThrowAnyException();
    }
}
