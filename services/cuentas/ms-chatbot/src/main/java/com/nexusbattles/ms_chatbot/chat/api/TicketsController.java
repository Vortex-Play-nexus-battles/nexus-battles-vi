package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// ms-chatbot.yaml 1.3.0 (7.4.3, RF-CHA-007): tickets de soporte del jugador.
// Vive en chat.api para que ManejadorErroresDelChat traduzca sus errores
// (401, 409, 422, 429, 503) con `motivo`, igual que los del chat.
//
// Solo con token: la identidad sale del uid del token (ResolutorDeIdentidad).
// X-Id-Sesion-Anonima no se lee aqui: un visitante no abre tickets, y asi
// ninguna cabecera puede hacerse pasar por un jugador.
@RestController
@RequestMapping("/chat/tickets")
public class TicketsController {

    private final TicketSoporteService servicio;
    private final ResolutorDeIdentidad resolutor;

    public TicketsController(TicketSoporteService servicio, ResolutorDeIdentidad resolutor) {
        this.servicio = servicio;
        this.resolutor = resolutor;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> abrir(@RequestBody @Valid AbrirTicketRequest request,
                                                Authentication authentication) {
        TicketSoporte ticket = servicio.abrir(jugador(authentication), request.categoria(), request.asunto(),
            request.mensaje());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.desde(ticket));
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> misTickets(Authentication authentication) {
        List<TicketResponse> mios = servicio.misTickets(jugador(authentication)).stream()
            .map(TicketResponse::desde)
            .toList();
        return ResponseEntity.ok(mios);
    }

    // null si no hay token valido; el servicio responde entonces 401.
    private IdentidadDelChat jugador(Authentication authentication) {
        return resolutor.resolver(authentication, null)
            .filter(IdentidadDelChat::autenticado)
            .orElse(null);
    }
}
