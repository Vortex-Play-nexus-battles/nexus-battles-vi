package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

import com.nexusbattles.plataforma.metricasplataforma.moderacion.FuenteDeUsuarios;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-MET-001 (CA-03) — el cliente de ms-identidad que arma esta configuracion tiene plazos de verdad (2 s para
 * conectar, 3 s para responder): si identidad acepta la conexion y no contesta, el tablero de moderacion no se queda
 * esperando. Con red de verdad y la misma fabrica que usa el servicio desplegado, no con un doble.
 */
@DisplayName("Plazos del cliente de ms-identidad (HU-MET-001)")
class PlazosDeLaFuenteDeUsuariosTest {

    private final CountDownLatch soltar = new CountDownLatch(1);
    private HttpServer identidadQueNoContesta;

    @AfterEach
    void apagar() {
        soltar.countDown();
        if (identidadQueNoContesta != null) {
            identidadQueNoContesta.stop(0);
        }
    }

    @Test
    @DisplayName("identidad acepta la conexion y no contesta: a los 3 s es «no responde», sin esperar a que conteste")
    void plazoDeRespuesta() throws IOException {
        identidadQueNoContesta = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        identidadQueNoContesta.setExecutor(Executors.newCachedThreadPool());
        identidadQueNoContesta.createContext("/api/v1/admin/jugadores/indicadores", intercambio -> {
            try {
                soltar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            intercambio.sendResponseHeaders(503, -1);
            intercambio.close();
        });
        identidadQueNoContesta.start();
        FuenteDeUsuarios fuente = new ConfiguracionDeMetricasTecnicas()
                .fuenteDeUsuarios("http://127.0.0.1:" + identidadQueNoContesta.getAddress().getPort());

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> fuente.consultar("2026-09-30", "2026-10-01", "Bearer token-del-admin"))
                .isInstanceOf(FuenteDeUsuarios.NoDisponible.class)
                .hasMessageContaining("no responde");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        // Ni al instante (eso seria una conexion rechazada) ni a los 10 s (eso seria no tener plazo).
        assertThat(ms).as("el plazo de respuesta es de 3 s").isBetween(2_500L, 6_000L);
    }
}
