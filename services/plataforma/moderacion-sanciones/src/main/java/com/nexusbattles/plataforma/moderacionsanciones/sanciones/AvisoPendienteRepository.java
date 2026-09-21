package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvisoPendienteRepository extends JpaRepository<AvisoPendiente, UUID> {

    List<AvisoPendiente> findByEntregadoEnIsNullOrderByCreadoEnAsc(Pageable pagina);
}
