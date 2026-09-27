package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SalidaPendienteRepository extends JpaRepository<SalidaPendiente, UUID> {

    /** Las que tocan ahora: sin entregar y cuya espera ya paso, de la mas antigua a la mas reciente. */
    @Query("select s from SalidaPendiente s where s.entregadoEn is null "
            + "and (s.proximoIntentoEn is null or s.proximoIntentoEn <= :ahora) order by s.creadoEn asc")
    List<SalidaPendiente> porEntregar(@Param("ahora") OffsetDateTime ahora, Pageable pagina);

    /** Todas las sin entregar, sin mirar la espera (diagnostico y pruebas). */
    List<SalidaPendiente> findByEntregadoEnIsNullOrderByCreadoEnAsc(Pageable pagina);

    List<SalidaPendiente> findBySancionIdOrderByCreadoEnAsc(UUID sancionId);
}
