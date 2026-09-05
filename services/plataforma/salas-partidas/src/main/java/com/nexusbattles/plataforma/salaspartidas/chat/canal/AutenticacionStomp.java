package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Identidad de la conexion STOMP a partir del mismo JWT de la API HTTP.
 *
 * <p>El navegador no puede mandar cabeceras en el handshake WebSocket, asi
 * que el token viaja en la cabecera Authorization del frame CONNECT. Sin
 * token valido no hay conexion: el chat no admite anonimos porque cada
 * mensaje se atribuye a un jugador y las sanciones son por persona.
 */
public class AutenticacionStomp implements ChannelInterceptor {

    static final String CABECERA = "Authorization";
    static final String PREFIJO = "Bearer ";

    private final JwtDecoder decodificador;

    public AutenticacionStomp(JwtDecoder decodificador) {
        this.decodificador = decodificador;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor cabeceras = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (cabeceras == null || !StompCommand.CONNECT.equals(cabeceras.getCommand())) {
            return mensaje;
        }
        String valor = cabeceras.getFirstNativeHeader(CABECERA);
        if (valor == null || !valor.startsWith(PREFIJO)) {
            throw new AccessDeniedException("El chat necesita un token de acceso en la conexion.");
        }
        try {
            Jwt jwt = decodificador.decode(valor.substring(PREFIJO.length()).strip());
            cabeceras.setUser(new JwtAuthenticationToken(jwt));
        } catch (JwtException ex) {
            throw new AccessDeniedException("El token de acceso no es valido.");
        }
        return mensaje;
    }
}
