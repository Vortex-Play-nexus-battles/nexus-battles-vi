package com.nexusbattles.ms_chatbot.chat.soporte;

// 409 (motivo SOLICITUD_CERRADA, ms-chatbot.yaml 1.14.0): a una solicitud
// respondida o cerrada ya no se le adjunta nada; si hace falta, se abre otra.
public class TicketCerradoException extends RuntimeException {

    public TicketCerradoException() {
        super("Esta solicitud ya se respondio o se cerro: abre otra si lo necesitas.");
    }
}
