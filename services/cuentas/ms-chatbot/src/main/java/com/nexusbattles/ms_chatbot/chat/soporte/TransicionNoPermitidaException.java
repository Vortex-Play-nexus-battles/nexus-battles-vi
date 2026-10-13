package com.nexusbattles.ms_chatbot.chat.soporte;

// 409 (motivo TRANSICION_NO_PERMITIDA, ms-chatbot.yaml 1.3.0): el estado
// actual del ticket no permite el cambio pedido.
public class TransicionNoPermitidaException extends RuntimeException {

    public TransicionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
