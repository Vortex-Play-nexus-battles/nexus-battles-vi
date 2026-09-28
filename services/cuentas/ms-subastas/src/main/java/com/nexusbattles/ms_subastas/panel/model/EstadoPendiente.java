package com.nexusbattles.ms_subastas.panel.model;

/** Un producto ganado, desde que se adjudica hasta que llega al inventario (7.7.9). */
public enum EstadoPendiente {

    /** Ganado y pagado; bajo el bloqueo de la subasta hasta que se recoja. */
    PENDIENTE,

    /** El ganador lo recogio: disponible en su inventario. */
    RECOGIDO,

    /** Vencieron los 7 dias con la politica ENTREGAR: disponible en su inventario. */
    ENTREGADO_AL_VENCER,

    /** Vencieron los 7 dias con la politica DEVOLVER_AL_VENDEDOR: volvio al vendedor. */
    DEVUELTO_AL_VENDEDOR
}
