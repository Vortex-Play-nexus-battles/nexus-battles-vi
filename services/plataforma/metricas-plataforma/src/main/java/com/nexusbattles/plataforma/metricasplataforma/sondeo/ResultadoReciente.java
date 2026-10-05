package com.nexusbattles.plataforma.metricasplataforma.sondeo;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * El ultimo resultado de una ronda, valido durante una vigencia corta
 * (RFINAL-08).
 *
 * <h2>Por que</h2>
 *
 * La consola de control integral pide {@code /admin/sistema/servicios} dos
 * veces al cargar (panel Resumen y panel Sistema, a la vez), y cada recarga
 * volvia a sondear los diecinueve servicios. Con esto:
 *
 * <ul>
 *   <li>una peticion que llega mientras otra esta midiendo ESPERA esa misma
 *       medicion en vez de lanzar otra (un solo vuelo);</li>
 *   <li>dentro de la vigencia se devuelve lo ultimo medido, marcado como
 *       reutilizado para que nadie lo confunda con una medicion nueva: la
 *       respuesta lleva la hora de la ronda de la que sale.</li>
 * </ul>
 *
 * Una medicion que lanza no se guarda: la siguiente peticion lo vuelve a
 * intentar. Vigencia cero apaga la reutilizacion por completo.
 *
 * <p>La vigencia se cuenta con un reloj monotono ({@link System#nanoTime()}),
 * no con la hora del sistema: un ajuste de hora no puede alargarla ni
 * acortarla.
 *
 * <p>{@link ReentrantLock} y no {@code synchronized}: quien espera bloquea
 * durante la ronda, y un {@code synchronized} fijaria el hilo portador si
 * algun dia la peticion corre en un hilo virtual.
 */
public final class ResultadoReciente<T> {

    private final Duration vigencia;
    private final LongSupplier nanos;
    private final ReentrantLock cerrojo = new ReentrantLock();

    private T ultimo;
    private long medidoEnNanos;

    public ResultadoReciente(Duration vigencia) {
        this(vigencia, System::nanoTime);
    }

    ResultadoReciente(Duration vigencia, LongSupplier nanos) {
        if (vigencia == null || vigencia.isNegative()) {
            throw new IllegalArgumentException("la vigencia no puede ser negativa y llego " + vigencia);
        }
        this.vigencia = vigencia;
        this.nanos = Objects.requireNonNull(nanos, "nanos");
    }

    /**
     * @param medir la ronda completa; solo se llama si no hay un resultado vigente
     */
    public Lectura<T> obtener(Supplier<T> medir) {
        if (vigencia.isZero()) {
            return new Lectura<>(Objects.requireNonNull(medir.get(), "la ronda no devolvio nada"), false);
        }
        cerrojo.lock();
        try {
            if (ultimo != null && nanos.getAsLong() - medidoEnNanos < vigencia.toNanos()) {
                return new Lectura<>(ultimo, true);
            }
            T nuevo = Objects.requireNonNull(medir.get(), "la ronda no devolvio nada");
            ultimo = nuevo;
            // La vigencia empieza cuando la ronda TERMINA: lo que se reutiliza
            // nunca tiene mas de `vigencia` de antiguedad desde que se tuvo.
            medidoEnNanos = nanos.getAsLong();
            return new Lectura<>(nuevo, false);
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * @param valor      el resultado
     * @param desdeCache true si sale de una ronda anterior (o de la que estaba
     *                   en curso cuando llego la peticion), no de una propia
     */
    public record Lectura<T>(T valor, boolean desdeCache) { }
}
