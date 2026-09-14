package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.comun.error.ErrorDeNegocio;
import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Entradas STOMP del chat, con las direcciones del contrato AsyncAPI.
 *
 * <p>El historial se entrega al suscribirse a /app/.../historial, en una sola
 * respuesta a esa conexion, y no por HTTP: asi quien entra despues se pone al
 * dia en el mismo canal en el que va a conversar. Los errores vuelven por la
 * cola privada /usuario/cola/salas que ya declara el contrato, en el mismo
 * formato problem details que ManejadorDeErrores.
 */
@Controller
public class ChatController {

    private final EnviarMensaje enviarMensaje;
    private final HistorialDeChat historial;
    private final int tamanoHistorial;

    public ChatController(EnviarMensaje enviarMensaje, HistorialDeChat historial,
            @Value("${chat.historial.tamano:50}") int tamanoHistorial) {
        this.enviarMensaje = enviarMensaje;
        this.historial = historial;
        this.tamanoHistorial = tamanoHistorial;
    }

    @MessageMapping("/salas/{idSala}/chat")
    public void enviarASala(@DestinationVariable UUID idSala, @Payload EnviarMensajeRequest cuerpo,
            Principal principal) {
        enviarMensaje.enviar(Canal.deSala(idSala), autorDe(principal), cuerpo.texto(), cuerpo.logro());
    }

    @MessageMapping("/chat/general")
    public void enviarAlGeneral(@Payload EnviarMensajeRequest cuerpo, Principal principal) {
        enviarMensaje.enviar(Canal.general(), autorDe(principal), cuerpo.texto(), cuerpo.logro());
    }

    @SubscribeMapping("/salas/{idSala}/chat/historial")
    public List<MensajeDeChatResponse> historialDeSala(@DestinationVariable UUID idSala) {
        return historial.ultimos(Canal.deSala(idSala), tamanoHistorial).stream()
                .map(MensajeDeChatResponse::de).toList();
    }

    @SubscribeMapping("/chat/general/historial")
    public List<MensajeDeChatResponse> historialGeneral() {
        return historial.ultimos(Canal.general(), tamanoHistorial).stream()
                .map(MensajeDeChatResponse::de).toList();
    }

    /** Mismo formato que ManejadorDeErrores, pero por la cola privada del jugador (errorDeCanal). */
    @MessageExceptionHandler(ErrorDeNegocio.class)
    @SendToUser(destinations = "/cola/salas", broadcast = false)
    public ProblemDetail errorDeNegocio(ErrorDeNegocio error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(error.estado()), error.detalle());
        problema.setType(error.tipo());
        problema.setTitle(error.titulo());
        return problema;
    }

    /** El jugador sale del mismo JWT que en SalasController: sujeto y claim del apodo. */
    static Autor autorDe(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("El chat necesita un jugador autenticado.");
        }
        Jwt jwt = token.getToken();
        String apodo = jwt.getClaimAsString("preferred_username");
        return new Autor(UUID.fromString(jwt.getSubject()), apodo == null ? jwt.getSubject() : apodo);
    }
}
