package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

/**
 * Los tipos de accion de la bitacora central que usa este servicio, con el
 * nombre exacto de {@code TipoAccion} en ms-cumplimiento-auditoria.yaml 1.3.0.
 */
public enum TipoDeAccion {
    /** Alta de un termino en la lista negra. */
    CREACION,
    /** Edicion de un termino, o levantamiento de una suspension. */
    ACTUALIZACION,
    /** Baja de un termino de la lista negra (se borra). */
    ELIMINACION,
    /** Emision de una suspension. */
    SUSPENSION,
    /** Emision de una advertencia o de un baneo. */
    SANCION,
    /** Apelacion estimada: la sancion se revierte o se reduce. */
    APROBACION,
    /** Apelacion desestimada: la sancion se mantiene. */
    RECHAZO
}
