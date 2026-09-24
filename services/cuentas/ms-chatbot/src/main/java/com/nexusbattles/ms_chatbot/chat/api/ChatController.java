package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.service.CalificacionService;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String CABECERA_SESION_ANONIMA = "X-Id-Sesion-Anonima";

    private final ChatService chatService;
    private final CalificacionService calificacionService;

    public ChatController(ChatService chatService, CalificacionService calificacionService) {
        this.chatService = chatService;
        this.calificacionService = calificacionService;
    }

    @PostMapping("/mensajes")
    public ResponseEntity<MensajeResponse> enviarMensaje(
        @RequestBody @Valid EnviarMensajeRequest request,
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        Identidad identidad = resolverIdentidad(authentication, idSesionAnonima);
        Mensaje respuesta = chatService.enviarMensaje(
            identidad.identificadorSesion(), identidad.autenticado(),
            request.contenido(), request.adjuntoUrl(), identidad.tokenCrudo());

        return ResponseEntity.ok(MensajeResponse.desde(respuesta));
    }

    // HU-CHA-011: calificar una respuesta del bot como util / no util, con
    // comentario opcional. Sirve igual para visitantes y usuarios
    // autenticados; la identidad se resuelve exactamente como en los demas
    // endpoints, y CalificacionService verifica que el mensaje pertenezca a
    // esa conversacion.
    // Respuestas: 201 creada, 400 cuerpo invalido o mensaje que no es del
    // bot, 404 mensaje inexistente o ajeno, 409 ya calificado.
    @PostMapping("/mensajes/{mensajeId}/calificacion")
    public ResponseEntity<CalificacionResponse> calificarMensaje(
        @PathVariable UUID mensajeId,
        @RequestBody @Valid CalificarMensajeRequest request,
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        Identidad identidad = resolverIdentidad(authentication, idSesionAnonima);
        Calificacion calificacion = calificacionService.calificar(
            mensajeId, identidad.identificadorSesion(), request.util(), request.comentario());

        return ResponseEntity.status(HttpStatus.CREATED).body(CalificacionResponse.desde(calificacion));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<MensajeResponse>> obtenerHistorial(
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        Identidad identidad = resolverIdentidad(authentication, idSesionAnonima);
        List<MensajeResponse> historial = chatService.obtenerHistorial(identidad.identificadorSesion())
            .stream()
            .map(MensajeResponse::desde)
            .toList();

        return ResponseEntity.ok(historial);
    }

    @DeleteMapping("/historial")
    public ResponseEntity<Void> limpiarHistorial(
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        Identidad identidad = resolverIdentidad(authentication, idSesionAnonima);
        chatService.limpiarHistorial(identidad.identificadorSesion());

        return ResponseEntity.noContent().build();
    }

    // Un usuario autenticado se identifica por el claim 'uid' de su JWT
    // (emitido por ms-identidad). Un visitante no trae JWT, asi que debe
    // mandar su propio identificador anonimo en la cabecera X-Id-Sesion-Anonima.
    //
    // HU-CHA-008: ademas del uid, se guarda el token crudo (compact JWT)
    // cuando esta autenticado, para que ChatService pueda reenviarlo tal
    // cual a inventario/subastas/notificaciones (ADR-001/ADR-005: la
    // identidad del jugador siempre viaja en su propio token, nunca se
    // fabrica una credencial nueva para esto).
    private Identidad resolverIdentidad(Authentication authentication, String idSesionAnonima) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String uid = jwtAuth.getToken().getClaimAsString("uid");
            String tokenCrudo = jwtAuth.getToken().getTokenValue();
            return new Identidad(uid, true, tokenCrudo);
        }
        if (idSesionAnonima == null || idSesionAnonima.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Falta la cabecera " + CABECERA_SESION_ANONIMA + " para un visitante no autenticado.");
        }
        return new Identidad(idSesionAnonima, false, null);
    }

    private record Identidad(String identificadorSesion, boolean autenticado, String tokenCrudo) {
    }
}
