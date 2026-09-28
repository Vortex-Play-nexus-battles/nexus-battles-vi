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
 */
public record TerminoActivo(String termino, String normalizado, CategoriaDeTermino categoria,
                            ModoDeCoincidencia modo) implements Serializable {

    public TerminoActivo {
        Objects.requireNonNull(termino);
        Objects.requireNonNull(normalizado);
        Objects.requireNonNull(categoria);
        Objects.requireNonNull(modo);
    }

    static TerminoActivo desde(TerminoProhibido termino) {
        return new TerminoActivo(termino.termino(), termino.normalizado(), termino.categoria(), termino.modo());
    }
}
