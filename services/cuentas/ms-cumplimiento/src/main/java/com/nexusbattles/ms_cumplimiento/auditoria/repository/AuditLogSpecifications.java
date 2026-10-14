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

    /**
     * 1.3.0: cualquiera de varios administradores. La misma persona figura con
     * su apodo en las entradas de ms-identidad y con su {@code uid} en las de
     * los servicios de plataforma. Coincidencia exacta; vacio no filtra.
     */
    public static Specification<AuditLog> conAdministradores(java.util.Collection<String> administradores) {
        if (administradores == null) return null;
        java.util.List<String> valores = administradores.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
        if (valores.isEmpty()) return null;
        return (root, query, cb) -> root.get("administradorId").in(valores);
    }

    /**
     * La cuenta o el comentario afectado (contrato 1.2.0). Admite varios
     * valores porque {@code afectado} guarda a veces el {@code uid} y a veces
     * la clave numerica de la misma cuenta: la ficha administrativa pide los
     * dos a la vez. Coincidencia exacta.
     */
    public static Specification<AuditLog> conAfectado(java.util.Collection<String> afectados) {
        if (afectados == null) return null;
        java.util.List<String> valores = afectados.stream()
            .filter(v -> v != null && !v.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
        if (valores.isEmpty()) return null;
        return (root, query, cb) -> root.get("afectado").in(valores);
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