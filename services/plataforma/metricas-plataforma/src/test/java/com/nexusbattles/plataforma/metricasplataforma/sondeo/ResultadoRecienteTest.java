package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFINAL-08 — el ultimo resultado del panel vale unos segundos.
 *
 * <p>La consola pide el estado de los servicios dos veces al cargar (Resumen y
 * Sistema, a la vez) y cada recarga volvia a sondear todo. Con esto la segunda
 * peticion simultanea espera a la ronda en curso en vez de lanzar otra, y una
 * recarga dentro de la vigencia reutiliza la ultima.
 */
@DisplayName("Resultado reciente con vigencia corta (RFINAL-08)")
class ResultadoRecienteTest {

    /** Reloj monotono de mentira: avanza solo cuando la prueba lo dice. */
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    private void avanzar(Duration cuanto) {
        nanos.addAndGet(cuanto.toNanos());
    }

    @Test
    @DisplayName("dentro de la vigencia se reutiliza y se dice que es reutilizado")
    void dentroDeLaVigenciaSeReutiliza() {
        ResultadoReciente<String> reciente = new ResultadoReciente<>(Duration.ofSeconds(15), nanos::get);
        AtomicInteger mediciones = new AtomicInteger();

        ResultadoReciente.Lectura<String> primera = reciente.obtener(() -> "ronda-" + mediciones.incrementAndGet());
        avanzar(Duration.ofSeconds(14));
        ResultadoReciente.Lectura<String> segunda = reciente.obtener(() -> "ronda-" + mediciones.incrementAndGet());

        assertThat(primera.valor()).isEqualTo("ronda-1");
        assertThat(primera.desdeCache()).isFalse();
        assertThat(segunda.valor()).isEqualTo("ronda-1");
        assertThat(segunda.desdeCache()).isTrue();
        assertThat(mediciones.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("vencida la vigencia se vuelve a medir")
    void vencidaSeVuelveAMedir() {
        ResultadoReciente<String> reciente = new ResultadoReciente<>(Duration.ofSeconds(15), nanos::get);
        AtomicInteger mediciones = new AtomicInteger();

        reciente.obtener(() -> "ronda-" + mediciones.incrementAndGet());
        avanzar(Duration.ofSeconds(15));
        ResultadoReciente.Lectura<String> otra = reciente.obtener(() -> "ronda-" + mediciones.incrementAndGet());

        assertThat(otra.valor()).isEqualTo("ronda-2");
        assertThat(otra.desdeCache()).isFalse();
    }

    @Test
    @DisplayName("vigencia cero: cada peticion mide (la cache queda apagada)")
    void vigenciaCeroApagaLaCache() {
        ResultadoReciente<String> reciente = new ResultadoReciente<>(Duration.ZERO, nanos::get);
        AtomicInteger mediciones = new AtomicInteger();

        reciente.obtener(() -> "ronda-" + mediciones.incrementAndGet());
        ResultadoReciente.Lectura<String> otra = reciente.obtener(() -> "ronda-" + mediciones.incrementAndGet());

        assertThat(otra.valor()).isEqualTo("ronda-2");
        assertThat(otra.desdeCache()).isFalse();
    }

    @Test
    @DisplayName("una vigencia negativa se rechaza al construir")
    void vigenciaNegativa() {
        assertThatThrownBy(() -> new ResultadoReciente<String>(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una medicion que falla no se guarda: la siguiente peticion vuelve a intentarlo")
    void unFalloNoSeGuarda() {
        ResultadoReciente<String> reciente = new ResultadoReciente<>(Duration.ofSeconds(15), nanos::get);

        assertThatThrownBy(() -> reciente.obtener(() -> {
            throw new IllegalStateException("roto");
        })).hasMessage("roto");
        ResultadoReciente.Lectura<String> despues = reciente.obtener(() -> "sana");

        assertThat(despues.valor()).isEqualTo("sana");
        assertThat(despues.desdeCache()).isFalse();
    }

    /**
     * Resumen y Sistema piden a la vez. Sin esto, cada una lanzaba su ronda
     * completa y el host recibia el doble de sondas en el mismo segundo.
     */
    @Test
    @DisplayName("peticiones simultaneas comparten una sola medicion")
    void peticionesSimultaneasCompartenLaMedicion() throws Exception {
        ResultadoReciente<String> reciente = new ResultadoReciente<>(Duration.ofSeconds(15));
        AtomicInteger mediciones = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<ResultadoReciente.Lectura<String>>> lecturas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                lecturas.add(hilos.submit(() -> {
                    salida.await();
                    return reciente.obtener(() -> {
                        mediciones.incrementAndGet();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "unica";
                    });
                }));
            }
            salida.countDown();

            int medidasAqui = 0;
            for (Future<ResultadoReciente.Lectura<String>> lectura : lecturas) {
                assertThat(lectura.get().valor()).isEqualTo("unica");
                if (!lectura.get().desdeCache()) {
                    medidasAqui++;
                }
            }
            assertThat(mediciones.get()).isEqualTo(1);
            assertThat(medidasAqui).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }
}
