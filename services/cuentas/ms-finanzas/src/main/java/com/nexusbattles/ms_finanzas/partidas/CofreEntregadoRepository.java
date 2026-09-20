package com.nexusbattles.ms_finanzas.partidas;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CofreEntregadoRepository extends JpaRepository<CofreEntregado, UUID> {

    Page<CofreEntregado> findByUidJugadorOrderByEntregadoEnDesc(String uidJugador, Pageable pagina);
}
