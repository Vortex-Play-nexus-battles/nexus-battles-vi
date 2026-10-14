package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import org.springframework.messaging.Message;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 7.4.9 — el frame {@code ERROR} con el que el canal rechaza un CONNECT, un
 * SUBSCRIBE o un SEND (y despues cierra la conexion, como manda STOMP).
 *
 * <p>Sin esto, el {@code message} del frame era el de la envoltura de Spring
 * («Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]»)
 * y el cliente no sabia por que. Ahora lleva el motivo de
 * {@link PoliticaDelCanal} («Ese destino no existe en el canal del chatbot.»)
 * y, para cualquier otro fallo, un texto fijo: nunca el detalle interno de una
 * excepcion.
 */
public class ErroresDelProtocolo extends StompSubProtocolErrorHandler {

    static final String GENERICO = "El canal del chatbot no pudo procesar ese frame.";

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> mensajeDelCliente, Throwable error) {
        return super.handleClientMessageProcessingError(mensajeDelCliente, new IllegalStateException(motivo(error)));
    }

    static String motivo(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof AccessDeniedException rechazo && rechazo.getMessage() != null) {
                return rechazo.getMessage();
            }
        }
        return GENERICO;
    }
}
