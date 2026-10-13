package com.nexusbattles.ms_chatbot.chat.soporte;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// ms-chatbot.yaml 1.3.0 (RF-ADM-004): bandeja de tickets del administrador.
// Rol exigido por SecurityConfig para todo /chatbot/admin/**.
@RestController
@RequestMapping("/chatbot/admin/tickets")
public class TicketsAdminController {

    private final TicketSoporteAdminService servicio;

    public TicketsAdminController(TicketSoporteAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public PaginaDeTicketsResponse listar(@RequestParam(required = false) EstadoTicket estado,
                                          @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return PaginaDeTicketsResponse.desde(servicio.listar(estado, pagina, tamano));
    }

    @GetMapping("/{ticketId}")
    public TicketAdminResponse obtener(@PathVariable UUID ticketId) {
        return TicketAdminResponse.desde(servicio.obtener(ticketId));
    }

    @PatchMapping("/{ticketId}")
    public TicketAdminResponse atender(@PathVariable UUID ticketId, @RequestBody @Valid AtenderTicketRequest cuerpo) {
        return TicketAdminResponse.desde(servicio.atender(ticketId, cuerpo.getEstado(), cuerpo.getRespuesta(),
            cuerpo.getAsignadoA(), cuerpo.desasignar()));
    }
}
