package com.nexusbattles.ms_chatbot.chat.soporte;

// 409 (motivo OTRO_TICKET_ABIERTO, ms-chatbot.yaml 1.3.9): el administrador
// quiere reabrir un ticket y el jugador ya tiene otro ABIERTO o EN_PROCESO.
// Solo puede haber uno por jugador (indice unico parcial de V6); sin esta
// revision, la base lo rechazaba y la respuesta era un 500.
public class OtroTicketAbiertoException extends RuntimeException {

    public OtroTicketAbiertoException() {
        super("El jugador ya tiene otra solicitud abierta. Atiende esa antes de reabrir esta.");
    }
}
