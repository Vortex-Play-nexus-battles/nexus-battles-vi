package com.nexusbattles.plataforma.observabilidad;

/**
 * Objetivo de latencia contra el que se evalua la medicion (HU-REN-001).
 *
 * <h2>Los dos numeros tienen origen distinto</h2>
 *
 * <p>Los <b>500 ms</b> no los decide el equipo: vienen fijados por RNF-REN-001
 * y por el Project Charter, que los declara inalterables.
 *
 * <p>El <b>percentil</b> si lo decide el equipo, y esta decidido: <b>p95</b>
 * (ADR-006). Hasta septiembre de 2026 no tenia valor por omision porque se
 * leyo CA-03 como si el Product Owner tuviera que aprobar p95 o p99 por
 * escrito. Esa lectura se reviso: ningun documento oficial del proyecto fija
 * el percentil —el Charter, {@code CLAUDE.md} y el catalogo de parametros
 * hablan de 500 ms y de nada mas—, y elegir en que percentil se comprueba un
 * umbral es una convencion de medicion de ingenieria, no una decision de
 * producto. Esperar una firma para algo que nadie iba a firmar dejaba
 * RNF-REN-001 sin poder evaluarse nunca.
 *
 * <p>Sigue siendo configurable por {@code LATENCIA_PERCENTIL}: pasar a p99
 * para una campaña de medicion concreta no exige recompilar. Lo que cambia es
 * que ya no hay un estado «sin decidir».
 *
 * @param objetivoMs latencia maxima aceptable, en milisegundos
 * @param percentil percentil de evaluacion, entre 0 y 100 exclusive
 */
public record ObjetivoDeLatencia(long objetivoMs, double percentil) {

    /** Los 500 ms de RNF-REN-001 y del Project Charter. Inalterables. */
    public static final long OBJETIVO_POR_OMISION_MS = 500;

    /**
     * p95 — convencion de medicion del equipo, ADR-006.
     *
     * <p>p95 y no p99 porque con las ventanas que este registro mantiene
     * (10 000 muestras por proceso, en memoria) un p99 se calcula sobre las
     * 100 peores muestras y una sola pausa del recolector de basura lo mueve;
     * p95 se apoya en 500 y describe la experiencia tipica sin quedar a
     * merced del ruido. Subirlo a 99 es un cambio de variable de entorno, no
     * de codigo.
     */
    public static final double PERCENTIL_POR_OMISION = 95;

    public ObjetivoDeLatencia {
        if (objetivoMs <= 0) {
            throw new IllegalArgumentException(
                    "el objetivo de latencia debe ser positivo, y llego " + objetivoMs);
        }
        if (percentil <= 0 || percentil >= 100) {
            throw new IllegalArgumentException(
                    "el percentil debe estar entre 0 y 100 exclusive, y llego " + percentil
                            + ". Por omision es p" + (long) PERCENTIL_POR_OMISION
                            + " (ADR-006); para cambiarlo, LATENCIA_PERCENTIL.");
        }
    }

    /** Nombre legible del percentil para el informe: 95.0 -> «p95». */
    public String nombre() {
        return percentil == Math.rint(percentil)
                ? "p" + (long) percentil
                : "p" + percentil;
    }
}
