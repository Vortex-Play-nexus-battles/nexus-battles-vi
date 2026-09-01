package com.nexusbattles.plataforma.notificaciones.bandeja;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
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
 */
@Configuration
@EnableWebSocketMessageBroker
class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    private final String endpoint;
    private final String[] origenesPermitidos;

    ConfiguracionWebSocket(
            @Value("${notificaciones.websocket.endpoint}") String endpoint,
            @Value("${notificaciones.websocket.origenes-permitidos}") String[] origenesPermitidos) {
        this.endpoint = endpoint;
        this.origenesPermitidos = origenesPermitidos.clone();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/tema", "/cola");
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/usuario");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint(endpoint).setAllowedOriginPatterns(origenesPermitidos);
    }
}
