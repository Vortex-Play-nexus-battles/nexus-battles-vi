package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import jakarta.validation.constraints.Size;

// ms-chatbot.yaml 1.8.0: AbrirTicketDesdeConversacion. Todo opcional: de quien
// es la conversacion lo dice el token, nunca este cuerpo.
public record AbrirTicketDesdeConversacionRequest(
    VistaDelChat vista,
    @Size(max = 2000) String comentario
) {

    static final AbrirTicketDesdeConversacionRequest VACIA = new AbrirTicketDesdeConversacionRequest(null, null);
}
