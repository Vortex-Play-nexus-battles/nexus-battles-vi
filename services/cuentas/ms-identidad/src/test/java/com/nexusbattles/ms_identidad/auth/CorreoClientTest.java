package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.correo.CorreoClient;
import com.nexusbattles.ms_identidad.auth.correo.Enmascarado;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoAvisoAccesoRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoBienvenidaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoCambioClaveRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoConfirmacionCuentaRequest;
import com.nexusbattles.ms_identidad.auth.correo.dto.CorreoRecuperacionClaveRequest;
import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * El cliente de correo contra un servidor HTTP de verdad: ruta, cuerpo y la
 * cabecera {@code Idempotency-Key} de correo.yaml 1.4.0 (B1).
 */
class CorreoClientTest {

    private ServidorFalso correo;
    private CorreoClient correoClient;

    @BeforeEach
    void setUp() {
        correo = new ServidorFalso();
        correo.responder("POST", "/api/v1/correos/.*", 202, "");
        correoClient = new CorreoClient(RestClient.create());
        String base = correo.url() + "/api/v1/correos/";
        ReflectionTestUtils.setField(correoClient, "urlBienvenida", base + "bienvenida");
        ReflectionTestUtils.setField(correoClient, "urlAvisoAcceso", base + "aviso-acceso");
        ReflectionTestUtils.setField(correoClient, "urlRecuperacionClave", base + "recuperacion-clave");
        ReflectionTestUtils.setField(correoClient, "urlConfirmacionCuenta", base + "confirmacion-cuenta");
        ReflectionTestUtils.setField(correoClient, "urlCambioClave", base + "cambio-clave");
    }

    @AfterEach
    void apagar() {
        correo.close();
    }

    private ServidorFalso.Peticion unica(String plantilla) {
        assertThat(correo.recibidas("POST", "/api/v1/correos/" + plantilla)).hasSize(1);
        return correo.recibidas("POST", "/api/v1/correos/" + plantilla).get(0);
    }

    @Test
    void debeEnviarCorreoDeBienvenidaConLosDatosCorrectos() {
        correoClient.enviarBienvenida(
            new CorreoBienvenidaRequest("cristian@test.com", "cristianc", "Cristian", "Chaparro"), "bienvenida-u1");

        ServidorFalso.Peticion peticion = unica("bienvenida");
        assertThat(peticion.cuerpo()).contains("\"email\":\"cristian@test.com\"").contains("\"apodo\":\"cristianc\"");
        assertThat(peticion.cabecera("Idempotency-Key")).isEqualTo("bienvenida-u1");
    }

    @Test
    void debeEnviarAvisoDeAccesoSinClave() {
        correoClient.enviarAvisoAcceso(new CorreoAvisoAccesoRequest(
            "cristian@test.com", "cristianc", "127.0.0.1", "2026-08-30T14:23:11-05:00"));

        ServidorFalso.Peticion peticion = unica("aviso-acceso");
        assertThat(peticion.cuerpo()).contains("\"ip\":\"127.0.0.1\"");
        assertThat(peticion.cabecera("Idempotency-Key")).isNull();
    }

    @Test
    @DisplayName("B1: verificacion con proposito VERIFICACION y la clave del codigo")
    void confirmacionDeCuenta() {
        correoClient.enviarConfirmacionCuenta(new CorreoConfirmacionCuentaRequest(
            "ana@test.com", "ana", "K7QX2M9P", 1440, CorreoConfirmacionCuentaRequest.VERIFICACION), "verificacion-7");

        ServidorFalso.Peticion peticion = unica("confirmacion-cuenta");
        assertThat(peticion.cuerpo()).contains("\"codigo\":\"K7QX2M9P\"").contains("\"minutosVigencia\":1440")
            .contains("\"proposito\":\"VERIFICACION\"");
        assertThat(peticion.cabecera("Idempotency-Key")).isEqualTo("verificacion-7");
    }

    @Test
    void recuperacionYCambioDeClave() {
        correoClient.enviarRecuperacionClave(
            new CorreoRecuperacionClaveRequest("ana@test.com", "ana", "ABCD2345", 30), "restablecimiento-3");
        correoClient.enviarCambioClave(
            new CorreoCambioClaveRequest("ana@test.com", "ana", "10.0.0.1", "2026-09-21T15:00:00Z"), " ");

        assertThat(unica("recuperacion-clave").cabecera("Idempotency-Key")).isEqualTo("restablecimiento-3");
        ServidorFalso.Peticion cambio = unica("cambio-clave");
        assertThat(cambio.cuerpo()).contains("\"fechaHora\":\"2026-09-21T15:00:00Z\"");
        assertThat(cambio.cabecera("Idempotency-Key")).as("una clave en blanco no se envia").isNull();
    }

    @Test
    void losRespaldosNoLanzanExcepcionNiImprimenElCorreoEntero() {
        RuntimeException caido = new RuntimeException("Servicio caído");
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(correoClient, "enviarBienvenidaConFallback",
            new CorreoBienvenidaRequest("cristian@test.com", "c", "C", "C"), "k", caido));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(correoClient, "enviarAvisoAccesoConFallback",
            new CorreoAvisoAccesoRequest("cristian@test.com", "c", "1.1.1.1", "x"), caido));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(correoClient, "enviarCambioClaveConFallback",
            new CorreoCambioClaveRequest("ana@test.com", "ana", "10.0.0.1", "x"), "k", caido));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(correoClient, "enviarRecuperacionClaveConFallback",
            new CorreoRecuperacionClaveRequest("ana@test.com", "ana", "X", 30), "k", caido));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(correoClient, "enviarConfirmacionCuentaConFallback",
            new CorreoConfirmacionCuentaRequest("ana@test.com", "ana", "X", 30, "ACTIVACION"), "k", caido));

        assertThat(Enmascarado.correo("valkiria@upb.edu.co")).isEqualTo("v***a@upb.edu.co");
        assertThat(Enmascarado.correo("v@upb.edu.co")).isEqualTo("v***@upb.edu.co");
        assertThat(Enmascarado.correo("sin-arroba")).isEqualTo("***");
        assertThat(Enmascarado.correo(null)).isEqualTo("(sin correo)");
    }
}
