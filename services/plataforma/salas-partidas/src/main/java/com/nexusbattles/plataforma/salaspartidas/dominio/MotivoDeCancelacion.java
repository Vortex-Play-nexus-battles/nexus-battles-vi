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
     * <p>Declarado en el contrato y todavia sin productor: no existe requisito
     * que fije el plazo de inactividad, y elegir uno por nuestra cuenta seria
     * inventar un umbral. Queda aqui para que el dia que se decida no haya que
     * tocar el contrato.
     */
    INACTIVIDAD,

    /** El sistema no pudo sostener la sala. Sin productor todavia, igual que arriba. */
    ERROR_DEL_SISTEMA
}
