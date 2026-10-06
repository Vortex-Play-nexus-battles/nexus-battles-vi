package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.EstadoTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;

import java.time.Instant;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0: TicketDeSoporte, lo que ve el jugador. Sin uid,
// asignado ni contexto: eso es solo del administrador.
public record TicketResponse(
    UUID ticketId,
    Categoria categoria,
    String asunto,
    String mensaje,
    EstadoTicket estado,
    String respuesta,
    Instant creadoEn,
    Instant actualizadoEn
) {
    public static TicketResponse desde(TicketSoporte ticket) {
        return new TicketResponse(ticket.getId(), ticket.getCategoria(), ticket.getAsunto(), ticket.getMensaje(),
            ticket.getEstado(), ticket.getRespuesta(), ticket.getCreadoEn(), ticket.getActualizadoEn());
    }
}
