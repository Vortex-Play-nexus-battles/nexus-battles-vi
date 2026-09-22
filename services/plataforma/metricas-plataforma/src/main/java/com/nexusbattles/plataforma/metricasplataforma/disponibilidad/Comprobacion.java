package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Instant;

/**
 * Resultado de una comprobacion de salud sobre un servicio del bloque.
 *
 * <p>Es el dato crudo del que sale todo lo demas: el estado en vivo (CP-01) y,
 * acumulado, el informe del periodo (CP-02).
 *
 * @param servicio nombre del servicio comprobado, tal como lo declara la configuracion
 * @param instante momento de la comprobacion
 * @param disponible true si el servicio respondio que esta sano
 * @param detalle motivo cuando no lo esta; vacio cuando si
 */
public record Comprobacion(String servicio, Instant instante, boolean disponible, String detalle) {

    public static Comprobacion disponible(String servicio, Instant instante) {
        return new Comprobacion(servicio, instante, true, "");
    }

    public static Comprobacion caido(String servicio, Instant instante, String detalle) {
        return new Comprobacion(servicio, instante, false, detalle == null ? "" : detalle);
    }
}
