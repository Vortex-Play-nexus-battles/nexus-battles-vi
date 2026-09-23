package com.nexusbattles.ms_subastas.subastas.realtime;

import java.util.Arrays;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * HU-SUB-011. Contador y datos en vivo del listado, via STOMP sobre
 * WebSocket (backend-spring.md: "Tiempo real: Spring WebSocket + STOMP").
 *
 * <p>Broker simple en memoria (enableSimpleBroker), no un broker externo
 * (RabbitMQ/ActiveMQ) -- confirmado que RabbitMQ no existe todavia en el
 * proyecto (verificado con Andres, contracts/eventos/ vacio), y un broker
 * en memoria es suficiente para un solo canal de difusion de baja
 * frecuencia como este.
 *
 * <h2>R9.6 — el PENDIENTE que se desplego igual</h2>
 *
 * Aqui habia escrito, en letra del propio autor: <i>"PENDIENTE:
 * setAllowedOriginPatterns("*") es temporal. Restringir a los origenes reales
 * del frontend antes de cualquier despliegue"</i>. Se desplego sin
 * restringirlo. No es un reproche a quien lo escribio —dejo la nota y era
 * correcta—: es que un comentario no bloquea nada, y por eso ahora es
 * configuracion con un valor por omision cerrado, que si lo hace.
 *
 * <p>Los origenes salen de {@code SUBASTAS_WS_ORIGENES}, igual que
 * {@code SALAS_WS_ORIGENES} y {@code CHAT_WS_ORIGENES} en salas-partidas y
 * {@code NOTIFICACIONES_WS_ORIGENES} en notificaciones: los tres canales de la
 * casa se configuran ya del mismo modo (regla 10). Vacio = la lista de
 * desarrollo de abajo, que cubre el borde y el propio servicio en local.
 *
 * <h2>R9.6b — la politica del canal</h2>
 *
 * R9.6a dejo dicho que la autenticacion del CONNECT iba en el PR siguiente, y
 * este es. El orden se respeto: primero el navegador aprendio a mandar el
 * Bearer (#631, ya desplegado), y solo ahora el servidor lo mira. Al reves, el
 * listado en vivo se habria quedado sin canal entre un despliegue y el
 * siguiente.
 *
 * <p>Quien decide que se admite es {@link PoliticaDelCanalDeSubastas}, y su
 * javadoc explica por que aqui NO vale el {@code AutenticacionStomp}
 * compartido tal cual: este canal sirve una vista publica por decision de
 * producto y no acepta ni un mensaje del cliente.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Desarrollo local: el borde y el propio servicio. Nada mas. */
    static final String[] ORIGENES_POR_OMISION = {
        "http://localhost:8099", "http://127.0.0.1:8099",
        "http://localhost:8092", "http://127.0.0.1:8092"
    };

    private final String[] origenesPermitidos;
    private final JwtDecoder decodificador;

    public WebSocketConfig(
            @Value("${subastas.ws.origenes:}") String[] origenesConfigurados,
            JwtDecoder decodificador) {
        this.origenesPermitidos = normalizar(origenesConfigurados);
        this.decodificador = decodificador;
    }

    /**
     * R9.6b — el interceptor que decide quien entra y que puede hacer.
     *
     * <p>Es el MISMO decodificador que valida los Bearer de la API HTTP (el
     * que Boot construye a partir de {@code jwk-set-uri}, apuntando al JWKS de
     * ms-identidad desde R4.2) y el MISMO conversor de roles: un token que
     * sirve para la API sirve para el canal, y uno que no, tampoco. Dos
     * cadenas de validacion distintas sobre el mismo token es como se acaba
     * teniendo una puerta abierta sin darse cuenta.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.interceptors(new PoliticaDelCanalDeSubastas(
                decodificador,
                new ConversorRolesJwt(),
                SubastaRealtimePublisher.CANAL_LISTADO));
    }

    /**
     * Una lista vacia no puede significar "ninguno": significaria que el
     * despliegue paso la variable sin valor y el canal dejaria de abrirse para
     * todo el mundo, sin que nadie entendiera por que. Ante eso se cae a la
     * lista de desarrollo, que es restrictiva pero utilizable.
     */
    private static String[] normalizar(String[] configurados) {
        if (configurados == null) {
            return ORIGENES_POR_OMISION.clone();
        }
        String[] limpios = Arrays.stream(configurados)
                .filter(o -> o != null && !o.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
        return limpios.length == 0 ? ORIGENES_POR_OMISION.clone() : limpios;
    }

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
            .setAllowedOriginPatterns(origenesPermitidos);
    }
}
