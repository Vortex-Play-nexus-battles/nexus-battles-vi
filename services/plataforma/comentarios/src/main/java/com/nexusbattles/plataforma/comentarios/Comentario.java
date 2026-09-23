package com.nexusbattles.plataforma.comentarios;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Opinion publicada por un jugador sobre un producto. HU-COM-001, requisito RF-COM-001.
 *
 * <p>La regla RN-CMT-001 fija que el comentario lleva texto e imagenes, y ademas el
 * apodo de quien lo escribio, su calificacion en estrellas y la fecha de publicacion.
 * Los tres ultimos se guardan aqui y no se calculan despues, porque el apodo puede
 * cambiar con el tiempo y el comentario debe conservar el que tenia ese dia.
 *
 * <p>La calificacion es opcional a proposito. Un jugador puede comentar un producto
 * cuantas veces quiera pero solo puede calificarlo una vez, asi que del segundo
 * comentario en adelante va sin estrellas. Eso no es un error, es el comportamiento
 * que describe la historia.
 *
 * @param id identificador unico del comentario
 * @param productoId producto sobre el que se opina
 * @param autorId jugador que lo escribe
 * @param apodoAutor apodo del jugador en el momento de publicar
 * @param texto contenido escrito
 * @param imagenes adjuntos, puede venir vacia
 * @param estrellas calificacion de 1 a 5, o vacia si el jugador ya habia calificado
 * @param fechaPublicacion momento en que quedo registrado
 * @param estado si quedo publicado o retenido por el filtro
 */
public record Comentario(
        String id,
        String productoId,
        String autorId,
        String apodoAutor,
        String texto,
        List<String> imagenes,
        Integer estrellas,
        Instant fechaPublicacion,
        Comentario.Estado estado) {

    /** Situacion del comentario despues de pasar por el filtro automatico. */
    public enum Estado {
        /** Visible en el hilo del producto. */
        PUBLICADO,
        /** Retenido por el filtro automatico o por un reporte, esperando decision. */
        EN_REVISION,
        /**
         * Retirado por moderacion conservando el registro — RF-COM-008, R10.1.
         *
         * <p>La ficha exige distinguirlo de ELIMINADO: "La accion «Ocultar»
         * debe preservar el registro en base de datos, a diferencia de
         * «Eliminar»". En el modelo eso significa que de OCULTO se vuelve
         * (RESTAURAR) y de ELIMINADO no. Sin esa diferencia, ocultar seria un
         * sinonimo caro de eliminar y el moderador no tendria ninguna accion
         * reversible.
         */
        OCULTO,
        /** Retirado definitivamente: por su autor (HU-COM-004) o por moderacion. */
        ELIMINADO
    }

    public Comentario {
        exigirTexto(id, "el identificador del comentario");
        exigirTexto(productoId, "el identificador del producto");
        exigirTexto(autorId, "el identificador del autor");
        exigirTexto(apodoAutor, "el apodo del autor");
        exigirTexto(texto, "el texto del comentario");
        Objects.requireNonNull(fechaPublicacion, "la fecha de publicacion es obligatoria");
        Objects.requireNonNull(estado, "el estado del comentario es obligatorio");
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
        if (estrellas != null && (estrellas < 1 || estrellas > 5)) {
            throw new IllegalArgumentException(
                    "la calificacion va de 1 a 5 estrellas, llego " + estrellas);
        }
    }

    /** Calificacion asociada, vacia cuando el jugador ya habia calificado el producto. */
    public Optional<Integer> calificacion() {
        return Optional.ofNullable(estrellas);
    }

    /** Si el comentario es visible en el hilo. */
    public boolean estaPublicado() {
        return estado == Estado.PUBLICADO;
    }

    /** Si su autor lo retiro, o moderacion lo retiro definitivamente. */
    public boolean estaEliminado() {
        return estado == Estado.ELIMINADO;
    }

    /**
     * Si esta esperando una decision de moderacion — R10.1.
     *
     * <p>Antes de R10.1 este estado no tenia salida: el filtro automatico metia
     * comentarios aqui y no habia cola donde aparecieran ni accion que los
     * resolviera. El estado existia y el camino de vuelta no.
     */
    public boolean estaEnRevision() {
        return estado == Estado.EN_REVISION;
    }

    /** El mismo comentario en otro estado. El dominio es inmutable. */
    public Comentario con(Estado nuevo) {
        return new Comentario(id, productoId, autorId, apodoAutor, texto, imagenes,
                estrellas, fechaPublicacion, nuevo);
    }

    /** Si es de ese jugador. */
    public boolean esDe(String autor) {
        return autorId.equals(autor);
    }

    /**
     * El mismo comentario, retirado por su autor — HU-COM-004.
     *
     * <p>Se le retira tambien la calificacion: deja de contar en el promedio
     * (CA-01) y libera la unica calificacion del autor sobre el producto, que
     * asi puede volver a calificar en un comentario nuevo (decision D-19).
     */
    public Comentario eliminado() {
        return new Comentario(id, productoId, autorId, apodoAutor, texto, imagenes, null,
                fechaPublicacion, Estado.ELIMINADO);
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
