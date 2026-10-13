package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0: TicketDeSoporteAdmin (lo que ve el administrador):
// lo del jugador mas uid, asignadoA y el contexto ya redactado.
public record TicketAdminResponse(
    UUID ticketId,
    Categoria categoria,
    String asunto,
    String mensaje,
    EstadoTicket estado,
    String respuesta,
    Instant creadoEn,
    Instant actualizadoEn,
    String uid,
    String asignadoA,
    List<Contexto> contexto
) {
    public record Contexto(String remitente, String contenido, Instant fechaEnvio) {
    }

    public static TicketAdminResponse desde(TicketSoporte ticket) {
        return new TicketAdminResponse(ticket.getId(), ticket.getCategoria(), ticket.getAsunto(),
            ticket.getMensaje(), ticket.getEstado(), ticket.getRespuesta(), ticket.getCreadoEn(),
            ticket.getActualizadoEn(), ticket.getUid(), ticket.getAsignadoA(),
            ticket.getContexto().stream()
                .map(m -> new Contexto(m.getRemitente(), m.getContenido(), m.getFechaEnvio()))
                .toList());
    }
}
