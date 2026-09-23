package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.PublicadorDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.SancionesDelJugador;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Beans del chat de HU-JUE-015.
 *
 * <p>El canal STOMP (endpoint {@code /ws}, broker, prefijos {@code /tema},
 * {@code /app} y {@code /usuario/cola}, y la autenticacion por JWT en el
 * {@code CONNECT} con {@code AutenticacionStomp} de plataforma-seguridad) es uno solo para todo el
 * servicio y vive en {@code tiemporeal.ConfiguracionWebSocket}: la sala de
 * batalla (HU-SAL-002) y el chat comparten conexion, tal como fija
 * {@code contracts/websocket/salas-partidas.yaml}. Esta clase registro ese
 * mismo endpoint por su cuenta mientras el chat vivio en una rama aparte; al
 * integrarse ambas historias, dos configuraciones sobre {@code /ws} impedian
 * arrancar el servicio, y el canal quedo en un solo sitio. Los origenes que
 * el chat declaraba en {@code chat.ws.origenes} se siguen honrando alli.
 */
@Configuration
public class ConfiguracionDelChat {

    @Bean
    public EnviarMensaje enviarMensaje(HistorialDeChat historial, FiltroDeContenido filtro,
            SancionesDelJugador sanciones, PublicadorDeChat publicador) {
        return new EnviarMensaje(historial, filtro, sanciones, publicador, Clock.systemUTC());
    }

    /**
     * Cliente del chat hacia lista negra y sanciones.
     *
     * <p>Lleva el interceptor de traza (regla 5, R11): sin el, la consulta de
     * lista negra y la de sancion salen sin `traceparent` y la traza del
     * mensaje se corta justo donde empieza a ser interesante —cuando el
     * mensaje NO se publica y hay que averiguar por que.
     */
    @Bean
    public RestClient restClientChat() {
        return RestClient.builder()
                .requestInterceptor(new com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza())
                .build();
    }
}
