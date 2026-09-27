package com.nexusbattles.ms_chatbot.chat.api;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import com.nexusbattles.ms_chatbot.chat.model.Calificacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.service.CalificacionService;
import com.nexusbattles.ms_chatbot.chat.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.util.Optional;
import java.util.UUID;

// B11 (ms-chatbot.yaml 1.2.0): la identidad la resuelve ResolutorDeIdentidad.
// Un usuario es el uid de su token y nada mas; un visitante es una sesion
// registrada, emitida por este servidor o declarada por el navegador.
// X-Id-Sesion-Anonima nunca abre la conversacion de un usuario registrado (el
// ataque de la auditoria: mandar el uid de otro).
@RestController
@RequestMapping("/chat")
public class ChatController {

    static final String CABECERA_SESION_ANONIMA = "X-Id-Sesion-Anonima";

    // Detras del borde, nginx fija X-Real-IP con la direccion real del cliente
    // (borde-dev.conf); sin borde, la del socket.
    private static final String CABECERA_IP_REAL = "X-Real-IP";

    private static final String MENSAJE_NO_ENCONTRADO =
        "No existe una respuesta con ese identificador en tu conversacion.";

    private final ChatService chatService;
    private final CalificacionService calificacionService;
    private final ResolutorDeIdentidad resolutor;
    private final SesionesAnonimas sesiones;

    public ChatController(ChatService chatService, CalificacionService calificacionService,
                          ResolutorDeIdentidad resolutor, SesionesAnonimas sesiones) {
        this.chatService = chatService;
        this.calificacionService = calificacionService;
        this.resolutor = resolutor;
        this.sesiones = sesiones;
    }

    // 1.2.0: el visitante puede pedir su sesion al servidor (256 bits) en vez
    // de declarar la suya.
    @PostMapping("/sesiones")
    public ResponseEntity<SesionAnonimaResponse> crearSesion(HttpServletRequest peticion) {
        SesionesAnonimas.Emitida emitida = sesiones.emitir(origenDe(peticion));
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(CABECERA_SESION_ANONIMA, emitida.identificador())
            .body(new SesionAnonimaResponse(emitida.identificador(), emitida.sesion().getExpiraEn()));
    }

    @PostMapping("/mensajes")
    public ResponseEntity<MensajeResponse> enviarMensaje(
        @RequestBody @Valid EnviarMensajeRequest request,
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication,
        HttpServletRequest peticion) {

        ResolutorDeIdentidad.Resultado quien = resolutor.resolverOEmitir(authentication, idSesionAnonima,
            origenDe(peticion));
        Mensaje respuesta = chatService.enviarMensaje(quien.identidad(), request.contenido(), request.adjuntoUrl());

        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (quien.sesionEmitida() != null) {
            ok.header(CABECERA_SESION_ANONIMA, quien.sesionEmitida());
        }
        return ok.body(MensajeResponse.desde(respuesta));
    }

    // HU-CHA-011: calificar una respuesta del bot como util / no util, con
    // comentario opcional. CalificacionService verifica que el mensaje
    // pertenezca a la conversacion de quien llama; sin identidad valida no hay
    // conversacion propia, asi que 404 (sin revelar si el mensaje existe).
    // Respuestas: 201 creada, 400 cuerpo invalido o mensaje que no es del
    // bot, 404 mensaje inexistente o ajeno, 409 ya calificado.
    @PostMapping("/mensajes/{mensajeId}/calificacion")
    public ResponseEntity<CalificacionResponse> calificarMensaje(
        @PathVariable UUID mensajeId,
        @RequestBody @Valid CalificarMensajeRequest request,
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        IdentidadDelChat identidad = resolutor.resolver(authentication, idSesionAnonima)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MENSAJE_NO_ENCONTRADO));
        Calificacion calificacion = calificacionService.calificar(
            mensajeId, identidad.claveDeConversacion(), request.util(), request.comentario());

        return ResponseEntity.status(HttpStatus.CREATED).body(CalificacionResponse.desde(calificacion));
    }

    // Sin identidad valida no hay conversacion propia: lista vacia.
    @GetMapping("/historial")
    public ResponseEntity<List<MensajeResponse>> obtenerHistorial(
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        Optional<IdentidadDelChat> identidad = resolutor.resolver(authentication, idSesionAnonima);
        List<MensajeResponse> historial = identidad
            .map(chatService::obtenerHistorial)
            .orElseGet(List::of)
            .stream()
            .map(MensajeResponse::desde)
            .toList();

        return ResponseEntity.ok(historial);
    }

    // Sin identidad valida no hay nada propio que borrar: 204 igual.
    @DeleteMapping("/historial")
    public ResponseEntity<Void> limpiarHistorial(
        @RequestHeader(value = CABECERA_SESION_ANONIMA, required = false) String idSesionAnonima,
        Authentication authentication) {

        resolutor.resolver(authentication, idSesionAnonima).ifPresent(chatService::limpiarHistorial);
        return ResponseEntity.noContent().build();
    }

    static String origenDe(HttpServletRequest peticion) {
        String real = peticion.getHeader(CABECERA_IP_REAL);
        return real == null || real.isBlank() ? peticion.getRemoteAddr() : real.strip();
    }
}
