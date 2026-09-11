package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Informe de disponibilidad de un periodo (HU-DIS-001, CP-02).
 *
 * <p>Es exactamente lo que el ejemplo de la historia pide poder anexar al
 * informe de avance: «la plataforma estuvo disponible 167 h 52 min de 168 h,
 * equivalente al 99,92 %», con las interrupciones que lo explican.
 *
 * @param desde inicio del periodo medido
 * @param hasta fin del periodo medido
 * @param periodo duracion total del periodo
 * @param umbral porcentaje minimo acordado en DEC-01
 * @param servicios una linea por servicio del bloque
 */
public record InformeDeDisponibilidad(
        Instant desde, Instant hasta, Duration periodo, double umbral, List<LineaDeServicio> servicios) {

    /**
     * @param disponible tiempo disponible dentro del periodo
     * @param indisponible tiempo caido, ya descontado el mantenimiento programado
     * @param porcentaje disponibilidad con dos decimales
     * @param interrupciones tramos de caida que tocan el periodo
     */
    public record LineaDeServicio(
            String servicio,
            Duration disponible,
            Duration indisponible,
            double porcentaje,
            List<Interrupcion> interrupciones) {

        public boolean cumple(double umbral) {
            return porcentaje >= umbral;
        }
    }

    /**
     * Disponibilidad del bloque: la del peor servicio.
     *
     * <p>No es el promedio a proposito. Si un servicio del bloque esta caido,
     * el jugador no puede jugar; promediarlo con los que si respondian
     * maquillaria la cifra que se le entrega al cliente.
     */
    public double porcentajeDelBloque() {
        return servicios.stream()
                .mapToDouble(LineaDeServicio::porcentaje)
                .min()
                .orElse(100d);
    }

    /** True cuando todos los servicios del bloque alcanzan el umbral (CP-03). */
    public boolean cumpleElUmbral() {
        return porcentajeDelBloque() >= umbral;
    }

    /** Servicios que quedaron por debajo del umbral; vacio si ninguno. */
    public List<LineaDeServicio> porDebajoDelUmbral() {
        return servicios.stream().filter(linea -> !linea.cumple(umbral)).toList();
    }
}
