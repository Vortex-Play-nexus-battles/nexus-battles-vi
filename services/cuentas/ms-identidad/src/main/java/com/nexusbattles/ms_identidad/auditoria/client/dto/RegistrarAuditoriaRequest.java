package com.nexusbattles.ms_identidad.auditoria.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo de {@code POST /api/v1/admin/auditoria/eventos}
 * (ms-cumplimiento-auditoria.yaml). {@code referencia} (1.3.0, opcional) es la
 * clave del evento en este servicio: con una ya registrada, ms-cumplimiento
 * devuelve la entrada que habia en vez de duplicarla. Sin ella no se envia.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrarAuditoriaRequest(
    String tipoAccion,
    String administradorId,
    String afectado,
    String valorAnterior,
    String valorNuevo,
    String motivo,
    String ipOrigen,
    String referencia
) {

    public RegistrarAuditoriaRequest(String tipoAccion, String administradorId, String afectado,
                                     String valorAnterior, String valorNuevo, String motivo, String ipOrigen) {
        this(tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen, null);
    }
}
