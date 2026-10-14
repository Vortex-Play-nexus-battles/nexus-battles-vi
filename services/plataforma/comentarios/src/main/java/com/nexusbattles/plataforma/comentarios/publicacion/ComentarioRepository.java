package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.nexusbattles.plataforma.comentarios.Comentario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComentarioRepository extends JpaRepository<RegistroDeComentario, String> {

    /**
     * Una pagina del hilo publico — B3, contrato 1.5.0.
     *
     * <p>Sustituye a {@code findByProductoIdOrderByFechaPublicacionAsc}, que
     * traia el producto entero a memoria en cada lectura y en cada
     * publicacion. El orden lo pone el {@link Pageable} (fecha descendente y
     * luego id, para que dos comentarios del mismo instante no bailen entre
     * paginas) y lo sirve el indice {@code idx_comentarios_hilo} de V5. Son dos
     * consultas —la pagina y el total— por mucho que crezca el hilo.
     */
    Page<RegistroDeComentario> findByProductoIdAndEstado(
            String productoId, Comentario.Estado estado, Pageable pagina);

    /**
     * Los que esperan decision de moderacion — R10.1, RF-COM-005.
     *
     * <p>Se consulta por estado en la base y no trayendo la tabla entera para
     * filtrarla en Java: funcionaria con veinte comentarios y seria un
     * problema con veinte mil, y la cola de moderacion es justo lo que nadie
     * vuelve a mirar hasta que crece.
     */
    List<RegistroDeComentario> findByEstadoOrderByFechaPublicacionAsc(Comentario.Estado estado);

    List<RegistroDeComentario> findByEstadoAndProductoIdOrderByFechaPublicacionAsc(
            Comentario.Estado estado, String productoId);

    /** Los que esperan decision y no estan marcados (cola con {@code marcado=false}). */
    List<RegistroDeComentario> findByEstadoAndMarcadoOrderByFechaPublicacionAsc(
            Comentario.Estado estado, boolean marcado);

    List<RegistroDeComentario> findByEstadoAndMarcadoAndProductoIdOrderByFechaPublicacionAsc(
            Comentario.Estado estado, boolean marcado, String productoId);

    /**
     * La lista de seguimiento especial (cola con {@code marcado=true}, 7.3.3):
     * los marcados en cualquiera de los estados que se pueden seguir mirando.
     */
    List<RegistroDeComentario> findByMarcadoTrueAndEstadoInOrderByFechaPublicacionAsc(
            Collection<Comentario.Estado> estados);

    List<RegistroDeComentario> findByMarcadoTrueAndEstadoInAndProductoIdOrderByFechaPublicacionAsc(
            Collection<Comentario.Estado> estados, String productoId);

    /**
     * Los reportados que siguen a la vista (contrato 1.8.0): la cola los pide
     * por id —los que tienen reportes pendientes— y en un estado concreto.
     */
    List<RegistroDeComentario> findByIdInAndEstadoOrderByFechaPublicacionAsc(
            Collection<String> ids, Comentario.Estado estado);

    /**
     * Una pagina del historial de un autor — HU-COM-005, contrato 1.7.0.
     *
     * <p>En cualquier estado, tambien OCULTO y ELIMINADO: moderacion los conserva
     * y son justo lo que el moderador quiere ver del autor. El orden lo pone el
     * {@link Pageable} (fecha descendente y luego id) y lo sirve
     * {@code idx_comentarios_por_autor} de V6. Devuelve la proyeccion, no la
     * entidad, para no cargar las imagenes que el historial no muestra.
     */
    Page<ResumenDeComentario> findByAutorId(String autorId, Pageable pagina);

    /**
     * El hilo de un producto antes de un instante, en cualquier estado — el
     * contexto de un comentario para moderacion (contrato 1.17.0, SA-16,
     * §7.3.8). El orden y el limite los pone el {@link Pageable}: del mas
     * cercano al mas lejano. Proyeccion: sin las imagenes.
     */
    List<ResumenDeComentario> findByProductoIdAndFechaPublicacionBefore(
            String productoId, Instant fecha, Pageable limite);

    /** Lo mismo, despues del instante (contrato 1.17.0). */
    List<ResumenDeComentario> findByProductoIdAndFechaPublicacionAfter(
            String productoId, Instant fecha, Pageable limite);

    /** Cuantos comentarios tiene el hilo de un producto, en cualquier estado (contrato 1.17.0). */
    long countByProductoId(String productoId);

    /**
     * Solo los identificadores de los comentarios de un autor, en cualquier
     * estado (contrato 1.11.0): los reportes que ha RECIBIDO son los de estos
     * comentarios. Proyeccion de una columna, sin cargar la entidad.
     */
    List<IdDeComentario> findIdByAutorId(String autorId);

    /**
     * Lo que la ficha administrativa muestra del comentario de un reporte
     * (contrato 1.11.0), sin cargar las imagenes de la entidad.
     */
    List<ComentarioReportado> findByIdIn(Collection<String> ids);

    /** Proyeccion: el identificador de un comentario. */
    interface IdDeComentario {
        String getId();
    }

    /** Proyeccion: el comentario de un reporte, para la ficha administrativa. */
    interface ComentarioReportado {
        String getId();

        String getProductoId();

        String getAutorId();

        String getApodoAutor();

        String getTexto();

        Comentario.Estado getEstado();
    }

    /**
     * Solo el estado de un comentario, sin cargarlo: es lo unico que hace falta
     * para decidir si una de sus imagenes es publica.
     */
    @Query("select c.estado from RegistroDeComentario c where c.id = :id")
    Optional<Comentario.Estado> estadoDe(@Param("id") String id);
}
