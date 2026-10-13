package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFINAL-08 — la ronda de comprobaciones de una peticion del panel.
 *
 * <p>Lo que se fija aqui es lo que pedia el hallazgo de DEV del 4-oct: trece
 * sondas en serie tardaban la suma de las trece (8440 ms). En paralelo tardan
 * lo que la mas lenta, y la que no llega a tiempo se reporta y no arrastra a
 * las demas.
 */
@DisplayName("Ronda en paralelo con tope (RFINAL-08)")
class RondaEnParaleloTest {

    private RondaEnParalelo ronda;

    @AfterEach
    void cerrar() {
        if (ronda != null) {
            ronda.close();
        }
    }

    private static String dormirYDevolver(String valor, long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return valor;
    }

    @Test
    @DisplayName("trece comprobaciones de 1 s tardan ~1 s, no 13 s, y vuelven en el orden pedido")
    void treceDeUnSegundoTardanUnSegundo() {
        ronda = RondaEnParalelo.acotada(16, Duration.ofSeconds(3));
        List<Integer> trece = IntStream.rangeClosed(1, 13).boxed().toList();

        long inicio = System.nanoTime();
        List<String> resultados = ronda.ejecutar(trece,
                n -> dormirYDevolver("s" + n, 1000),
                (n, fallo) -> "fallo",
                n -> "sin tiempo");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        System.out.printf("RFINAL-08 ronda: 13 comprobaciones de 1000 ms en %d ms%n", ms);
        assertThat(resultados).containsExactlyElementsOf(trece.stream().map(n -> "s" + n).toList());
        assertThat(ms).isLessThan(2000);
    }

    @Test
    @DisplayName("la que no termina dentro del plazo sale con su desenlace propio y no retrasa a las demas")
    void unaLentaNoRetrasaALasDemas() {
        ronda = RondaEnParalelo.acotada(4, Duration.ofMillis(300));

        long inicio = System.nanoTime();
        List<String> resultados = ronda.ejecutar(List.of("rapida", "colgada", "otra"),
                nombre -> "colgada".equals(nombre) ? dormirYDevolver(nombre, 5000) : nombre,
                (nombre, fallo) -> "fallo",
                nombre -> nombre + ": sin tiempo");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(resultados).containsExactly("rapida", "colgada: sin tiempo", "otra");
        assertThat(ms).isLessThan(1500);
    }

    @Test
    @DisplayName("una comprobacion que lanza no tumba la ronda: sale con su motivo")
    void unaQueLanzaNoTumbaLaRonda() {
        ronda = RondaEnParalelo.acotada(2, Duration.ofSeconds(2));

        List<String> resultados = ronda.ejecutar(List.of("bien", "rota"),
                nombre -> {
                    if ("rota".equals(nombre)) {
                        throw new IllegalStateException("se rompio");
                    }
                    return nombre;
                },
                (nombre, fallo) -> nombre + ": " + fallo.getMessage(),
                nombre -> "sin tiempo");

        assertThat(resultados).containsExactly("bien", "rota: se rompio");
    }

    @Test
    @DisplayName("el ejecutor es acotado: nunca corren a la vez mas tareas que hilos")
    void elEjecutorEsAcotado() {
        ronda = RondaEnParalelo.acotada(3, Duration.ofSeconds(5));
        AtomicInteger enCurso = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();

        ronda.ejecutar(IntStream.range(0, 12).boxed().toList(),
                n -> {
                    int ahora = enCurso.incrementAndGet();
                    maximo.accumulateAndGet(ahora, Math::max);
                    dormirYDevolver("", 50);
                    enCurso.decrementAndGet();
                    return n;
                },
                (n, fallo) -> -1,
                n -> -2);

        assertThat(maximo.get()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("sin nada que comprobar no hay nada que esperar")
    void sinElementos() {
        ronda = RondaEnParalelo.acotada(2, Duration.ofSeconds(1));

        assertThat(ronda.ejecutar(List.<String>of(), s -> s, (s, f) -> s, s -> s)).isEmpty();
    }

    @Test
    @DisplayName("con el contexto cerrandose (ejecutor apagado) cada uno sale con su fallo, no con una excepcion")
    void conElEjecutorApagado() {
        ronda = RondaEnParalelo.acotada(2, Duration.ofSeconds(1));
        ronda.close();

        List<String> resultados = ronda.ejecutar(List.of("a", "b"), s -> s,
                (s, fallo) -> s + ": " + fallo.getClass().getSimpleName(), s -> "sin tiempo");

        assertThat(resultados).containsExactly("a: RejectedExecutionException", "b: RejectedExecutionException");
    }

    @Test
    @DisplayName("un plazo o un numero de hilos sin sentido se rechaza al construir")
    void valoresSinSentido() {
        assertThatThrownBy(() -> RondaEnParalelo.acotada(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RondaEnParalelo.acotada(2, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
