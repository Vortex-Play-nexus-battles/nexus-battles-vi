package com.nexusbattles.plataforma.comentarios;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Hilo de opiniones de un producto. HU-COM-001, requisitos RF-COM-001 y RF-COM-002.
 *
 * <p>Aqui viven las tres reglas que la historia describe y que no se pueden dejar al
 * criterio de quien llame. La primera es que un jugador comenta cuantas veces quiera
 * pero califica una sola vez: del segundo comentario en adelante el sistema lo acepta
 * y le quita las estrellas, sin tratarlo como error. La segunda es que un jugador
 * silenciado por sancion no publica. La tercera es que una imagen con formato no
 * admitido tumba la publicacion completa.
 *
 * <p>Hay una diferencia que conviene no perder de vista. El rechazo y la retencion no
 * son lo mismo. Si el autor esta silenciado o la imagen no sirve, el comentario no se
 * guarda y se le explica por que. Si el filtro automatico lo senala, si se guarda,
 * pero queda esperando a un moderador. Al jugador hay que decirle cosas distintas en
 * cada caso.
 *
 * <p>El hilo no consulta el estado de sancion ni ejecuta el filtro: los recibe ya
 * resueltos. Esa decision viene de la regla de plataforma que prohibe que un servicio
 * alcance los datos de otro, y ademas deja la clase probable sin levantar nada.
 *
 * <p>Los formatos de imagen admitidos se reciben al construir el hilo porque el issue
 * los deja pendientes de definir con el Product Owner. Cuando se decidan, se
 * configuran en un solo sitio y esta clase no cambia.
 */
public final class HiloDeComentarios {

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

    /** Motivo por el que una publicacion no llega a guardarse. */
    public enum MotivoDeRechazo {
        /** El autor tiene una sancion activa de silencio. */
        AUTOR_SILENCIADO,
        /** Al menos una imagen viene en un formato que no se admite. */
        FORMATO_DE_IMAGEN_NO_ADMITIDO
    }

    /** Se lanza cuando el comentario no se guarda. Lleva el motivo para explicarselo al autor. */
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

    private final String productoId;
    private final Set<String> formatosAdmitidos;
    private final List<Comentario> comentarios = new ArrayList<>();
    private final Set<String> yaCalificaron = new LinkedHashSet<>();

    private HiloDeComentarios(String productoId, Set<String> formatosAdmitidos) {
        this.productoId = productoId;
        this.formatosAdmitidos = formatosAdmitidos;
    }

    /**
     * Abre el hilo de un producto.
     *
     * @param productoId producto comentado
     * @param formatosAdmitidos extensiones de imagen aceptadas, sin punto y sin importar
     *     mayusculas. Valor pendiente de definir con el Product Owner segun el issue
     * @return un hilo sin comentarios
     */
    public static HiloDeComentarios de(String productoId, Set<String> formatosAdmitidos) {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("el identificador del producto es obligatorio");
        }
        Objects.requireNonNull(formatosAdmitidos, "los formatos admitidos son obligatorios");
        if (formatosAdmitidos.isEmpty()) {
            throw new IllegalArgumentException("hay que admitir al menos un formato de imagen");
        }
        Set<String> normalizados = new LinkedHashSet<>();
        for (String formato : formatosAdmitidos) {
            normalizados.add(formato.toLowerCase(Locale.ROOT));
        }
        return new HiloDeComentarios(productoId, Set.copyOf(normalizados));
    }

    /**
     * Reconstruye el hilo a partir de los comentarios ya guardados de un producto.
     *
     * <p>Existe para la capa de persistencia. El hilo aplica la regla de la
     * calificacion unica recordando quien ya califico, y esa memoria hay que
     * recuperarla de la base de datos antes de atender cada publicacion nueva.
     * Los comentarios retenidos tambien reservan la calificacion de su autor,
     * igual que hace publicar, para que nadie califique dos veces aprovechando
     * que su primer intento quedo en revision.
     *
     * @param productoId producto comentado
     * @param formatosAdmitidos extensiones de imagen aceptadas, sin punto
     * @param existentes comentarios ya guardados del producto, del mas antiguo al mas reciente
     * @return un hilo con la historia cargada, listo para publicar el siguiente
     */
    public static HiloDeComentarios reconstituir(
            String productoId, Set<String> formatosAdmitidos, List<Comentario> existentes) {
        Objects.requireNonNull(existentes, "los comentarios existentes son obligatorios");
        HiloDeComentarios hilo = de(productoId, formatosAdmitidos);
        for (Comentario comentario : existentes) {
            Objects.requireNonNull(comentario, "ningun comentario existente puede ser nulo");
            hilo.comentarios.add(comentario);
            if (comentario.calificacion().isPresent()) {
                hilo.yaCalificaron.add(comentario.autorId());
            }
        }
        return hilo;
    }

    /**
     * Publica un comentario aplicando las reglas de la historia.
     *
     * @param solicitud lo que envio el jugador
     * @param estadoAutor si el jugador puede publicar, resuelto por el modulo de sanciones
     * @param resultadoFiltro veredicto del filtro automatico sobre el texto
     * @return el comentario tal como quedo guardado, que puede venir sin estrellas si el
     *     jugador ya habia calificado, y en revision si el filtro lo senalo
     * @throws PublicacionRechazada si el autor esta silenciado o alguna imagen no se admite
     */
    public Comentario publicar(
            SolicitudDePublicacion solicitud,
            EstadoDeAutor estadoAutor,
            ResultadoDelFiltro resultadoFiltro) {

        Objects.requireNonNull(solicitud, "la solicitud es obligatoria");
        Objects.requireNonNull(estadoAutor, "el estado del autor es obligatorio");
        Objects.requireNonNull(resultadoFiltro, "el resultado del filtro es obligatorio");

        if (estadoAutor == EstadoDeAutor.SILENCIADO) {
            throw new PublicacionRechazada(
                    MotivoDeRechazo.AUTOR_SILENCIADO,
                    "tu cuenta tiene una sancion activa y no puede publicar comentarios");
        }
        for (String imagen : solicitud.imagenes()) {
            if (!formatoAdmitido(imagen)) {
                throw new PublicacionRechazada(
                        MotivoDeRechazo.FORMATO_DE_IMAGEN_NO_ADMITIDO,
                        "el formato de la imagen " + imagen + " no se admite");
            }
        }

        Integer estrellas = solicitud.estrellas();
        if (estrellas != null && yaCalificaron.contains(solicitud.autorId())) {
            estrellas = null;
        }

        Comentario comentario = new Comentario(
                solicitud.comentarioId(),
                productoId,
                solicitud.autorId(),
                solicitud.apodoAutor(),
                solicitud.texto(),
                solicitud.imagenes(),
                estrellas,
                solicitud.fecha(),
                resultadoFiltro == ResultadoDelFiltro.SENALADO
                        ? Comentario.Estado.EN_REVISION
                        : Comentario.Estado.PUBLICADO);

        if (estrellas != null) {
            yaCalificaron.add(solicitud.autorId());
        }
        comentarios.add(comentario);
        return comentario;
    }

    /** Si ese jugador ya gasto su unica calificacion sobre este producto. */
    public boolean yaCalifico(String autorId) {
        return yaCalificaron.contains(autorId);
    }

    /**
     * Calificacion promedio del producto.
     *
     * <p>Solo entran las calificaciones de comentarios publicados. Un comentario retenido
     * por el filtro reserva la calificacion de su autor, para que no pueda calificar dos
     * veces, pero no mueve el promedio mientras un moderador no lo apruebe.
     *
     * @return el promedio, o vacio si todavia nadie ha calificado
     */
    public OptionalDouble promedio() {
        return comentarios.stream()
                .filter(Comentario::estaPublicado)
                .filter(comentario -> comentario.calificacion().isPresent())
                .mapToInt(Comentario::estrellas)
                .average();
    }

    /** Comentarios del hilo, del mas antiguo al mas reciente. */
    public List<Comentario> comentarios() {
        return List.copyOf(comentarios);
    }

    /** Comentarios visibles para los jugadores, sin los retenidos por el filtro. */
    public List<Comentario> visibles() {
        return comentarios.stream().filter(Comentario::estaPublicado).toList();
    }

    private boolean formatoAdmitido(String nombreDeArchivo) {
        int punto = nombreDeArchivo.lastIndexOf(46);
        if (punto < 0 || punto == nombreDeArchivo.length() - 1) {
            return false;
        }
        return formatosAdmitidos.contains(
                nombreDeArchivo.substring(punto + 1).toLowerCase(Locale.ROOT));
    }
}
