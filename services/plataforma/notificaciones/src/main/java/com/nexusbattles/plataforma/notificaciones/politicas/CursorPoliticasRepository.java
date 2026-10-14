package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * El cursor de cambios en las politicas por jugador (V4), con las mismas
 * sentencias que el del catalogo: cada escritura es una sola sentencia en su
 * transaccion corta, y el cursor solo avanza ({@code GREATEST}) aunque dos
 * sesiones del mismo jugador escriban a la vez.
 */
public interface CursorPoliticasRepository extends JpaRepository<RegistroDeCursorPoliticas, String> {

    /** Crea el cursor o lo avanza, decidido por la base. @return filas escritas (1) */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO {h-schema}cursor_cambios_politica AS c (usuario_id, hasta, consultado_en)
            VALUES (:usuarioId, :hasta, :consultadoEn)
            ON CONFLICT (usuario_id) DO UPDATE
               SET hasta = GREATEST(c.hasta, EXCLUDED.hasta),
                   consultado_en = EXCLUDED.consultado_en
            """)
    int guardar(@Param("usuarioId") String usuarioId,
                @Param("hasta") Instant hasta,
                @Param("consultadoEn") Instant consultadoEn);

    /** Anota una consulta sin lote (admin-parametros no respondio) sin mover {@code hasta}. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RegistroDeCursorPoliticas c set c.consultadoEn = :consultadoEn where c.usuarioId = :usuarioId")
    int marcarConsulta(@Param("usuarioId") String usuarioId, @Param("consultadoEn") Instant consultadoEn);
}
