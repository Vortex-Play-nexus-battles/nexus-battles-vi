package com.nexusbattles.ms_subastas.subastas.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * HU-SUB-011. Contador y datos en vivo del listado, via STOMP sobre
 * WebSocket (backend-spring.md: "Tiempo real: Spring WebSocket + STOMP").
 *
 * Broker simple en memoria (enableSimpleBroker), no un broker externo
 * (RabbitMQ/ActiveMQ) -- confirmado que RabbitMQ no existe todavia en el
 * proyecto (verificado con Andres, contracts/eventos/ vacio), y un broker
 * en memoria es suficiente para un solo canal de difusion de baja
 * frecuencia como este.
 *
 * PENDIENTE: setAllowedOriginPatterns("*") es temporal. Restringir a los
 * origenes reales del frontend antes de cualquier despliegue -- mismo tipo
 * de descuido que se senalo en SecurityInterceptor de ms-identidad
 * (Access-Control-Allow-Origin: *).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Un solo canal de difusion para el listado (no uno por subasta):
        // ver justificacion en el diseno de esta HU.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Con server.servlet.context-path=/api/v1 ya confirmado en este
        // servicio, la URL real de conexion queda en /api/v1/ws-subastas,
        // no en /ws-subastas -- mismo comportamiento que ya vimos con el
        // controlador REST.
        registry.addEndpoint("/ws-subastas")
            .setAllowedOriginPatterns("*"); // PENDIENTE: restringir antes de desplegar
    }
}
