package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.chat.canal.AutenticacionStomp;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * El unico canal STOMP sobre WebSocket del servicio — HU-SAL-002 y HU-JUE-015.
 *
 * <p>Un solo endpoint, un solo broker y una sola cadena de interceptores para
 * la sala de batalla y para el chat, segun
 * {@code contracts/websocket/salas-partidas.yaml}. Antes habia dos
 * configuraciones que registraban el mismo {@code /ws}; Spring no admite dos
 * manejadores en la misma ruta, asi que el servicio no arrancaba con ambas.
 * La del chat ({@code ConfiguracionDelChat}) conserva solo sus beans de
 * dominio.
 *
 * <p><b>Como se autentica el canal.</b> El navegador no puede poner cabeceras
 * en el handshake HTTP del WebSocket, y un token en la URL quedaria en
 * bitacoras y en el historial. Por eso el handshake {@code /ws} esta abierto
 * en {@code SecurityConfig} y el JWT —el mismo de la API— viaja en la cabecera
 * {@code Authorization} del frame {@code CONNECT}, donde lo valida
 * {@link AutenticacionStomp}. Sin token valido no hay sesion STOMP; la
 * conexion se cierra antes de poder suscribirse a nada.
 *
 * <p><b>Como se autoriza.</b> Autenticado no significa dentro:
 * {@link AutorizacionDeDestinos} deja suscribirse al canal de una sala solo a
 * sus participantes. Va despues de la autenticacion porque necesita la
 * identidad que aquella deja en la sesion.
 *
 * <p>Broker simple en memoria: el docker-compose no levanta ninguno todavia y
 * elegirlo es decision de equipo. Cuando exista, se cambia aqui y el contrato
 * no se mueve.
 */
@Configuration
@EnableWebSocketMessageBroker
class ConfiguracionWebSocket implements WebSocketMessageBrokerConfigurer {

    private final String endpoint;
    private final Set<String> origenesPermitidos = new LinkedHashSet<>();
    private final JwtDecoder decodificador;
    private final RepositorioDeSalas salas;

    ConfiguracionWebSocket(
            @Value("${salas.websocket.endpoint}") String endpoint,
            @Value("${salas.websocket.origenes-permitidos}") String[] origenesDeSalas,
            // El chat (HU-JUE-015) declaro su propia variable de origenes antes
            // de unificar el canal; se sigue honrando para no romper entornos
            // ya configurados.
            @Value("${chat.ws.origenes:}") String[] origenesDelChat,
            JwtDecoder decodificador,
            RepositorioDeSalas salas) {
        this.endpoint = endpoint;
        for (String origen : origenesDeSalas) {
            if (!origen.isBlank()) {
                origenesPermitidos.add(origen.strip());
            }
        }
        for (String origen : origenesDelChat) {
            if (!origen.isBlank()) {
                origenesPermitidos.add(origen.strip());
            }
        }
        this.decodificador = decodificador;
        this.salas = salas;
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
                .setAllowedOriginPatterns(origenesPermitidos.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        // El orden importa: primero quien eres, despues que puedes seguir.
        registro.interceptors(
                new AutenticacionStomp(decodificador),
                new AutorizacionDeDestinos(salas));
    }
}
