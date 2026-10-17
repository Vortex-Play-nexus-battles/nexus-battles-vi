package com.nexusbattles.ms_cumplimiento.auditoria.dto;

import com.nexusbattles.ms_cumplimiento.auditoria.model.AuditLog;

import java.time.Instant;

/**
 * Una entrada de la bitacora. {@code ocurridoEn} y {@code referencia} (1.3.0)
 * y {@code nombreAfectado} (1.4.0, SA-18) son nulos en las entradas que no los
 * traian.
 */
public record AuditLogResponse(
        String id,
        Instant fechaHora,
        String administrador,
        String tipoAccion,
        String afectado,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        String ipOrigen,
        Instant ocurridoEn,
        String referencia,
        String nombreAfectado
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getFechaHora(),
                a.getAdministradorId(),
                a.getTipoAccion().name(),
                a.getAfectado(),
                a.getValorAnterior(),
                a.getValorNuevo(),
                a.getMotivo(),
                a.getIpOrigen(),
                a.getOcurridoEn(),
                a.getReferencia(),
                a.getNombreAfectado()
        );
    }
}
