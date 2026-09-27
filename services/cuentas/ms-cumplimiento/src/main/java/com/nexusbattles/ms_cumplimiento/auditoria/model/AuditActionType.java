package com.nexusbattles.ms_cumplimiento.auditoria.model;

/**
 * Tipos de accion administrativa que deben quedar auditadas.
 *
 * <p>Los emite ms-identidad por {@code POST /api/v1/admin/auditoria/eventos}
 * como texto ({@code tipoAccion}); un valor que no este aqui responde 400.
 * {@link #SECURITY_BYPASS_ATTEMPT} es el que manda {@code SecurityInterceptor}
 * de ms-identidad ante un intento de saltarse un permiso: faltaba en la
 * lista y cada uno de esos eventos moria con 500 en vez de quedar registrado.
 */
public enum AuditActionType {
    CREACION,
    ACTUALIZACION,
    ELIMINACION_LOGICA,
    SUSPENSION,
    SANCION,
    CAMBIO_ROL,
    APROBACION,
    RECHAZO,
    SECURITY_BYPASS_ATTEMPT,
    // HU-AUD-004: cada exportación del propio registro de auditoría queda
    // registrada con su propio tipo, para poder distinguirla de otros
    // accesos administrativos genéricos (OTRO).
    EXPORTACION,
    OTRO
}
