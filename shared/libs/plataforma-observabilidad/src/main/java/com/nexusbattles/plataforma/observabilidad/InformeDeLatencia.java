package com.nexusbattles.plataforma.observabilidad;

import java.util.List;

/**
 * Resultado de la medicion de latencia (HU-REN-001, CA-02).
 *
 * <p>Es lo que se exporta como evidencia en el informe tecnico: cuantas
 * peticiones se midieron, el percentil acordado con el Product Owner, y si ese
 * percentil se mantuvo bajo el objetivo.
 *
 * @param muestras cuantas peticiones entraron en el calculo
 * @param objetivo objetivo y percentil vigentes
 * @param percentilMs valor del percentil en milisegundos
 * @param maximoMs la peticion mas lenta medida
 * @param operacionesMasLentas las peores operaciones, para saber donde mirar
 */
public record InformeDeLatencia(
        long muestras,
        ObjetivoDeLatencia objetivo,
        long percentilMs,
        long maximoMs,
        List<Operacion> operacionesMasLentas) {

    /** Latencia agregada de una operacion concreta. */
    public record Operacion(String metodo, String ruta, long muestras, long percentilMs) {}

    /**
     * True si el percentil acordado se mantuvo bajo el objetivo.
     *
     * <p>Un informe sin muestras <b>no</b> cumple: no medir no es lo mismo que
     * cumplir, y darlo por bueno dejaria pasar un servicio que nunca se
     * instrumento.
     */
    public boolean cumple() {
        return muestras > 0 && percentilMs <= objetivo.objetivoMs();
    }

    public boolean sinDatos() {
        return muestras == 0;
    }
}
