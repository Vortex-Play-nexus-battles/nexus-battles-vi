package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.PublicadorDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.SancionesDelJugador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.time.Clock;

/**
 * Canal STOMP del chat, con los prefijos que fija el contrato AsyncAPI:
 * /tema para suscribirse, /app para enviar y /usuario/cola para lo privado.
 *
 * <p>El broker es el simple de Spring, en memoria: alcanza para un nodo y la
 * eleccion de uno externo es decision de equipo. La identidad de cada
 * conexion la pone AutenticacionStomp a partir del JWT, la misma que usa la
 * API HTTP.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ConfiguracionDelChat implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder decodificador;
    private final String[] origenesPermitidos;

    public ConfiguracionDelChat(JwtDecoder decodificador,
            @Value("${chat.ws.origenes}") String[] origenesPermitidos) {
        this.decodificador = decodificador;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws").setAllowedOriginPatterns(origenesPermitidos);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry broker) {
        broker.enableSimpleBroker("/tema", "/cola");
        broker.setApplicationDestinationPrefixes("/app");
        broker.setUserDestinationPrefix("/usuario");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(new AutenticacionStomp(decodificador));
    }

    @Bean
    public EnviarMensaje enviarMensaje(HistorialDeChat historial, FiltroDeContenido filtro,
            SancionesDelJugador sanciones, PublicadorDeChat publicador) {
        return new EnviarMensaje(historial, filtro, sanciones, publicador, Clock.systemUTC());
    }

    @Bean
    public RestClient restClientChat() {
        return RestClient.builder().build();
    }
}
