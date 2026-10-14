package com.nexusbattles.ms_cumplimiento.auditoria.repository;

import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, String>, JpaSpecificationExecutor<AuditLog> {

    /** SA-5: la entrada que ya trajo esta referencia, si la hay (entrega idempotente). */
    Optional<AuditLog> findByReferencia(String referencia);
}
