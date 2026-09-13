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
     * Latencia agregada de un tipo de operacion (HU-REN-002).
     *
     * <p>Separar lecturas de escrituras no es cosmetico: un listado que tarda
     * 300 ms es aceptable y una puja que tarda 300 ms no lo es, porque el
     * jugador esta compitiendo contra otros por el mismo objeto. En un
     * percentil unico, las lecturas —que son muchisimas mas— entierran a las
     * escrituras y el numero deja de decir nada sobre lo que duele.
     *
     * @param tipo lectura, escritura u otra
     * @param muestras cuantas peticiones de ese tipo se midieron
     * @param percentilMs percentil de ese tipo
     * @param maximoMs la mas lenta de ese tipo
     */
    public record ResumenPorTipo(TipoDeOperacion tipo, long muestras, long percentilMs, long maximoMs) {

        /** Nombre para el informe y el panel. */
        public String etiqueta() {
            return tipo.etiqueta();
        }
    }

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
