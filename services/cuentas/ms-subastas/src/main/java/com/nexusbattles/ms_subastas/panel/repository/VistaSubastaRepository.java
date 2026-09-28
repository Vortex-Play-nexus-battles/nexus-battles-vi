package com.nexusbattles.ms_subastas.panel.repository;

import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Visualizaciones unicas de una subasta (7.7.9 «Estadisticas de
 * visualizaciones», y el orden por popularidad del listado). Una por jugador
 * con sesion; el vendedor no cuenta. Sin entidad propia: la tabla solo se
 * escribe y se cuenta por la columna {@code subastas.vistas}.
 */
public interface VistaSubastaRepository extends Repository<Subasta, UUID> {

    /** @return 1 si es la primera vez que este jugador la ve, 0 si no */
    @Modifying
    @Transactional
    @Query(value = """
            insert into vistas_subasta (subasta_id, jugador_id, vista_en)
            values (:subastaId, :jugadorId, :ahora)
            on conflict (subasta_id, jugador_id) do nothing
            """, nativeQuery = true)
    int registrarSiEsNueva(@Param("subastaId") UUID subastaId, @Param("jugadorId") UUID jugadorId,
                           @Param("ahora") Instant ahora);

    @Modifying
    @Transactional
    @Query("update Subasta s set s.vistas = s.vistas + 1 where s.id = :subastaId")
    int sumarVista(@Param("subastaId") UUID subastaId);
}
