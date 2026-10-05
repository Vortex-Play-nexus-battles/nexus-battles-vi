package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * B12 — el lector del catalogo de parametros de moderacion sale con tiempos
 * de espera.
 *
 * <p>El lector nunca lanza: si admin-parametros no contesta, sirve el
 * respaldo. Pero sin tiempo de lectura «no contesta» no llegaba nunca, y
 * emitir una sancion (que lee el rango de la suspension) se quedaba colgado
 * con admin-parametros aceptando la conexion y callado.
 */
@DisplayName("Lector de parametros de sanciones: un catalogo colgado no cuelga la sancion (B12)")
class LectorDeParametrosConTiemposTest {

    @Test
    @DisplayName("con admin-parametros colgado, el lector devuelve el respaldo dentro de su tiempo de lectura")
    void catalogoColgadoDevuelveElRespaldo() throws Exception {
        // Acepta la conexion (la completa el nucleo) y no contesta nunca.
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            LectorDeParametros lector = new ConfiguracionDeSanciones().lectorDeParametros(
                    "http://127.0.0.1:" + mudo.getLocalPort() + "/api/v1", 30, 300, 300, Clock.systemUTC());

            assertThat(lector.tieneCatalogo()).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThat(lector.entero("sanciones.apelacion.plazo-dias", 30)).isEqualTo(30));
        }
    }

    @Test
    @DisplayName("sin URL no hay catalogo ni peticiones: solo respaldos")
    void sinUrlSoloRespaldo() {
        LectorDeParametros lector = new ConfiguracionDeSanciones().lectorDeParametros("", 30, 1000, 2000,
                Clock.systemUTC());

        assertThat(lector.tieneCatalogo()).isFalse();
        assertThat(lector.entero("sanciones.apelacion.plazo-dias", 30)).isEqualTo(30);
    }
}
