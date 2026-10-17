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
    // 1.3.0 (SA-5): lo que se borra de verdad, como un termino de la lista
    // negra. ELIMINACION_LOGICA queda para lo que se marca y se conserva.
    ELIMINACION,
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
    // SA-18 (1.4.0): las operaciones del catalogo que importa la bitacora.
    // Reactivar un producto suspendido y devolverlo a un estado anterior no
    // son una actualizacion cualquiera: se distinguen al leer y al filtrar.
    REACTIVACION,
    REVERSION,
    OTRO
}
