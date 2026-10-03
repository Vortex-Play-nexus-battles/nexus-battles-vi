package com.nexusbattles.plataforma.salaspartidas.dominio;

/**
 * Por que se cerro una sala.
 *
 * <p>Los tres valores son exactamente los del mensaje {@code sala.cancelada} en
 * {@code contracts/websocket/salas-partidas.yaml}. No se anaden mas: un motivo
 * que la interfaz no sabe traducir sale en pantalla como un codigo crudo.
 */
public enum MotivoDeCancelacion {

    /** El anfitrion la cancelo a proposito. Es el unico camino implementado hoy. */
    CANCELADA_POR_ANFITRION,

    /**
     * Se cerro sola por quedarse sin movimiento.
     *
     * <p>Desde la auditoria de DEV del 30-sep tiene productor:
     * {@code CerrarAbandonadas} cierra la sala que nadie empezo pasado el plazo
     * de abandono y devuelve la apuesta. El plazo no se invento: es el
     * vencimiento con que ms-finanzas crea la reserva de la apuesta (72 h), y
     * queda como decision pendiente del PO (D-39).
     */
    INACTIVIDAD,

    /** El sistema no pudo sostener la sala. Sin productor todavia, igual que arriba. */
    ERROR_DEL_SISTEMA
}
