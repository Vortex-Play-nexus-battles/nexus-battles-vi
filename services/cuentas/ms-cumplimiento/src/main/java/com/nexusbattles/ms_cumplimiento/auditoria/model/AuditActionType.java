package com.nexusbattles.ms_cumplimiento.auditoria.model;

/**
 * para acordarme Tipos de acción administrativa que deben quedar auditadas.

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
    OTRO
}