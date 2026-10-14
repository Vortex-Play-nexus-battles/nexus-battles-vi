package com.nexusbattles.ms_chatbot.chat.soporte;

// 409 (motivo SIN_CONVERSACION, ms-chatbot.yaml 1.8.0): se pidio una
// solicitud desde la conversacion y el jugador todavia no pregunto nada.
public class SinConversacionException extends RuntimeException {

    public SinConversacionException() {
        super("Todavia no hay una pregunta en la conversacion de la cual partir. Escribe tu solicitud.");
    }
}
