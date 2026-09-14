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

    /**
     * Si la peticion fue de lectura o de escritura (HU-REN-002).
     *
     * <p>La restriccion de esa historia lo pide literalmente: «los datos
     * capturados deben etiquetarse correctamente para diferenciar entre
     * consultas de lectura y operaciones de escritura». Y tiene sentido
     * medirlas por separado: un listado que tarda 300 ms es aceptable y una
     * puja que tarda 300 ms no lo es, porque el jugador esta compitiendo
     * contra otros por el mismo objeto. Mezclarlas en un solo percentil
     * esconde justo la que importa.
     *
     * <p>Se decide por el metodo HTTP y no por la ruta: es la unica regla que
     * vale igual para los veinte modulos sin que nadie tenga que mantener una
     * lista de rutas.
     */
    public TipoDeOperacion tipo() {
        return TipoDeOperacion.deMetodo(metodo);
    }
}
