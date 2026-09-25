package com.nexusbattles.plataforma.comentarios;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Opinion publicada por un jugador sobre un producto. HU-COM-001, requisito RF-COM-001.
 *
 * <p>La regla RN-CMT-001 fija que el comentario lleva texto e imagenes, y ademas el
 * apodo de quien lo escribio, su calificacion en estrellas y la fecha de publicacion.
 * El apodo y la fecha se guardan aqui y no se calculan despues, porque el apodo puede
 * cambiar con el tiempo y el comentario debe conservar el que tenia ese dia.
 *
 * <h2>Las estrellas ya no son del comentario (B3, contrato 1.5.0)</h2>
 *
 * Hasta B3 este registro llevaba sus propias estrellas, y de ahi salian tres
 * defectos que la auditoria de septiembre dejo escritos: no se podia calificar
 * sin escribir, el promedio se calculaba trayendo el hilo entero a memoria, y
 * un comentario ocultado o eliminado por moderacion conservaba las estrellas
 * sin que contaran, bloqueando a su autor para siempre. El 7.1 del documento
 * del curso separa las dos cosas: «solo pueden calificar un producto una vez,
 * pero podran agregar o retirar tantos comentarios como sea de su agrado». La
 * calificacion vive ahora en {@link Calificacion}; las estrellas que se pintan
 * junto a un comentario son las de la calificacion de su autor sobre ese
 * producto, y se buscan al leer, no se copian al escribir.
 *
 * @param id identificador unico del comentario
 * @param productoId producto sobre el que se opina
 * @param autorId jugador que lo escribe
 * @param apodoAutor apodo del jugador en el momento de publicar
 * @param texto contenido escrito
 * @param imagenes identificadores de las imagenes adjuntas (B3), puede venir vacia.
 *     Los comentarios anteriores a B3 conservan aqui los nombres de archivo que
 *     se guardaban entonces, sin imagen detras.
 * @param fechaPublicacion momento en que quedo registrado
 * @param estado si quedo publicado, retenido, oculto o retirado
 * @param editado si un moderador cambio su texto (EDITAR, 7.3.3)
 * @param marcado si un moderador lo senalo para seguimiento especial (MARCAR, 7.3.3)
 */
public record Comentario(
        String id,
        String productoId,
        String autorId,
        String apodoAutor,
        String texto,
        List<String> imagenes,
        Instant fechaPublicacion,
        Comentario.Estado estado,
        boolean editado,
        boolean marcado) {

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
    }

    /** Un comentario recien escrito: sin editar y sin marcar. */
    public Comentario(String id, String productoId, String autorId, String apodoAutor,
            String texto, List<String> imagenes, Instant fechaPublicacion, Estado estado) {
        this(id, productoId, autorId, apodoAutor, texto, imagenes, fechaPublicacion, estado,
                false, false);
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
                fechaPublicacion, nuevo, editado, marcado);
    }

    /**
     * El mismo comentario con el texto que dejo un moderador — EDITAR (7.3.3:
     * «modificar contenido inapropiado manteniendo el contexto del comentario
     * (con registro de la edicion)»).
     *
     * <p>Queda {@code editado} para siempre: quien lo lea tiene que saber que
     * ese texto no es exactamente el que escribio su autor. El texto anterior
     * no se pierde, lo guarda el asiento de moderacion.
     */
    public Comentario editadoCon(String textoNuevo) {
        return new Comentario(id, productoId, autorId, apodoAutor, textoNuevo, imagenes,
                fechaPublicacion, estado, true, marcado);
    }

    /** El mismo comentario con o sin la marca de seguimiento (MARCAR / DESMARCAR, 7.3.3). */
    public Comentario conMarca(boolean marca) {
        return new Comentario(id, productoId, autorId, apodoAutor, texto, imagenes,
                fechaPublicacion, estado, editado, marca);
    }

    /** Si es de ese jugador. */
    public boolean esDe(String autor) {
        return autorId.equals(autor);
    }

    /**
     * El mismo comentario, retirado por su autor — HU-COM-004.
     *
     * <p>Desde B3 retirar un comentario ya no toca la calificacion del autor:
     * el 7.1 dice que se califica una sola vez y que los comentarios se
     * agregan o retiran cuantas veces se quiera. La decision D-19, que al
     * retirar liberaba la calificacion, queda sustituida por esa lectura.
     */
    public Comentario eliminado() {
        return con(Estado.ELIMINADO);
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
