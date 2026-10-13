package com.nexusbattles.plataforma.notificaciones.catalogo;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * El cursor de avisos del catalogo por jugador (V3). Cada escritura es una
 * sola sentencia en su propia transaccion corta: el importador corre fuera de
 * transaccion porque en medio llama a productos por HTTP.
 */
public interface CursorCatalogoRepository extends JpaRepository<RegistroDeCursorCatalogo, String> {

    /**
     * Crea el cursor o lo avanza, decidido por la base: {@code ON CONFLICT}
     * sobre la clave primaria y {@code GREATEST} sobre {@code hasta}.
     *
     * <p>Dos sesiones del mismo jugador pueden importar a la vez (dos
     * pestanas). Ninguna espera a la otra: si las dos insertan el primer
     * cursor no hay error de clave repetida, y si la que llega tarde trae un
     * {@code hasta} anterior, el cursor no retrocede. {@code {h-schema}} lo
     * pone Hibernate (el esquema {@code notificaciones}); sin el, una sentencia
     * nativa buscaria la tabla en {@code public}.
     *
     * @return filas escritas (1)
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO {h-schema}cursor_alertas_catalogo AS c (usuario_id, hasta, consultado_en)
            VALUES (:usuarioId, :hasta, :consultadoEn)
            ON CONFLICT (usuario_id) DO UPDATE
               SET hasta = GREATEST(c.hasta, EXCLUDED.hasta),
                   consultado_en = EXCLUDED.consultado_en
            """)
    int guardar(@Param("usuarioId") String usuarioId,
                @Param("hasta") Instant hasta,
                @Param("consultadoEn") Instant consultadoEn);

    /**
     * Anota una consulta que no trajo lote (productos no respondio) sin mover
     * {@code hasta}: asi el intervalo minimo tambien protege a un productos
     * caido de recibir una consulta por cada pagina que abre el jugador.
     *
     * @return filas cambiadas (0 si el jugador aun no tiene cursor)
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RegistroDeCursorCatalogo c set c.consultadoEn = :consultadoEn where c.usuarioId = :usuarioId")
    int marcarConsulta(@Param("usuarioId") String usuarioId, @Param("consultadoEn") Instant consultadoEn);
}
