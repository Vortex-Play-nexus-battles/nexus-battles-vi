package com.nexusbattles.ms_cumplimiento.auditoria.repository;

import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditActionType;
import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() { }

    public static Specification<AuditLog> conAdministrador(String administradorId) {
        if (administradorId == null || administradorId.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("administradorId"), administradorId);
    }

    public static Specification<AuditLog> conTipoAccion(AuditActionType tipoAccion) {
        if (tipoAccion == null) return null;
        return (root, query, cb) -> cb.equal(root.get("tipoAccion"), tipoAccion);
    }

    public static Specification<AuditLog> entreFechas(Instant desde, Instant hasta) {
        if (desde == null && hasta == null) return null;
        return (root, query, cb) -> {
            if (desde != null && hasta != null) {
                return cb.between(root.get("fechaHora"), desde, hasta);
            } else if (desde != null) {
                return cb.greaterThanOrEqualTo(root.get("fechaHora"), desde);
            } else {
                return cb.lessThanOrEqualTo(root.get("fechaHora"), hasta);
            }
        };
    }
}