package com.nexusbattles.ms_chatbot.chat.soporte;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0 (RF-ADM-004): bandeja de tickets del administrador.
// Rol exigido por SecurityConfig para todo /chatbot/admin/**.
@RestController
@RequestMapping("/chatbot/admin/tickets")
public class TicketsAdminController {

    private final TicketSoporteAdminService servicio;
    private final CapturasDeSoporteService capturas;

    public TicketsAdminController(TicketSoporteAdminService servicio, CapturasDeSoporteService capturas) {
        this.servicio = servicio;
        this.capturas = capturas;
    }

    @GetMapping
    public PaginaDeTicketsResponse listar(@RequestParam(required = false) EstadoTicket estado,
                                          @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return PaginaDeTicketsResponse.desde(servicio.listar(estado, pagina, tamano));
    }

    @GetMapping("/{ticketId}")
    public TicketAdminResponse obtener(@PathVariable UUID ticketId) {
        return conCapturas(servicio.obtener(ticketId));
    }

    @PatchMapping("/{ticketId}")
    public TicketAdminResponse atender(@PathVariable UUID ticketId, @RequestBody @Valid AtenderTicketRequest cuerpo) {
        return conCapturas(servicio.atender(ticketId, cuerpo.getEstado(), cuerpo.getRespuesta(),
            cuerpo.getAsignadoA(), cuerpo.desasignar()));
    }

    // ms-chatbot.yaml 1.14.0 (§7.4.6): el contenido de una captura de la
    // solicitud, para leerla al atenderla.
    @GetMapping("/{ticketId}/capturas/{capturaId}")
    public ResponseEntity<byte[]> captura(@PathVariable UUID ticketId, @PathVariable UUID capturaId) {
        try {
            return RespuestaDeCaptura.de(capturas.deCualquiera(ticketId, capturaId));
        } catch (TicketNoEncontradoException sinEsaCaptura) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa captura en esa solicitud.");
        }
    }

    private TicketAdminResponse conCapturas(TicketSoporte ticket) {
        return TicketAdminResponse.desde(ticket,
            capturas.deLasSolicitudes(List.of(ticket.getId())).getOrDefault(ticket.getId(), List.of()));
    }
}
