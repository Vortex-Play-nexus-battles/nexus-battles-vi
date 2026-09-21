package com.nexusbattles.plataforma.torneos.torneo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TorneoRepository extends JpaRepository<Torneo, UUID> {

    List<Torneo> findAllByOrderByCreadoEnDesc();

    /** El torneo mas reciente que cuenta para la ventana de 91 dias (los cancelados no cuentan). */
    Optional<Torneo> findFirstByEstadoNotAndCreadoEnAfterOrderByCreadoEnDesc(Torneo.Estado estado, OffsetDateTime desde);
}
