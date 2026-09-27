package com.nexusbattles.ms_ecommerce.repository;

import com.nexusbattles.ms_ecommerce.model.ListaDeseos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ListaDeseosRepository extends JpaRepository<ListaDeseos, Long> {

    /** La lista del jugador, del mas reciente al mas antiguo; sin las filas legadas sin referencia. */
    List<ListaDeseos> findByUsuarioIdAndProductoRefIsNotNullOrderByAgregadoEnDescIdDesc(String usuarioId);

    Optional<ListaDeseos> findByUsuarioIdAndProductoRef(String usuarioId, String productoRef);

    /** Solo los identificadores, para marcar la vitrina sin cargar filas enteras. */
    @Query("select l.productoRef from ListaDeseos l where l.usuarioId = :usuarioId and l.productoRef is not null")
    List<String> productosDe(@Param("usuarioId") String usuarioId);

    /**
     * Anade el producto si no estaba. Con el indice unico de V3, dos
     * peticiones simultaneas no pueden dejar dos filas, y la segunda no falla:
     * el producto ya esta en la lista, que es lo que pedia.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO lista_deseos (usuario_id, producto_ref, producto_nombre, agregado_en)
            VALUES (:usuarioId, :productoRef, :nombre, :agregadoEn)
            ON CONFLICT (usuario_id, producto_ref) WHERE producto_ref IS NOT NULL DO NOTHING""",
            nativeQuery = true)
    int anadirSiNoEsta(@Param("usuarioId") String usuarioId, @Param("productoRef") String productoRef,
                       @Param("nombre") String nombre, @Param("agregadoEn") Instant agregadoEn);

    @Modifying
    @Transactional
    @Query("delete from ListaDeseos l where l.usuarioId = :usuarioId and l.productoRef = :productoRef")
    int quitar(@Param("usuarioId") String usuarioId, @Param("productoRef") String productoRef);
}
