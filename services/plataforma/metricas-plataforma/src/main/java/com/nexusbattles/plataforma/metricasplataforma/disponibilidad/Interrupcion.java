package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Duration;
import java.time.Instant;

/**
 * Tramo en el que un servicio estuvo caido (HU-DIS-001, CP-02: el informe
 * incluye «el tiempo disponible y las interrupciones registradas»).
 *
 * <p>Una interrupcion abierta —el servicio sigue caido ahora mismo— tiene
 * {@code fin} nulo. Se cierra cuando el servicio vuelve a responder.
 */
public final class Interrupcion {

    private final String servicio;
    private final Instant inicio;
    private final String detalle;
    private Instant fin;

    Interrupcion(String servicio, Instant inicio, String detalle) {
        this.servicio = servicio;
        this.inicio = inicio;
        this.detalle = detalle;
    }

    void cerrar(Instant momento) {
        this.fin = momento;
    }

    public String servicio() {
        return servicio;
    }

    public Instant inicio() {
        return inicio;
    }

    /** Nulo mientras el servicio siga caido. */
    public Instant fin() {
        return fin;
    }

    public String detalle() {
        return detalle;
    }

    public boolean abierta() {
        return fin == null;
    }

    /**
     * Duracion de la interrupcion dentro del periodo pedido.
     *
     * <p>Se recorta a los limites del periodo a proposito: una caida que
     * empezo el mes pasado no debe restarle tiempo a este mes.
     *
     * @param desde inicio del periodo, inclusive
     * @param hasta fin del periodo, exclusivo; tambien cierra las interrupciones abiertas
     */
    public Duration duracionEn(Instant desde, Instant hasta) {
        Instant arranque = inicio.isBefore(desde) ? desde : inicio;
        Instant cierre = fin == null || fin.isAfter(hasta) ? hasta : fin;
        return cierre.isAfter(arranque) ? Duration.between(arranque, cierre) : Duration.ZERO;
    }
}
