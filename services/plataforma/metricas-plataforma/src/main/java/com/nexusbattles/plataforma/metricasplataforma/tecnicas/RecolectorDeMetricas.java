package com.nexusbattles.plataforma.metricasplataforma.tecnicas;

/** Puerto: de donde salen las metricas de un servicio (Actuator en produccion, un doble en pruebas). */
public interface RecolectorDeMetricas {

    /**
     * @param servicio nombre del servicio del bloque
     * @param urlDeSalud URL de su {@code /actuator/health}; el recolector deriva de ahi el resto
     */
    MetricasDeServicio recolectar(String servicio, String urlDeSalud);
}
