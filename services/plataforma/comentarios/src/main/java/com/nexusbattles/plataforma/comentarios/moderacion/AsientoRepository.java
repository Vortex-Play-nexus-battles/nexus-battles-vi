package com.nexusbattles.plataforma.comentarios.moderacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** El historico de decisiones de moderacion (RF-COM-008). */
public interface AsientoRepository extends JpaRepository<AsientoDeModeracion, String>,
        JpaSpecificationExecutor<AsientoDeModeracion> {

    /** De la mas antigua a la mas reciente: un historico se lee en orden. */
    List<AsientoDeModeracion> findByComentarioIdOrderByFechaAsc(String comentarioId);

    /** SA-5: los que faltan por llegar a la bitacora central y ya pueden intentarse, del mas antiguo. */
    @org.springframework.data.jpa.repository.Query("select a from AsientoDeModeracion a where a.auditadoEn is null "
            + "and (a.proximoIntentoDeAuditoria is null or a.proximoIntentoDeAuditoria <= :ahora) order by a.fecha asc")
    List<AsientoDeModeracion> porAuditar(@org.springframework.data.repository.query.Param("ahora") java.time.Instant ahora,
                                         org.springframework.data.domain.Pageable pagina);
}
