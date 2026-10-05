package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Las comprobaciones de una peticion del panel, todas a la vez y con tope
 * (RFINAL-08).
 *
 * <h2>Por que existe</h2>
 *
 * Hasta el 4-oct las pantallas «Sistema» y tablero tecnico preguntaban en
 * serie, en el hilo de la peticion: trece sondas de salud una detras de otra
 * (8440 ms medidos en DEV) y veintiocho lecturas de Actuator (7293 ms). El
 * tiempo de la pantalla era la SUMA de todos; ahora es el del mas lento, con
 * un techo.
 *
 * <h2>Las tres reglas</h2>
 *
 * <ul>
 *   <li><b>Ejecutor acotado.</b> Como mucho {@code hilos} llamadas a la vez:
 *       el panel no puede convertirse en una avalancha contra los servicios
 *       que vigila. Hilos con nombre ({@code sondeo-N}), de demonio, que se
 *       retiran cuando no hay trabajo (el servicio vive con 128 MB de monton).</li>
 *   <li><b>Tope para la ronda entera.</b> Lo que no termino al vencer el plazo
 *       sale con su propio desenlace ({@code siNoTermina}) y no retrasa a los
 *       demas. Se reporta como tal: nunca se descarta en silencio ni se rellena
 *       con un dato inventado.</li>
 *   <li><b>Sin reintentos.</b> Es una peticion interactiva: alguien esta
 *       mirando la pantalla. Un reintento duplicaria la espera para decir lo
 *       mismo; la vigencia corta de {@link ResultadoReciente} y la siguiente
 *       recarga ya hacen ese papel.</li>
 * </ul>
 *
 * Los resultados vuelven en el orden de entrada, que es el de la
 * configuracion y el que lee quien mira la pantalla.
 */
public final class RondaEnParalelo implements AutoCloseable {

    /** Cuanto sobrevive un hilo ocioso antes de retirarse. */
    private static final long HILO_OCIOSO_S = 30;

    private final ExecutorService ejecutor;
    private final Duration plazo;

    public RondaEnParalelo(ExecutorService ejecutor, Duration plazo) {
        this.ejecutor = Objects.requireNonNull(ejecutor, "ejecutor");
        if (plazo == null || plazo.isZero() || plazo.isNegative()) {
            throw new IllegalArgumentException("el plazo de la ronda debe ser positivo y llego " + plazo);
        }
        this.plazo = plazo;
    }

    /**
     * La ronda de produccion: {@code hilos} hilos de plataforma como mucho.
     *
     * <p>Hilos de plataforma y no virtuales: la sonda usa HttpURLConnection,
     * que se bloquea dentro de secciones sincronizadas y fijaria el hilo
     * portador en Java 21. Con el tope de hilos el coste es conocido.
     */
    public static RondaEnParalelo acotada(int hilos, Duration plazo) {
        if (hilos <= 0) {
            throw new IllegalArgumentException("una ronda necesita al menos un hilo y llegaron " + hilos);
        }
        ThreadPoolExecutor ejecutor = new ThreadPoolExecutor(hilos, hilos, HILO_OCIOSO_S, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), Thread.ofPlatform().name("sondeo-", 1).daemon(true).factory());
        ejecutor.allowCoreThreadTimeOut(true);
        return new RondaEnParalelo(ejecutor, plazo);
    }

    /** Lo mas que espera una ronda. */
    public Duration plazo() {
        return plazo;
    }

    /**
     * @param elementos   lo que se comprueba, en el orden en que se devuelve
     * @param tarea       la comprobacion de uno; se espera que no lance
     * @param siFalla     el desenlace de uno cuya tarea lanzo de todos modos
     * @param siNoTermina el desenlace de uno que no termino dentro del plazo
     * @return un resultado por elemento, en el mismo orden
     */
    public <E, R> List<R> ejecutar(List<E> elementos,
                                   Function<? super E, ? extends R> tarea,
                                   BiFunction<? super E, Throwable, ? extends R> siFalla,
                                   Function<? super E, ? extends R> siNoTermina) {
        if (elementos.isEmpty()) {
            return List.of();
        }
        List<Callable<R>> tareas = new ArrayList<>(elementos.size());
        for (E elemento : elementos) {
            tareas.add(() -> tarea.apply(elemento));
        }

        List<Future<R>> futuros;
        try {
            // invokeAll con plazo: espera a todas o al plazo, lo que llegue
            // antes, y cancela las que sigan en marcha. Ninguna espera a otra.
            futuros = ejecutor.invokeAll(tareas, plazo.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return elementos.stream().<R>map(siNoTermina::apply).toList();
        } catch (RejectedExecutionException e) {
            // Solo pasa si el contexto se esta cerrando.
            return elementos.stream().<R>map(elemento -> siFalla.apply(elemento, e)).toList();
        }

        List<R> resultados = new ArrayList<>(elementos.size());
        for (int i = 0; i < elementos.size(); i++) {
            resultados.add(desenlace(elementos.get(i), futuros.get(i), siFalla, siNoTermina));
        }
        // unmodifiableList y no List.copyOf: el resultado de una tarea lo decide
        // quien la escribe, y la ronda no es quien para rechazar un null.
        return Collections.unmodifiableList(resultados);
    }

    private static <E, R> R desenlace(E elemento, Future<R> futuro,
                                      BiFunction<? super E, Throwable, ? extends R> siFalla,
                                      Function<? super E, ? extends R> siNoTermina) {
        if (futuro.isCancelled()) {
            return siNoTermina.apply(elemento);
        }
        try {
            return futuro.get();
        } catch (ExecutionException e) {
            return siFalla.apply(elemento, e.getCause() == null ? e : e.getCause());
        } catch (CancellationException e) {
            return siNoTermina.apply(elemento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return siNoTermina.apply(elemento);
        }
    }

    /** Spring lo llama al cerrar el contexto (metodo de destruccion inferido). */
    @Override
    public void close() {
        ejecutor.shutdownNow();
    }
}
