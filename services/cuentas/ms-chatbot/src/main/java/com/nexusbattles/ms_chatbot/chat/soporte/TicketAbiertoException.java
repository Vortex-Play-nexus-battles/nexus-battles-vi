package com.nexusbattles.ms_chatbot.chat.soporte;

// 409 (motivo TICKET_ABIERTO, ms-chatbot.yaml 1.3.0): ya hay un ticket
// ABIERTO o EN_PROCESO para esta conversacion.
public class TicketAbiertoException extends RuntimeException {

    public TicketAbiertoException() {
        super("Ya tienes un ticket de soporte abierto. Te avisaremos cuando soporte lo responda.");
    }
}
