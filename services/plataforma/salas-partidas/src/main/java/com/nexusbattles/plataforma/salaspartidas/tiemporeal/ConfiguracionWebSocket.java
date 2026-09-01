package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal STOMP sobre WebSocket de salas y partidas — HU-SAL-002.
 *
 * <p>Los cuatro prefijos son <b>los mismos</b> que ya usa {@code notificaciones}
 * en HU-NOT-006: broker en {@code /tema} y {@code /cola}, destinos de
 * aplicacion en {@code /app}, destinos por usuario en {@code /usuario}. No es
 * casualidad ni copia: es la convencion con la que estan escritos los dos
 * AsyncAPI de la plataforma, y mantenerla es lo que permite que un mismo
 * cliente hable con los dos servicios sin dos configuraciones distintas.
 *
 * <p>La clase se queda <b>dentro de este servicio</b> y no en {@code shared/}.
 * Cada microservicio configura su propio broker: es su proceso y su despliegue.
 * Con dos casos no hay abstraccion que extraer todavia, y sacarla ahora seria
 * inventar una dependencia comun que nadie ha pedido.
 *
 * <p>Broker simple en memoria, a proposito: para el estado de una sala mientras
 * se llena no hace falta un relay externo. Si el canal de combate necesita
 * varias instancias, eso se decide con su carga medida, no ahora.
 */
@Configuration
@EnableWebSocketMessageBroker
class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    private final String endpoint;
    private final String[] origenesPermitidos;

    ConfiguracionWebSocket(
            @Value("${salas.websocket.endpoint}") String endpoint,
            @Value("${salas.websocket.origenes-permitidos}") String[] origenesPermitidos) {
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
