package com.nexusbattles.plataforma.comentarios;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Las reglas del hilo de opiniones de un producto. HU-COM-001, requisitos RF-COM-001 y
 * RF-COM-002; reescrito en B3 (contrato 1.5.0).
 *
 * <p>
 * Aqui viven las reglas que la historia describe y que no se pueden dejar al
 * criterio de quien llame: un jugador silenciado por sancion no publica, y lo
 * que el filtro automatico senala se guarda pero queda esperando a un
 * moderador. Retirar un comentario es cosa de su autor y de nadie mas.
 *
 * <p>
 * Hay una diferencia que conviene no perder de vista. El rechazo y la retencion
 * no son lo mismo. Si el autor esta silenciado, el comentario no se guarda y se
 * le explica por que. Si el filtro automatico lo senala, si se guarda, pero
 * queda esperando a un moderador. Al jugador hay que decirle cosas distintas en
 * cada caso.
 *
 * <h2>Que cambio en B3 y por que</h2>
 *
 * <p>Hasta B3 esta clase era un hilo con estado: para publicar un comentario
 * se traian de la base <i>todos</i> los comentarios del producto, se
 * reconstruia el hilo en memoria y se decidia sobre el. Hacia falta para una
 * sola regla —«califica una sola vez»—, porque las estrellas vivian dentro de
 * cada comentario y la unica forma de saber si alguien ya habia calificado
 * era mirarlos todos. Con la calificacion separada en su propia tabla y una
 * restriccion unica que la protege, esa memoria sobra: cada publicacion es una
 * decision sobre una sola solicitud, y el hilo se lee paginado desde la base.
 *
 * <p>Las imagenes tampoco se deciden aqui por la extension del nombre de
 * archivo: desde B3 se suben aparte, se comprueban por su firma de bytes y el
 * comentario solo lleva sus identificadores ({@link SolicitudDePublicacion}).
 *
 * <p>
 * El hilo no consulta el estado de sancion ni ejecuta el filtro: los recibe. La
 * sancion llega resuelta y el filtro llega como un {@link Supplier}, para que
 * el orden de las comprobaciones siga siendo decision del dominio —primero el
 * silencio, despues el filtro— sin gastar una llamada a la lista negra en un
 * autor que no va a publicar de todos modos.
 */
public final class HiloDeComentarios {

    /** Contrato 1.4.0: «maximo 3» imagenes por comentario. */
    public static final int MAXIMO_DE_IMAGENES = 3;

    /** Situacion disciplinaria del autor, resuelta por el modulo de sanciones. */
    public enum EstadoDeAutor {
        /** Puede publicar. */
        HABILITADO,
        /** Tiene una sancion activa que le impide publicar. */
        SILENCIADO
    }

    /** Veredicto del filtro automatico de contenido sobre el texto. */
    public enum ResultadoDelFiltro {
        /** No se detecto nada, el comentario sale al hilo. */
        LIMPIO,
        /** Se detecto contenido senalado, el comentario queda en revision. */
        SENALADO
    }

    /** Motivo por el que una publicacion o una calificacion no llega a guardarse. */
    public enum MotivoDeRechazo {
        /** El autor tiene una sancion activa de silencio. */
        AUTOR_SILENCIADO
    }

    /**
     * Se lanza cuando el comentario no se guarda. Lleva el motivo para explicarselo
     * al autor.
     */
    public static final class PublicacionRechazada extends RuntimeException {

        private final transient MotivoDeRechazo motivo;

        public PublicacionRechazada(MotivoDeRechazo motivo, String explicacion) {
            super(explicacion);
            this.motivo = motivo;
        }

        public MotivoDeRechazo motivo() {
            return motivo;
        }
    }

    /** El comentario no esta en este hilo (o nunca existio). */
    public static final class ComentarioNoEncontrado extends RuntimeException {
        public ComentarioNoEncontrado(String comentarioId) {
            super("no hay ningun comentario " + comentarioId + " en este producto");
        }
    }

    /** El comentario es de otro jugador: solo su autor puede retirarlo. */
    public static final class ComentarioAjeno extends RuntimeException {
        public ComentarioAjeno(String comentarioId) {
            super("el comentario " + comentarioId + " no es tuyo");
        }
    }

    /**
     * Las imagenes que trae el comentario no se pueden adjuntar (400, contrato
     * 1.4.0): no son identificadores de imagen, son mas de tres, se repiten, o
     * alguna no es del autor o ya la usa otro comentario.
     */
    public static final class ImagenesNoValidas extends RuntimeException {
        public ImagenesNoValidas(String explicacion) {
            super(explicacion);
        }
    }

    private HiloDeComentarios() {
    }

    /**
     * Decide que queda de una solicitud de publicacion.
     *
     * @param productoId      producto comentado, ya comprobado contra el catalogo
     * @param solicitud       lo que envio el jugador, ya validado en su forma
     * @param estadoAutor     si el jugador puede publicar, resuelto por el modulo
     *                        de sanciones
     * @param filtro          veredicto del filtro automatico sobre el texto; solo
     *                        se consulta si el autor puede publicar
     * @return el comentario tal como hay que guardarlo: publicado, o en revision
     *         si el filtro lo senalo
     * @throws PublicacionRechazada si el autor esta silenciado
     */
    public static Comentario publicar(
            String productoId,
            SolicitudDePublicacion solicitud,
            EstadoDeAutor estadoAutor,
            Supplier<ResultadoDelFiltro> filtro) {

        Objects.requireNonNull(solicitud, "la solicitud es obligatoria");
        Objects.requireNonNull(estadoAutor, "el estado del autor es obligatorio");
        Objects.requireNonNull(filtro, "el filtro es obligatorio");

        if (estadoAutor == EstadoDeAutor.SILENCIADO) {
            throw new PublicacionRechazada(
                    MotivoDeRechazo.AUTOR_SILENCIADO,
                    "tu cuenta tiene una sancion activa y no puede publicar comentarios");
        }
        ResultadoDelFiltro veredicto = Objects.requireNonNull(
                filtro.get(), "el filtro tiene que dar un veredicto");

        return new Comentario(
                solicitud.comentarioId(),
                productoId,
                solicitud.autorId(),
                solicitud.apodoAutor(),
                solicitud.texto(),
                solicitud.imagenes(),
                solicitud.fecha(),
                veredicto == ResultadoDelFiltro.SENALADO
                        ? Comentario.Estado.EN_REVISION
                        : Comentario.Estado.PUBLICADO);
    }

    /**
     * Retira un comentario propio — HU-COM-004.
     *
     * <p>Idempotente: retirar uno ya retirado devuelve el mismo, sin error
     * (CA-03). El de otro jugador es {@link ComentarioAjeno} (CA-02). La
     * calificacion del autor no se toca (ver {@link Comentario#eliminado()}).
     *
     * @return el comentario ya retirado; el mismo objeto si ya lo estaba
     */
    public static Comentario retirar(Comentario comentario, String autorId) {
        Objects.requireNonNull(comentario, "el comentario es obligatorio");
        Objects.requireNonNull(autorId, "hace falta saber quien retira el comentario");
        if (!comentario.esDe(autorId)) {
            throw new ComentarioAjeno(comentario.id());
        }
        return comentario.estaEliminado() ? comentario : comentario.eliminado();
    }
}
