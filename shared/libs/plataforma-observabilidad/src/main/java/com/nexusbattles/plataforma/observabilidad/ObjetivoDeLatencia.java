package com.nexusbattles.plataforma.observabilidad;

/**
 * Objetivo de latencia contra el que se evalua la medicion (HU-REN-001).
 *
 * <p><b>El percentil no tiene valor por omision, y es deliberado.</b> El
 * criterio CA-03 dice que el Product Owner debe aprobar <i>por escrito</i> si
 * el requisito se evalua en p95 o en p99, y hasta que eso ocurra elegir uno
 * seria inventar la decision. Por eso hay que darlo siempre —por variable de
 * entorno {@code LATENCIA_PERCENTIL}— y cambiarlo no exige recompilar.
 *
 * <p>Los 500 ms si tienen valor por omision: ese numero no lo decide el equipo,
 * viene fijado por RNF-REN-001 y por el Project Charter.
 *
 * @param objetivoMs latencia maxima aceptable, en milisegundos
 * @param percentil percentil de evaluacion, entre 0 y 100 exclusive
 */
public record ObjetivoDeLatencia(long objetivoMs, double percentil) {

    /** Los 500 ms de RNF-REN-001. El percentil no tiene equivalente: lo decide el PO. */
    public static final long OBJETIVO_POR_OMISION_MS = 500;

    public ObjetivoDeLatencia {
        if (objetivoMs <= 0) {
            throw new IllegalArgumentException(
                    "el objetivo de latencia debe ser positivo, y llego " + objetivoMs);
        }
        if (percentil <= 0 || percentil >= 100) {
            throw new IllegalArgumentException(
                    "el percentil debe estar entre 0 y 100 exclusive, y llego " + percentil
                            + ". Lo aprueba el Product Owner (CA-03 de HU-REN-001): "
                            + "configuralo en LATENCIA_PERCENTIL, no lo fijes en el codigo.");
        }
    }

    /** Nombre legible del percentil para el informe: 95.0 -> «p95». */
    public String nombre() {
        return percentil == Math.rint(percentil)
                ? "p" + (long) percentil
                : "p" + percentil;
    }
}
