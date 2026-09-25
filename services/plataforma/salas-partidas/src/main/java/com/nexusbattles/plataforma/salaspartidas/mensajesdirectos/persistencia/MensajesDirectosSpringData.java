package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas sobre mensajes_directos.
 *
 * <p>Todas en JPQL, ninguna nativa: el esquema {@code salas_partidas} lo pone
 * Hibernate ({@code default_schema}) y una consulta nativa sin calificar
 * buscaria la tabla en el {@code search_path} de la conexion, donde no esta.
 */
interface MensajesDirectosSpringData extends JpaRepository<MensajeDirectoEntidad, UUID> {

    Optional<MensajeDirectoEntidad> findByIdRemitenteAndIdCliente(UUID idRemitente, String idCliente);

    List<MensajeDirectoEntidad> findByConversacionOrderByEnviadoEnDescIdDesc(String conversacion, Pageable pagina);

    List<MensajeDirectoEntidad> findByConversacionAndEnviadoEnBeforeOrderByEnviadoEnDescIdDesc(
            String conversacion, Instant antesDe, Pageable pagina);

    /**
     * El ultimo mensaje de cada conversacion del jugador. Si dos comparten el
     * mismo instante salen los dos; el adaptador se queda con uno.
     */
    @Query("""
            SELECT m FROM MensajeDirectoEntidad m
             WHERE (m.idRemitente = :jugador OR m.idDestinatario = :jugador)
               AND m.enviadoEn = (SELECT MAX(otro.enviadoEn) FROM MensajeDirectoEntidad otro
                                   WHERE otro.conversacion = m.conversacion)
            """)
    List<MensajeDirectoEntidad> ultimosPorConversacion(@Param("jugador") UUID jugador);

    @Query("""
            SELECT m.idRemitente AS remitente, COUNT(m) AS cantidad
              FROM MensajeDirectoEntidad m
             WHERE m.idDestinatario = :destinatario AND m.leidoEn IS NULL
             GROUP BY m.idRemitente
            """)
    List<NoLeidosDeUnRemitente> contarNoLeidosPorRemitente(@Param("destinatario") UUID destinatario);

    long countByIdDestinatarioAndIdRemitenteAndLeidoEnIsNull(UUID idDestinatario, UUID idRemitente);

    /** El que abre la racha de no leidos; a igual instante desempata el id, siempre igual. */
    Optional<MensajeDirectoEntidad> findFirstByIdDestinatarioAndIdRemitenteAndLeidoEnIsNullOrderByEnviadoEnAscIdAsc(
            UUID idDestinatario, UUID idRemitente);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MensajeDirectoEntidad m SET m.leidoEn = :cuando
             WHERE m.idDestinatario = :destinatario AND m.idRemitente = :remitente AND m.leidoEn IS NULL
            """)
    int marcarLeidos(@Param("destinatario") UUID destinatario, @Param("remitente") UUID remitente,
                     @Param("cuando") Instant cuando);

    /** Proyeccion del conteo por remitente. */
    interface NoLeidosDeUnRemitente {
        UUID getRemitente();

        long getCantidad();
    }
}
