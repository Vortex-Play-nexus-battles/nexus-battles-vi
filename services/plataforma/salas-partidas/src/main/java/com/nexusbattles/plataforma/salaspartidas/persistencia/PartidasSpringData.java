package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso generado por Spring Data. El puerto del dominio lo envuelve. */
interface PartidasSpringData extends JpaRepository<PartidaEntidad, UUID> {

    Optional<PartidaEntidad> findByIdSala(UUID idSala);

    /** Historial de un jugador (1.7.0), de la mas reciente a la mas antigua. */
    @Query(value = "select p from PartidaEntidad p join p.participantes pp "
            + "where pp.idJugador = :jugador order by p.iniciadaEn desc, p.id",
            countQuery = "select count(p) from PartidaEntidad p join p.participantes pp "
                    + "where pp.idJugador = :jugador")
    Page<PartidaEntidad> delJugador(@Param("jugador") UUID jugador, Pageable pagina);

    /** Partidas en curso con el turno agotado (D-B7-14). */
    @Query("select p from PartidaEntidad p where p.estado = :estado "
            + "and p.turnoVenceEn is not null and p.turnoVenceEn <= :ahora")
    List<PartidaEntidad> conTurnoVencido(@Param("estado") EstadoPartida estado, @Param("ahora") Instant ahora);
}
