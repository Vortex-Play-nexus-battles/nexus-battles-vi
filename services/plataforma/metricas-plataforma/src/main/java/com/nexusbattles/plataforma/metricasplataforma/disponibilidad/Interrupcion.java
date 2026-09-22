package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Duration;
import java.time.Instant;

/**
 * Un tramo en el que un servicio estuvo caido — HU-DIS-001.
 *
 * <p>Se abre con la primera comprobacion fallida y se cierra con la primera
 * que vuelve a responder. Mientras no se cierra, {@link #fin()} es nulo y
 * la duracion se calcula hasta el fin del periodo consultado.
 *
 * <p>El identificador lo pone el {@link AlmacenDeDisponibilidad} al guardarla
 * (es nulo hasta entonces): hace falta para poder cerrarla despues, incluso
 * si el servicio se reinicio entre la apertura y el cierre.
 */
public final class Interrupcion {

    private final String servicio;
    private final Instant inicio;
    private final String detalle;
    private Instant fin;
    private Long id;

    Interrupcion(String servicio, Instant inicio, String detalle) {
        this.servicio = servicio;
        this.inicio = inicio;
        this.detalle = detalle;
    }

    /** Reconstruye una interrupcion guardada, cerrada o no. */
    static Interrupcion guardada(long id, String servicio, Instant inicio, Instant fin, String detalle) {
        Interrupcion interrupcion = new Interrupcion(servicio, inicio, detalle);
        interrupcion.id = id;
        interrupcion.fin = fin;
        return interrupcion;
    }

    void cerrar(Instant momento) {
        this.fin = momento;
    }

    void identificar(long id) {
        this.id = id;
    }

    /** Identificador en el almacen, o nulo si nunca se guardo. */
    public Long id() {
        return id;
    }

    public String servicio() {
        return servicio;
    }

    public Instant inicio() {
        return inicio;
    }

    public Instant fin() {
        return fin;
    }

    public String detalle() {
        return detalle;
    }

    public boolean abierta() {
        return fin == null;
    }

    /** Cuanto de esta interrupcion cae dentro del periodo [desde, hasta). */
    public Duration duracionEn(Instant desde, Instant hasta) {
        Instant arranque = inicio.isBefore(desde) ? desde : inicio;
        Instant cierre = fin == null || fin.isAfter(hasta) ? hasta : fin;
        return cierre.isAfter(arranque) ? Duration.between(arranque, cierre) : Duration.ZERO;
    }
}
