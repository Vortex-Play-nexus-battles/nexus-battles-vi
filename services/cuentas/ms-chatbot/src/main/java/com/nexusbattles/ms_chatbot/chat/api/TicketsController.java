package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// ms-chatbot.yaml 1.3.0 (7.4.3, RF-CHA-007): tickets de soporte del jugador.
// Vive en chat.api para que ManejadorErroresDelChat traduzca sus errores
// (401, 409, 422, 429, 503) con `motivo`, igual que los del chat.
//
// La identidad la resuelve el servidor (ResolutorDeIdentidad): un jugador por
// el uid de su token y, desde 1.12.0 (RF-CHA-007, actores Visitante y
// Jugador), un visitante por su sesion vigente. Con un token valido la
// cabecera X-Id-Sesion-Anonima se ignora, y una sesion nunca abre las
// solicitudes de un jugador: son de otro espacio de claves (V5). Sin ninguna
// de las dos, 401 SESION_REQUERIDA; al listar, una sesion con forma que aun
// no esta registrada o ya vencio no tiene solicitudes (lista vacia).
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
                                                Authentication authentication,
                                                @RequestHeader(value = ChatController.CABECERA_SESION_ANONIMA,
                                                    required = false) String sesion,
                                                HttpServletRequest peticion) {
        TicketSoporte ticket = servicio.abrir(paraAbrir(authentication, sesion, peticion), request.categoria(),
            request.asunto(), request.mensaje());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.desde(ticket));
    }

    // ms-chatbot.yaml 1.8.0: la solicitud en un clic tras un escalamiento o un
    // «no me sirvio». El cuerpo es opcional y no elige la conversacion.
    @PostMapping("/desde-conversacion")
    public ResponseEntity<TicketResponse> abrirDesdeConversacion(
        @RequestBody(required = false) @Valid AbrirTicketDesdeConversacionRequest request,
        Authentication authentication,
        @RequestHeader(value = ChatController.CABECERA_SESION_ANONIMA, required = false) String sesion,
        HttpServletRequest peticion) {
        AbrirTicketDesdeConversacionRequest datos = request == null ? AbrirTicketDesdeConversacionRequest.VACIA : request;
        TicketSoporte ticket = servicio.abrirDesdeConversacion(paraAbrir(authentication, sesion, peticion),
            datos.vista(), datos.comentario());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.desde(ticket));
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> misTickets(Authentication authentication,
                                                           @RequestHeader(value = ChatController.CABECERA_SESION_ANONIMA,
                                                               required = false) String sesion) {
        IdentidadDelChat quien = identidad(authentication, sesion);
        if (quien == null && (SesionesAnonimas.esEmitida(sesion) || SesionesAnonimas.esDeclarada(sesion))) {
            // Un visitante cuya sesion aun no esta registrada (no ha escrito
            // ni pedido nada) o ya vencio: no tiene solicitudes que ver.
            return ResponseEntity.ok(List.of());
        }
        List<TicketResponse> mios = servicio.misTickets(quien).stream()
            .map(TicketResponse::desde)
            .toList();
        return ResponseEntity.ok(mios);
    }

    // El jugador del token o el visitante de una sesion vigente; null si no
    // hay ninguno y el servicio responde entonces 401.
    private IdentidadDelChat identidad(Authentication authentication, String sesion) {
        return resolutor.resolver(authentication, sesion).orElse(null);
    }

    // Para abrir: ademas, un visitante que aun no escribio al asistente
    // registra la sesion que declara su navegador (nunca se emite una).
    private IdentidadDelChat paraAbrir(Authentication authentication, String sesion, HttpServletRequest peticion) {
        return resolutor.resolverODeclarar(authentication, sesion, ChatController.origenDe(peticion)).orElse(null);
    }
}
