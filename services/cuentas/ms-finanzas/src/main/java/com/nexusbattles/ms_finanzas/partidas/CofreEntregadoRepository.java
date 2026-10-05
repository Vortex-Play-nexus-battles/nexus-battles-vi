package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CofreEntregadoRepository extends JpaRepository<CofreEntregado, UUID> {

    Page<CofreEntregado> findByUidJugadorOrderByEntregadoEnDesc(String uidJugador, Pageable pagina);

    /** Cofres de un jugador en una semana ISO: el tope es dos (§7.6, cofres.yaml 1.1.0). */
    long countByUidJugadorAndSemanaIso(String uidJugador, String semanaIso);

    /** Entregas pendientes cuyo reintento ya toca, las más antiguas primero (B7). */
    List<CofreEntregado> findTop20ByEstadoEntregaAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEnAsc(
            CofreEntregado.EstadoEntrega estado, Instant ahora);
}
