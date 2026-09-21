package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

/**
 * Lo que un servicio del bloque cuenta de si mismo por Actuator — HU-MET-004
 * (RF-MET-004): uso de procesador, memoria, peticiones, latencia media y
 * maxima y errores del servidor. {@code brecha} no nulo = no se pudo
 * recolectar (CA-03: brecha de observabilidad, senalada, nunca disimulada).
 */
public record MetricasDeServicio(String servicio, Double cpu, Double memoriaMb, long peticiones,
                                 Double promedioMs, Double maximoMs, long errores5xx, Double tasaDeError,
                                 String brecha) {

    public MetricasDeServicio(String servicio, Double cpu, Double memoriaMb, long peticiones,
                              Double promedioMs, Double maximoMs, long errores5xx, String brecha) {
        this(servicio, cpu, memoriaMb, peticiones, promedioMs, maximoMs, errores5xx,
                peticiones == 0 ? null : (double) errores5xx / peticiones, brecha);
    }

    public static MetricasDeServicio brecha(String servicio, String motivo) {
        return new MetricasDeServicio(servicio, null, null, 0, null, null, 0, motivo);
    }

    public boolean recolectado() {
        return brecha == null;
    }
}
