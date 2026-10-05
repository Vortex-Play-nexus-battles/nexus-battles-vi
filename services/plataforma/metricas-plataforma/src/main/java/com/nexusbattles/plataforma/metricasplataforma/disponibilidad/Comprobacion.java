package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;

/**
 * Resultado de una comprobacion de salud sobre un servicio del bloque.
 *
 * <p>Es el dato crudo del que sale todo lo demas: el estado en vivo (CP-01) y,
 * acumulado, el informe del periodo (CP-02).
 *
 * <p>{@code esperaAgotada} separa, entre los que no estan disponibles, al que
 * conecto pero no contesto a tiempo (RFINAL-08). Para la cifra de
 * disponibilidad de HU-DIS-001 no cambia nada: sigue contando como NO
 * disponible, igual que antes. Lo usa la pantalla «Sistema» para decir LENTO
 * en vez de CAIDO.
 *
 * @param servicio nombre del servicio comprobado, tal como lo declara la configuracion
 * @param instante momento de la comprobacion
 * @param disponible true si el servicio respondio que esta sano
 * @param detalle motivo cuando no lo esta; vacio cuando si
 * @param esperaAgotada true si conecto y la respuesta no llego dentro del plazo
 */
public record Comprobacion(String servicio, Instant instante, boolean disponible, String detalle,
                           boolean esperaAgotada) {

    public Comprobacion(String servicio, Instant instante, boolean disponible, String detalle) {
        this(servicio, instante, disponible, detalle, false);
    }

    public static Comprobacion disponible(String servicio, Instant instante) {
        return new Comprobacion(servicio, instante, true, "");
    }

    public static Comprobacion caido(String servicio, Instant instante, String detalle) {
        return new Comprobacion(servicio, instante, false, detalle == null ? "" : detalle);
    }

    /** Conecto y no contesto a tiempo: no disponible, y lento antes que caido. */
    public static Comprobacion sinRespuesta(String servicio, Instant instante, String detalle) {
        return new Comprobacion(servicio, instante, false, detalle == null ? "" : detalle, true);
    }
}
