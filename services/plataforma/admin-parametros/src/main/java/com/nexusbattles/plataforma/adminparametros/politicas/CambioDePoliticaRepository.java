package com.nexusbattles.plataforma.adminparametros.politicas;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CambioDePoliticaRepository extends JpaRepository<CambioDePolitica, UUID> {

    /** El historial, lo mas reciente primero. */
    List<CambioDePolitica> findAllByOrderByPublicadoEnDescIdDesc(Pageable pagina);

    /** Lo publicado despues de {@code desde}, en el orden en que se publico (para la bandeja). */
    @Query("select c from CambioDePolitica c where c.publicadoEn > :desde order by c.publicadoEn asc, c.id asc")
    List<CambioDePolitica> posterioresA(@Param("desde") OffsetDateTime desde, Pageable pagina);

    /** Los que faltan por llegar a la bitacora central y ya pueden intentarse. */
    @Query("select c from CambioDePolitica c where c.auditadoEn is null "
            + "and (c.proximoIntentoDeAuditoria is null or c.proximoIntentoDeAuditoria <= :ahora) "
            + "order by c.publicadoEn asc")
    List<CambioDePolitica> porAuditar(@Param("ahora") OffsetDateTime ahora, Pageable pagina);
}
