package com.nexusbattles.ms_identidad.auth.segundofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CodigoDeRecuperacionRepository extends JpaRepository<CodigoDeRecuperacion, UUID> {

    List<CodigoDeRecuperacion> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    long countByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    /** Gasta el codigo solo si nadie lo gasto antes. 0 = ya estaba usado. */
    @Modifying(flushAutomatically = true)
    @Query("update CodigoDeRecuperacion c set c.usadoEn = :ahora where c.id = :id and c.usadoEn is null")
    int marcarUsado(@Param("id") UUID id, @Param("ahora") LocalDateTime ahora);

    /** Borrado en bloque, en el acto (al activar de nuevo o al desactivar). */
    @Modifying(flushAutomatically = true)
    @Query("delete from CodigoDeRecuperacion c where c.usuarioId = :usuarioId")
    int borrarDe(@Param("usuarioId") Long usuarioId);
}
