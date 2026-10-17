package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 7.4.9 — lo que se sabe de una conexion antes del primer frame STOMP, en los
 * atributos de su sesion WebSocket:
 *
 * <ul>
 *   <li>{@link #ORIGEN}: la direccion del cliente, con la misma regla que
 *       {@code ChatController.origenDe}: {@code X-Real-IP}, que fija el borde
 *       (borde-dev.conf la pone tambien en la location del canal), o la del
 *       socket sin borde. Con ella se aplica el limite de mensajes por
 *       direccion (7.4.8) a cada mensaje del canal, como en la API;</li>
 *   <li>{@link #EN_VUELO}: cuantas preguntas de esta conexion esperan
 *       respuesta ({@link ConversacionEnVivo}).</li>
 * </ul>
 *
 * <p>No decide nada: el origen del navegador lo comprueba Spring con los
 * origenes de CORS ({@link CanalDelChatbotConfig}) y la identidad,
 * {@link PoliticaDelCanal} en el CONNECT.
 */
public class OrigenDelCanal implements HandshakeInterceptor {

    static final String ORIGEN = "chatbot.origen";
    static final String EN_VUELO = "chatbot.enVuelo";

    private static final String CABECERA_IP_REAL = "X-Real-IP";

    @Override
    public boolean beforeHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                   WebSocketHandler manejador, Map<String, Object> atributos) {
        String origen = origenDe(peticion);
        if (origen != null) {
            atributos.put(ORIGEN, origen);
        }
        atributos.put(EN_VUELO, new AtomicInteger());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                               WebSocketHandler manejador, Exception error) {
        // Nada que hacer despues: los atributos ya quedaron en la sesion.
    }

    static String origenDe(ServerHttpRequest peticion) {
        String real = peticion.getHeaders().getFirst(CABECERA_IP_REAL);
        if (real != null && !real.isBlank()) {
            return real.strip();
        }
        InetSocketAddress socket = peticion.getRemoteAddress();
        if (socket == null) {
            return null;
        }
        return socket.getAddress() == null ? socket.getHostString() : socket.getAddress().getHostAddress();
    }
}
