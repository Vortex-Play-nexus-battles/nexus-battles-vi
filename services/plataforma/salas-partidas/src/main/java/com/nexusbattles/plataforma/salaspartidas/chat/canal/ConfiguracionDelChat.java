package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.LimiteDeEnvios;
import com.nexusbattles.plataforma.salaspartidas.chat.PoliticaDeTexto;
import com.nexusbattles.plataforma.salaspartidas.chat.PublicadorDeChat;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.LimiteDeFrecuenciaEnMemoria;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesDelJugador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

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
            SancionesDelJugador sanciones, PublicadorDeChat publicador,
            PoliticaDeTexto.Limites limitesDeTexto, LimiteDeEnvios limiteDelChat) {
        return new EnviarMensaje(historial, filtro, sanciones, publicador, Clock.systemUTC(),
                limitesDeTexto, limiteDelChat);
    }

    /**
     * Lo que un mensaje tiene que ser para publicarse, ademas de pasar la lista
     * negra (auditoria de DEV del 30-sep). Lo usan el chat y los mensajes
     * privados. PROVISIONAL, D-37: ningun requisito fija estos numeros.
     */
    @Bean
    public PoliticaDeTexto.Limites limitesDeTextoDelChat(
            @Value("${chat.texto.maximo-de-lineas:6}") int maximoDeLineas,
            @Value("${chat.texto.maxima-repeticion:15}") int maximaRepeticion,
            @Value("${chat.texto.largo-para-mirar-simbolos:40}") int largoParaMirarSimbolos,
            @Value("${chat.texto.minimo-de-letras-por-ciento:50}") int minimoDeLetrasPorCiento) {
        return new PoliticaDeTexto.Limites(maximoDeLineas, maximaRepeticion, largoParaMirarSimbolos,
                minimoDeLetrasPorCiento);
    }

    /**
     * Cuantos mensajes seguidos admite el chat general y el de sala por autor.
     * El chat no tenia ninguno (auditoria de DEV del 30-sep); el adaptador es
     * el de los mensajes privados, con sus propios valores. PROVISIONAL, D-37.
     */
    @Bean
    public LimiteDeEnvios limiteDelChat(
            @Value("${chat.limite.mensajes:5}") int mensajes,
            @Value("${chat.limite.ventana-segundos:10}") long ventanaSegundos) {
        LimiteDeFrecuenciaEnMemoria limite = new LimiteDeFrecuenciaEnMemoria(mensajes,
                Duration.ofSeconds(ventanaSegundos), Clock.systemUTC());
        return limite::registrar;
    }

    /**
     * Cliente del chat hacia lista negra y sanciones.
     *
     * <p>Lleva el interceptor de traza (regla 5, R11): sin el, la consulta de
     * lista negra y la de sancion salen sin `traceparent` y la traza del
     * mensaje se corta justo donde empieza a ser interesante —cuando el
     * mensaje NO se publica y hay que averiguar por que.
     *
     * <p>B12 — y la fabrica con tiempos de espera de
     * {@code ConfiguracionDeResiliencia}, la misma que el resto de clientes de
     * este servicio. Era el unico que salia sin ella, y es el que mas se usa:
     * la sancion activa se consulta en cada mensaje, en cada alta de sala y en
     * cada ingreso. Con moderacion-sanciones aceptando la conexion y sin
     * contestar, todo eso se quedaba colgado hasta que el sistema operativo se
     * rindiera.
     */
    @Bean
    public RestClient restClientChat(ClientHttpRequestFactory fabricaDePeticionesConTiempos) {
        return RestClient.builder()
                .requestFactory(fabricaDePeticionesConTiempos)
                .requestInterceptor(new com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza())
                .build();
    }
}
