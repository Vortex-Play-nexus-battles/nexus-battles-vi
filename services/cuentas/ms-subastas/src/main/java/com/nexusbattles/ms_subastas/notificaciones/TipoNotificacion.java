package com.nexusbattles.ms_subastas.notificaciones;

/**
 * Solo los dos avisos que HU-SUB-004 exige explicitamente. No se anaden tipos
 * "por si acaso": cada uno obliga al microservicio de notificaciones a saber
 * como renderizarlo.
 */
public enum TipoNotificacion {
    /** Criterio 2: la compra inmediata cierra la subasta "notificando a quienes hubieran pujado". */
    SUBASTA_CERRADA_POR_COMPRA_INMEDIATA,
    /** Criterio 4: la puja automatica "se detiene notificando al alcanzar el limite". */
    LIMITE_AUTOMATICO_ALCANZADO
}
