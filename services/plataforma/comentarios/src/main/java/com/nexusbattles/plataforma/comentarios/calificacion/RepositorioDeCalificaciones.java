package com.nexusbattles.plataforma.comentarios.calificacion;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Las calificaciones de la tabla de V5, la unica fuente del promedio desde B3. */
public interface RepositorioDeCalificaciones extends JpaRepository<RegistroDeCalificacion, String> {

    /**
     * Inserta la calificacion si el jugador aun no tiene una sobre ese producto.
     *
     * <p>Es la pieza que hace segura la regla de «una sola vez» ante dos
     * peticiones simultaneas: {@code ON CONFLICT DO NOTHING} sobre la
     * restriccion {@code uk_calificacion_por_producto_y_autor}. Si otra
     * transaccion inserto la misma pareja y aun no confirmo, PostgreSQL hace
     * esperar a esta y, cuando la otra confirma, esta no inserta nada. Decide
     * la restriccion, no una lectura previa que la otra peticion no veia.
     *
     * <p>Con {@code DO NOTHING} la transaccion sigue sana (un
     * {@code INSERT} que choca y lanza la dejaria marcada para deshacer), que
     * es lo que permite que un comentario con estrellas se guarde aunque la
     * calificacion ya existiera: entra con {@code calificacionDescartada}.
     *
     * <p>{@code {h-schema}} lo sustituye Hibernate por el esquema por omision
     * ({@code comentarios}): una sentencia nativa no pasa por el mapeo de
     * entidades y sin el calificador buscaria la tabla en {@code public}.
     *
     * @return 1 si la inserto, 0 si ya habia una
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO {h-schema}calificaciones (id, producto_id, autor_id, estrellas, creada_en)
            VALUES (:id, :productoId, :autorId, :estrellas, :creadaEn)
            ON CONFLICT (producto_id, autor_id) DO NOTHING
            """)
    int insertarSiNoExiste(
            @Param("id") String id,
            @Param("productoId") String productoId,
            @Param("autorId") String autorId,
            @Param("estrellas") int estrellas,
            @Param("creadaEn") Instant creadaEn);

    Optional<RegistroDeCalificacion> findByProductoIdAndAutorId(String productoId, String autorId);

    /** Las de los autores de una pagina del hilo, en una sola consulta. */
    List<RegistroDeCalificacion> findByProductoIdAndAutorIdIn(String productoId, Collection<String> autores);

    /**
     * Cuantas calificaciones hay de cada numero de estrellas: todo lo que el
     * resumen necesita, agregado por la base en una consulta.
     */
    @Query("""
            select c.estrellas as estrellas, count(c) as cantidad
            from RegistroDeCalificacion c
            where c.productoId = :productoId
            group by c.estrellas
            """)
    List<ConteoPorEstrellas> contarPorEstrellas(@Param("productoId") String productoId);

    /** Una fila del conteo agregado. */
    interface ConteoPorEstrellas {

        Integer getEstrellas();

        Long getCantidad();
    }
}
