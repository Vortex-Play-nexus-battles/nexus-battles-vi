package com.nexusbattles.ms_chatbot.chat.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaDeSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaResponse;
import com.nexusbattles.ms_chatbot.chat.soporte.EstadoTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0: TicketDeSoporte, lo que ve el jugador. Sin uid,
// asignado ni contexto: eso es solo del administrador.
//
// 1.14.0 (§7.4.6): `capturas`, las capturas de pantalla que adjunto, sin su
// contenido. Sin ninguna no se escribe, asi que una solicitud sin capturas se
// manda exactamente como antes.
public record TicketResponse(
    UUID ticketId,
    Categoria categoria,
    String asunto,
    String mensaje,
    EstadoTicket estado,
    String respuesta,
    Instant creadoEn,
    Instant actualizadoEn,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CapturaResponse> capturas
) {
    public static TicketResponse desde(TicketSoporte ticket) {
        return desde(ticket, List.of());
    }

    public static TicketResponse desde(TicketSoporte ticket, List<CapturaDeSoporte.Resumen> capturas) {
        return new TicketResponse(ticket.getId(), ticket.getCategoria(), ticket.getAsunto(), ticket.getMensaje(),
            ticket.getEstado(), ticket.getRespuesta(), ticket.getCreadoEn(), ticket.getActualizadoEn(),
            capturas.stream().map(CapturaResponse::desde).toList());
    }
}
