package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.util.UUID;

/**
 * Entrega en tiempo real — puerto hacia el broker STOMP (B6).
 *
 * <p>Por la cola de usuario {@code /usuario/cola/mensajes-directos}
 * ({@code contracts/websocket/mensajes-directos.yaml}): al destinatario y al
 * remitente, que puede tener otras pestanas abiertas y en esta necesita el eco
 * para dar el mensaje por entregado.
 */
public interface EntregaDeMensajesDirectos {

    /** Al destinatario y al remitente. */
    void entregar(MensajeDirecto mensaje);

    /**
     * Solo el eco al remitente: un reintento de un envio que ya se guardo. El
     * destinatario ya lo recibio la primera vez.
     */
    void reenviarEco(MensajeDirecto mensaje);

    /**
     * Si ese jugador tiene ahora mismo alguna sesion escuchando su cola de
     * mensajes privados. Si no, el mensaje le espera en el historial y se le
     * avisa en su bandeja de notificaciones.
     */
    boolean estaEscuchando(UUID jugador);
}
