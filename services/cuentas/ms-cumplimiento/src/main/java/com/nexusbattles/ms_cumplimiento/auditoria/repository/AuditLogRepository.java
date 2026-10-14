package com.nexusbattles.ms_cumplimiento.auditoria.repository;

import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {

    /** SA-5: la entrada que ya trajo esta referencia, si la hay (entrega idempotente). */
    Optional<AuditLog> findByReferencia(String referencia);

    /**
     * SA-18: cuándo ocurrió la última acción importada de un origen (las
     * referencias que empiezan por {@code patron}, p. ej. {@code productos:%});
     * nulo si todavía no hay ninguna. Se consulta una vez al arrancar: después
     * el importador lleva la cuenta.
     */
    @Query("select max(a.ocurridoEn) from AuditLog a where a.referencia like :patron")
    Instant ultimoMomentoConReferencia(@Param("patron") String patron);
}
