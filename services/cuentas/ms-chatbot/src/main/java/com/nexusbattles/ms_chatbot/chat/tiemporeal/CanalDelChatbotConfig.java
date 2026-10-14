package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * 7.4.9 «WebSocket para comunicacion en tiempo real»
 * ({@code contracts/websocket/chatbot.yaml} 1.0.0). STOMP sobre WebSocket
 * nativo, como ms-subastas, salas-partidas y notificaciones (backend-spring.md:
 * «Tiempo real: Spring WebSocket + STOMP»; el cliente es el
 * {@code comun/transporte-stomp.js} del frontend, sin SockJS).
 *
 * <ul>
 *   <li><b>Endpoint</b> {@code /ws-chatbot}; con el context-path, la ruta
 *       publica es {@code /api/v1/ws-chatbot}, la del borde.</li>
 *   <li><b>Origenes</b>: los mismos de la API ({@code app.cors.origenes-permitidos},
 *       {@code CORS_ORIGENES}; desplegar.sh le suma {@code https://<dominio>}).
 *       Un {@code Origin} ajeno recibe 403 en el handshake. La cadena HTTP ya
 *       deja pasar el handshake sin token (SecurityConfig): la identidad va en
 *       el CONNECT ({@link PoliticaDelCanal}).</li>
 *   <li><b>Broker simple en memoria</b>, solo {@code /queue} (colas de
 *       usuario): no hay temas que se difundan a todos. Es por instancia; con
 *       varias habria que poner un broker externo (relay), que no esta
 *       desplegado. En DEV hay una sola.</li>
 *   <li><b>Latidos</b> de {@code chatbot.ws.latido-ms} (10 s) en cada
 *       sentido, con el planificador del propio broker (provisional,
 *       D-CHA-09).</li>
 *   <li><b>Interceptor de entrada</b>: {@link PoliticaDelCanal}, con el
 *       decodificador de JWT de la API y {@link ConversorRolesJwt}; lo que
 *       rechaza sale en un frame ERROR con su motivo ({@link ErroresDelProtocolo}).</li>
 * </ul>
 *
 * <p>Los mensajes se convierten con el mismo {@code JsonMapper} (Jackson 3) que
 * la API REST: Spring Boot lo registra en el canal, asi que un {@code Mensaje}
 * viaja con la misma forma que en {@code POST /chat/mensajes}.
 *
 * <p>Efecto colateral sabido: el broker trae su planificador
 * ({@code messageBrokerTaskScheduler}) y, al ser el unico {@code TaskScheduler}
 * del contexto, las tareas {@code @Scheduled} del servicio (vigilancia de la
 * base, publicacion programada, barrido del limitador) corren en el, como en
 * ms-subastas. Tiene un hilo por procesador (2 en el host de DEV): mientras
 * una evaluacion de la base ocupa uno, los latidos siguen en el otro.
 */
@Configuration
@EnableWebSocketMessageBroker
public class CanalDelChatbotConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] origenesPermitidos;
    private final long latidoMs;
    private final JwtDecoder decodificador;
    private final ConversorRolesJwt conversor;
    private final ResolutorDeIdentidad resolutor;
    private final TaskScheduler planificador;

    public CanalDelChatbotConfig(@Value("${app.cors.origenes-permitidos}") List<String> origenesPermitidos,
                                 @Value("${chatbot.ws.latido-ms:10000}") long latidoMs,
                                 JwtDecoder decodificador, ConversorRolesJwt conversor,
                                 ResolutorDeIdentidad resolutor,
                                 // @Lazy: el planificador lo define la misma configuracion
                                 // del broker que usa esta clase (receta de la documentacion
                                 // de Spring para los latidos del broker simple).
                                 @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler planificador) {
        this.origenesPermitidos = origenesPermitidos.stream()
            .filter(origen -> origen != null && !origen.isBlank())
            .map(String::strip)
            .toArray(String[]::new);
        this.latidoMs = Math.max(0, latidoMs);
        this.decodificador = decodificador;
        this.conversor = conversor;
        this.resolutor = resolutor;
        this.planificador = planificador;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint(DestinosDelCanal.ENDPOINT)
            .setAllowedOrigins(origenesPermitidos)
            .addInterceptors(new OrigenDelCanal());
        registro.setErrorHandler(new ErroresDelProtocolo());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/queue")
            .setHeartbeatValue(new long[] {latidoMs, latidoMs})
            .setTaskScheduler(planificador);
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix(DestinosDelCanal.PREFIJO_DE_USUARIO);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(new PoliticaDelCanal(new TokenDelCanal(decodificador, conversor), resolutor));
    }
}
