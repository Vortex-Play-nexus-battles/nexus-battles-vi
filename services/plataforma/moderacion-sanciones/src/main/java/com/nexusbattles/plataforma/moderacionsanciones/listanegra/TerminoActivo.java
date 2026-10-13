package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lo que la verificacion necesita de un termino activo, y lo unico que se
 * guarda en la cache (Redis). Es {@link Serializable} por eso: la cache usa la
 * serializacion de la JVM, y guardar la entidad JPA arrastraria su estado de
 * persistencia.
 *
 * @param termino     tal como lo escribio quien lo dio de alta (lo que ve un
 *                    moderador en {@code coincidencias})
 * @param normalizado su forma compacta ({@link NormalizadorDeTexto})
 * @param categoria   del 7.1.1
 * @param modo        como casa
 * @param id          el de la fila en la lista negra: la «regla» que se
 *                    registra en cada deteccion (HU-COM-007, RFINAL-02) y que
 *                    la respuesta de verificacion da a quien ve el detalle.
 *                    {@code null} solo en terminos construidos fuera de la
 *                    base (pruebas).
 */
public record TerminoActivo(String termino, String normalizado, CategoriaDeTermino categoria,
                            ModoDeCoincidencia modo, Long id) implements Serializable {

    public TerminoActivo {
        Objects.requireNonNull(termino);
        Objects.requireNonNull(normalizado);
        Objects.requireNonNull(categoria);
        Objects.requireNonNull(modo);
    }

    /** Un termino sin fila (pruebas y usos puros del detector). */
    public TerminoActivo(String termino, String normalizado, CategoriaDeTermino categoria,
                         ModoDeCoincidencia modo) {
        this(termino, normalizado, categoria, modo, null);
    }

    static TerminoActivo desde(TerminoProhibido termino) {
        return new TerminoActivo(termino.termino(), termino.normalizado(), termino.categoria(), termino.modo(),
                termino.id());
    }
}
