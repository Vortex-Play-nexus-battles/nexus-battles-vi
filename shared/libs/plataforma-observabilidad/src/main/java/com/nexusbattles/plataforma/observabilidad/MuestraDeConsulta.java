package com.nexusbattles.plataforma.observabilidad;

import java.time.Instant;

/**
 * Una consulta a la base de datos, medida (HU-REN-003, CA-01).
 *
 * <p>La sentencia se guarda tal como la recibe el driver, es decir con los
 * marcadores {@code ?} en lugar de los valores. Eso es deliberado por dos
 * razones: agrupa todas las ejecuciones de la misma consulta —sin marcadores,
 * cada busqueda de un jugador distinto seria una consulta distinta y no habria
 * percentil que calcular— y evita que valores del jugador acaben en la bitacora
 * o en el informe.
 *
 * @param servicio modulo que ejecuto la consulta
 * @param sentencia SQL con marcadores, nunca con valores
 * @param duracionMs lo que tardo el motor en responder
 * @param instante cuando se ejecuto
 * @param fallo si la consulta termino en excepcion
 */
public record MuestraDeConsulta(
        String servicio, String sentencia, long duracionMs, Instant instante, boolean fallo) {

    /** Cuanto de la sentencia se conserva; lo demas no aporta al informe. */
    public static final int LARGO_MAXIMO = 500;

    public MuestraDeConsulta {
        if (duracionMs < 0) {
            throw new IllegalArgumentException("la duracion no puede ser negativa, y llego " + duracionMs);
        }
        sentencia = normalizar(sentencia);
    }

    /**
     * Deja la sentencia en una linea y acotada.
     *
     * <p>El SQL que genera Hibernate viene con saltos de linea y sangrias; sin
     * normalizar, la misma consulta formateada distinto contaria como dos.
     */
    static String normalizar(String sentencia) {
        if (sentencia == null || sentencia.isBlank()) {
            return "(sentencia desconocida)";
        }
        String limpia = sentencia.replaceAll("\\s+", " ").trim();
        return limpia.length() <= LARGO_MAXIMO ? limpia : limpia.substring(0, LARGO_MAXIMO) + " …";
    }

    /** True si la consulta tardo mas de lo aceptable (CA-03). */
    public boolean lenta(long umbralMs) {
        return duracionMs > umbralMs;
    }
}
