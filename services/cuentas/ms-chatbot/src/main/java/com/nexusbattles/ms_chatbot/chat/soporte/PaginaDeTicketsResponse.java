package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0: PaginaDeTickets. Cada ticket sin uid ni contexto: la
// bandeja no necesita la conversacion; el detalle (GET /{ticketId}) si.
public record PaginaDeTicketsResponse(List<Resumen> contenido, int pagina, int tamano, long totalElementos) {

    public record Resumen(UUID ticketId, Categoria categoria, String asunto, String mensaje, EstadoTicket estado,
                          String respuesta, Instant creadoEn, Instant actualizadoEn) {

        static Resumen desde(TicketSoporte ticket) {
            return new Resumen(ticket.getId(), ticket.getCategoria(), ticket.getAsunto(), ticket.getMensaje(),
                ticket.getEstado(), ticket.getRespuesta(), ticket.getCreadoEn(), ticket.getActualizadoEn());
        }
    }

    public static PaginaDeTicketsResponse desde(Page<TicketSoporte> pagina) {
        return new PaginaDeTicketsResponse(pagina.getContent().stream().map(Resumen::desde).toList(),
            pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }
}
