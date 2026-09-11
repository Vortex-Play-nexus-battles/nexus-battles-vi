package com.nexusbattles.ms_subastas.pujas.repository;

import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PujaRepository extends JpaRepository<Puja, UUID> {

    /** La puja que hoy es la oferta vigente de la subasta. */
    Optional<Puja> findBySubastaIdAndEstado(UUID subastaId, EstadoPuja estado);

    /** Cuantas pujas del jugador siguen siendo oferta vigente (tope de 50). */
    int countByJugadorIdAndEstado(UUID jugadorId, EstadoPuja estado);

    /** Su ultima puja en cualquier subasta, para validar el intervalo minimo de 5 s. */
    Optional<Puja> findFirstByJugadorIdOrderByCreadaEnDesc(UUID jugadorId);

    /**
     * En cuantas subastas DISTINTAS participa activamente, excluyendo la que
     * esta pujando ahora (tope de 10). Se excluye la actual para que volver a
     * pujar en una subasta en la que ya participa no cuente como una nueva.
     */
    @Query("""
            select count(distinct p.subastaId) from Puja p
            where p.jugadorId = :jugadorId
              and p.estado = :estado
              and p.subastaId <> :subastaExcluida
            """)
    int contarSubastasActivasExcluyendo(@Param("jugadorId") UUID jugadorId,
                                        @Param("estado") EstadoPuja estado,
                                        @Param("subastaExcluida") UUID subastaExcluida);
}
