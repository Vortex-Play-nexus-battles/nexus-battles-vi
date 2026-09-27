package com.nexusbattles.plataforma.comentarios.imagenes;

import java.time.Instant;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las imagenes de comentario (V5). */
public interface RepositorioDeImagenes extends JpaRepository<RegistroDeImagen, String> {

    /**
     * Cuantas de esas imagenes son del autor y siguen pendientes. Es la
     * comprobacion previa de la publicacion: barata, sin cargar bytes, y antes
     * de preguntar nada a otros servicios.
     */
    @Query("""
            select count(i) from RegistroDeImagen i
            where i.id in :ids and i.autorId = :autorId and i.comentarioId is null
            """)
    long contarDisponibles(@Param("ids") Collection<String> ids, @Param("autorId") String autorId);

    /**
     * Asocia las imagenes al comentario, pero solo las del autor que sigan
     * pendientes. Quien llama compara las filas tocadas con las pedidas: si
     * otra publicacion simultanea del mismo autor uso ya alguna, aqui toca
     * menos filas y la publicacion se deshace. La condicion va en la propia
     * sentencia y no en una lectura previa, por la misma razon que la
     * calificacion unica: la lectura no ve a la otra peticion, el UPDATE si.
     *
     * @return cuantas imagenes quedaron asociadas
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RegistroDeImagen i set i.comentarioId = :comentarioId
            where i.id in :ids and i.autorId = :autorId and i.comentarioId is null
            """)
    int asociar(@Param("ids") Collection<String> ids,
            @Param("autorId") String autorId,
            @Param("comentarioId") String comentarioId);

    /**
     * Borra las que nadie uso a tiempo (contrato 1.4.0: «las no usadas en 24 h
     * se borran»). Un solo DELETE por antiguedad, sin traerlas.
     *
     * @return cuantas se borraron
     */
    @Modifying
    @Query("delete from RegistroDeImagen i where i.comentarioId is null and i.creadaEn < :limite")
    int borrarPendientesAnterioresA(@Param("limite") Instant limite);
}
