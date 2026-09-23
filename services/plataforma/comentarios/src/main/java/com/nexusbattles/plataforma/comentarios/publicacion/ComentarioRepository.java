package com.nexusbattles.plataforma.comentarios.publicacion;

import java.util.List;

import com.nexusbattles.plataforma.comentarios.Comentario;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComentarioRepository extends JpaRepository<RegistroDeComentario, String> {

    List<RegistroDeComentario> findByProductoIdOrderByFechaPublicacionAsc(String productoId);

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
}
