package com.nexusbattles.plataforma.notificaciones.bandeja;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.tiemporeal.AutenticacionStomp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal STOMP sobre WebSocket, con los prefijos que fija
 * contracts/websocket/notificaciones.yaml y que ya usa salas-partidas.
 *
 * <p>Se usa el broker simple que trae Spring y no uno externo. El
 * docker-compose.yml del proyecto no levanta ningun broker de mensajes todavia,
 * y elegir uno es una decision de equipo, no de este modulo. El broker simple
 * cumple los tres escenarios de la historia en un solo nodo. Cuando el equipo
 * acuerde el broker, se cambia aqui y el contrato no se mueve.
 *
 * <p><b>Como se autentica el canal.</b> Hasta la 1.0.0 del contrato la
 * identidad la ponia el handshake leyendo {@code ?usuario=} de la URL: quien
 * escribiera el id de otro recibia sus avisos. Desde la 1.1.0 el handshake
 * esta abierto y el JWT —el mismo de la API HTTP— viaja en la cabecera
 * {@code Authorization} del frame {@code CONNECT}, donde lo valida
 * {@link AutenticacionStomp} (plataforma-seguridad). El usuario de la sesion
 * se llama por su {@code uid}, asi que {@code convertAndSendToUser(uid, ...)}
 * llega a sus conexiones y solo a ellas.
 *
 * <p>El endpoint por defecto es {@code /ws/notificaciones} y no {@code /ws}:
 * el borde ya no puede decidir por el parametro {@code usuario} si un
 * WebSocket es de salas o de notificaciones, asi que decide por la ruta.
 */
@Configuration
@EnableWebSocketMessageBroker
class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    private final String endpoint;
    private final String[] origenesPermitidos;
    private final JwtDecoder decodificador;
    private final ConversorRolesJwt conversor;

    ConfiguracionWebSocket(
            @Value("${notificaciones.websocket.endpoint}") String endpoint,
            @Value("${notificaciones.websocket.origenes-permitidos}") String[] origenesPermitidos,
            JwtDecoder decodificador,
            ConversorRolesJwt conversor) {
        this.endpoint = endpoint;
        this.origenesPermitidos = origenesPermitidos.clone();
        this.decodificador = decodificador;
        this.conversor = conversor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/tema", "/cola");
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/usuario");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint(endpoint)
                .setAllowedOriginPatterns(origenesPermitidos);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(new AutenticacionStomp(decodificador, conversor));
    }
}
