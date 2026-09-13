package com.nexusbattles.plataforma.observabilidad;

import java.time.Instant;

/**
 * Una peticion medida de extremo a extremo (HU-REN-001, RNF-REN-001).
 *
 * <p>Lleva los campos que el informe tecnico necesita para ser evidencia y no
 * un numero suelto: sin la ruta no se sabe que operacion fue lenta, y sin el
 * codigo de respuesta no se distingue una peticion lenta de una que fallo
 * rapido.
 *
 * @param servicio nombre del microservicio que atendio la peticion
 * @param metodo metodo HTTP
 * @param ruta plantilla de la ruta, no la URI con valores
 * @param estado codigo de respuesta HTTP
 * @param duracionMs tiempo total de la peticion en milisegundos
 * @param instante momento en que termino de atenderse
 */
public record MuestraDeLatencia(
        String servicio, String metodo, String ruta, int estado, long duracionMs, Instant instante) {

    public MuestraDeLatencia {
        if (duracionMs < 0) {
            throw new IllegalArgumentException("una duracion no puede ser negativa: " + duracionMs);
        }
    }

    /** True si la peticion termino en error del servidor. */
    public boolean fallo() {
        return estado >= 500;
    }
}
