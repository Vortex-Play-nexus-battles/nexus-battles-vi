package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AsientoDeAuditoriaRepository extends JpaRepository<AsientoDeAuditoria, UUID> {

    /** Los que faltan por entregar y ya pueden intentarse, del mas antiguo al mas reciente. */
    @Query("select a from AsientoDeAuditoria a where a.entregadoEn is null "
            + "and (a.proximoIntentoEn is null or a.proximoIntentoEn <= :ahora) order by a.ocurridoEn asc")
    List<AsientoDeAuditoria> porEntregar(@Param("ahora") OffsetDateTime ahora, Pageable pagina);

    long countByEntregadoEnIsNull();
}
