package com.nexusbattles.ms_cumplimiento.auditoria.dto;

import java.time.Instant;

/**
 * Cuerpo de {@code POST /api/v1/admin/auditoria/eventos}.
 *
 * <p>1.3.0 (SA-5) suma dos campos opcionales: {@code ocurridoEn}, cuando
 * ocurrio la accion en el servicio de origen (la entrada sigue teniendo su
 * propia {@code fechaHora}, la de su registro aqui), y {@code referencia}, la
 * clave del evento en el origen, que hace idempotente la entrega.
 *
 * <p>1.4.0 (SA-18) suma {@code nombreAfectado}, opcional: el nombre de lo
 * afectado en ese momento (hasta 255 caracteres).
 */
public record RegistrarAuditoriaRequest(
        String tipoAccion,
        String administradorId,
        String afectado,
        String valorAnterior,
        String valorNuevo,
        String motivo,
        String ipOrigen,
        Instant ocurridoEn,
        String referencia,
        String nombreAfectado
) {

    /** La forma de 1.3.0, sin el nombre de lo afectado. */
    public RegistrarAuditoriaRequest(String tipoAccion, String administradorId, String afectado,
                                     String valorAnterior, String valorNuevo, String motivo, String ipOrigen,
                                     Instant ocurridoEn, String referencia) {
        this(tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen, ocurridoEn,
            referencia, null);
    }

    /** La forma anterior a 1.3.0, sin momento ni referencia. */
    public RegistrarAuditoriaRequest(String tipoAccion, String administradorId, String afectado,
                                     String valorAnterior, String valorNuevo, String motivo, String ipOrigen) {
        this(tipoAccion, administradorId, afectado, valorAnterior, valorNuevo, motivo, ipOrigen, null, null, null);
    }
}
