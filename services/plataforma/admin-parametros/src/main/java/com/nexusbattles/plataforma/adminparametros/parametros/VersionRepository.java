package com.nexusbattles.plataforma.adminparametros.parametros;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionRepository extends JpaRepository<Version, Long> {

    List<Version> findByClaveOrderByVersionDesc(String clave);

    /** SA-5: las versiones que faltan por llegar a la bitacora central y ya pueden intentarse. */
    @org.springframework.data.jpa.repository.Query("select v from Version v where v.auditadoEn is null "
            + "and (v.proximoIntentoDeAuditoria is null or v.proximoIntentoDeAuditoria <= :ahora) "
            + "order by v.cambiadoEn asc")
    List<Version> porAuditar(@org.springframework.data.repository.query.Param("ahora") java.time.OffsetDateTime ahora,
                             org.springframework.data.domain.Pageable pagina);
}
