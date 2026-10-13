package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RFINAL-08 — por que fallo una sonda, dicho en el panel en espanol y sin la
 * envoltura de Spring.
 *
 * <p>Lo que importa de verdad es separar los dos tiempos agotados: no poder
 * conectar (host apagado de noche, regla de red que descarta) es CAIDO;
 * conectar y no recibir respuesta a tiempo es LENTO. Confundirlos pintaria de
 * ambar un host apagado.
 */
@DisplayName("Motivo de un fallo de sondeo (RFINAL-08)")
class MotivoDeFalloTest {

    private static final Duration CONEXION = Duration.ofMillis(1000);
    private static final Duration RESPUESTA = Duration.ofMillis(1500);

    /** Como llega de RestClient: la causa real dentro de su envoltura. */
    private static ResourceAccessException envuelta(IOException causa) {
        return new ResourceAccessException("I/O error on GET request for \"http://srv-x:8081/actuator/health\": "
                + causa.getMessage(), causa);
    }

    @Test
    @DisplayName("lectura agotada (HttpURLConnection): espera de respuesta agotada, no de conexion")
    void lecturaAgotada() {
        ResourceAccessException fallo = envuelta(new SocketTimeoutException("Read timed out"));

        assertThat(MotivoDeFallo.esperaDeRespuestaAgotada(fallo)).isTrue();
        assertThat(MotivoDeFallo.conexionAgotada(fallo)).isFalse();
        assertThat(MotivoDeFallo.describir(fallo, CONEXION, RESPUESTA)).isEqualTo("sin respuesta en 1500 ms");
    }

    @Test
    @DisplayName("conexion agotada (HttpURLConnection): es no poder llegar, no lentitud")
    void conexionAgotada() {
        ResourceAccessException fallo = envuelta(new SocketTimeoutException("Connect timed out"));

        assertThat(MotivoDeFallo.conexionAgotada(fallo)).isTrue();
        assertThat(MotivoDeFallo.esperaDeRespuestaAgotada(fallo)).isFalse();
        assertThat(MotivoDeFallo.describir(fallo, CONEXION, RESPUESTA)).isEqualTo("sin conexión en 1000 ms");
    }

    @Test
    @DisplayName("los mismos dos casos con el cliente HTTP del JDK, por si se cambia la fabrica")
    void clienteDelJdk() {
        ResourceAccessException respuesta = envuelta(new HttpTimeoutException("request timed out"));
        ResourceAccessException conexion = envuelta(new HttpConnectTimeoutException("HTTP connect timed out"));

        assertThat(MotivoDeFallo.esperaDeRespuestaAgotada(respuesta)).isTrue();
        assertThat(MotivoDeFallo.conexionAgotada(respuesta)).isFalse();
        assertThat(MotivoDeFallo.conexionAgotada(conexion)).isTrue();
        assertThat(MotivoDeFallo.esperaDeRespuestaAgotada(conexion)).isFalse();
    }

    @Test
    @DisplayName("conexion rechazada y nombre que no resuelve, en palabras")
    void rechazoYNombreDesconocido() {
        assertThat(MotivoDeFallo.describir(envuelta(new ConnectException("Connection refused")), CONEXION, RESPUESTA))
                .isEqualTo("conexión rechazada");
        assertThat(MotivoDeFallo.describir(
                envuelta(new UnknownHostException("srv-ms-subastas: Name or service not known")), CONEXION, RESPUESTA))
                .isEqualTo("no se encuentra el host srv-ms-subastas");
    }

    @Test
    @DisplayName("lo demas sale con el mensaje de la causa raiz, que dice mas que el de la envoltura")
    void loDemasConLaCausaRaiz() {
        RuntimeException fallo = new RuntimeException("envoltura", new IllegalStateException("la causa"));

        assertThat(MotivoDeFallo.describir(fallo, CONEXION, RESPUESTA)).isEqualTo("la causa");
        assertThat(MotivoDeFallo.describir(new IllegalStateException(), null, null)).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("sin plazos conocidos se dice igual, sin inventar una cifra")
    void sinPlazos() {
        assertThat(MotivoDeFallo.describir(envuelta(new SocketTimeoutException("Read timed out")), null, null))
                .isEqualTo("sin respuesta dentro del plazo");
        assertThat(MotivoDeFallo.describir(envuelta(new SocketTimeoutException("Connect timed out")), null, null))
                .isEqualTo("sin conexión dentro del plazo");
    }
}
