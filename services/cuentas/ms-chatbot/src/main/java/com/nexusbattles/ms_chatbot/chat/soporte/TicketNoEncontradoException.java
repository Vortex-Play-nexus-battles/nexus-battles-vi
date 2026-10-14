package com.nexusbattles.ms_chatbot.chat.soporte;

// 404 (motivo SOLICITUD_NO_ENCONTRADA, ms-chatbot.yaml 1.14.0): no existe, o
// no es de quien pregunta. Las dos cosas responden igual: asi no se puede
// averiguar si existe una solicitud ajena.
public class TicketNoEncontradoException extends RuntimeException {

    public TicketNoEncontradoException() {
        super("No encontramos esa solicitud entre las tuyas.");
    }
}
