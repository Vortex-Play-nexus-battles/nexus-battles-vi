package com.nexusbattles.plataforma.observabilidad;

import java.util.List;

/**
 * Resultado de la medicion de consultas a la base de datos (HU-REN-003).
 *
 * @param servicio modulo del que salen las muestras
 * @param muestras cuantas consultas entraron en el calculo
 * @param objetivo objetivo y percentil vigentes
 * @param percentilMs valor del percentil en milisegundos
 * @param maximoMs la consulta mas lenta medida
 * @param umbralLentaMs a partir de cuantos ms una consulta se marca como lenta
 * @param cuantasLentas cuantas consultas superaron ese umbral (CA-03)
 * @param sentenciasMasLentas las peores sentencias, para saber donde mirar
 */
public record InformeDeConsultas(
        String servicio,
        long muestras,
        ObjetivoDeLatencia objetivo,
        long percentilMs,
        long maximoMs,
        long umbralLentaMs,
        long cuantasLentas,
        List<Sentencia> sentenciasMasLentas) {

    /** Latencia agregada de una sentencia concreta. */
    public record Sentencia(String sentencia, long muestras, long percentilMs, long maximoMs) {}

    /**
     * True si el percentil acordado se mantuvo bajo el objetivo.
     *
     * <p>Igual que en el informe de latencia: un informe sin muestras <b>no</b>
     * cumple. Un servicio cuyas consultas nunca se midieron no ha demostrado
     * nada sobre sus indices.
     */
    public boolean cumple() {
        return muestras > 0 && percentilMs <= objetivo.objetivoMs();
    }

    public boolean sinDatos() {
        return muestras == 0;
    }
}
