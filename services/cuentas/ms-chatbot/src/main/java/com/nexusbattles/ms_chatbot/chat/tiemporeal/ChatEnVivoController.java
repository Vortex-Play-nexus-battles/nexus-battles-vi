package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 7.4.9 — {@code /app/chat/mensajes} del canal en tiempo real
 * ({@code contracts/websocket/chatbot.yaml} 1.0.0). Sin logica: entrega el
 * frame, la conexion y sus atributos a {@link ConversacionEnVivo}, que
 * contesta por las colas de usuario. Quien llega hasta aqui ya paso por
 * {@link PoliticaDelCanal} (identidad en el CONNECT, solo este destino).
 *
 * <p>El cuerpo llega en bytes, sin convertir: si la conversion de Spring
 * fallara, la excepcion se quedaria en la bitacora y la ventana esperaria una
 * respuesta que no llega. {@link PreguntaEnVivo} lo lee y, si no tiene la
 * forma del contrato, se contesta un 400.
 */
@Controller
public class ChatEnVivoController {

    private final ConversacionEnVivo conversacion;

    public ChatEnVivoController(ConversacionEnVivo conversacion) {
        this.conversacion = conversacion;
    }

    @MessageMapping("/chat/mensajes")
    public void enviarMensaje(@Payload byte[] cuerpo, SimpMessageHeaderAccessor cabeceras) {
        Principal usuario = cabeceras.getUser();
        if (usuario == null || cabeceras.getSessionId() == null) {
            // PoliticaDelCanal ya lo impide; sin conexion identificada no hay a quien contestar.
            return;
        }
        conversacion.recibir(cuerpo,
            new ConversacionEnVivo.Destinatario(usuario.getName(), cabeceras.getSessionId()),
            cabeceras.getSessionAttributes());
    }
}
