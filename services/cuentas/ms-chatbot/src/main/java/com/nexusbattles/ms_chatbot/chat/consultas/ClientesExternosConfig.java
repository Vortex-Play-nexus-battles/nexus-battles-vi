package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Clientes HTTP hacia inventario, subastas, notificaciones, finanzas, torneos
// y la lista negra (HU-CHA-008, B11).
//
// Por que el RestClient.Builder se arma aqui a mano: en Spring Boot 4 el
// builder autoconfigurado vive en el modulo spring-boot-restclient, que este
// servicio no tiene en el classpath. Por eso Spring no lo ofrece como bean.
//
// Regla 5 de plataforma (propagar el trace id): TrazaAutoConfiguration, de
// plataforma-observabilidad, engancha InterceptorDeTraza solo a los builders
// que construye Spring Boot. Un builder armado a mano con RestClient.builder()
// debe agregarlo el propio servicio; sin esto, la traza de una consulta del
// chat moria al llamar a inventario, subastas o notificaciones.
//
// B11 — tiempos de espera: hasta aqui ningun cliente los tenia, y una consulta
// del chat a un servicio colgado retenia el hilo (y, antes de B11, una
// conexion de la base, porque la llamada ocurria dentro de la transaccion).
// El documento pide respuestas en menos de 2 s (7.4.11): por eso la lectura
// espera 1,5 s por omision y la consulta que no llega a tiempo responde
// "servicio no disponible" solo para esa parte.
@Configuration
public class ClientesExternosConfig {

    @Bean
    public SimpleClientHttpRequestFactory fabricaDePeticionesDelChat(
        @Value("${app.http.timeout-conexion-ms:1000}") int conexion,
        @Value("${app.http.timeout-lectura-ms:1500}") int lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(conexion));
        fabrica.setReadTimeout(Duration.ofMillis(lectura));
        return fabrica;
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder(InterceptorDeTraza interceptorDeTraza,
                                                SimpleClientHttpRequestFactory fabricaDePeticionesDelChat) {
        return RestClient.builder()
            .requestFactory(fabricaDePeticionesDelChat)
            .requestInterceptor(interceptorDeTraza);
    }

    @Bean
    public RestClient inventarioRestClient(RestClient.Builder builder,
                                           @Value("${app.inventario.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public RestClient subastasRestClient(RestClient.Builder builder,
                                         @Value("${app.subastas.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public RestClient notificacionesRestClient(RestClient.Builder builder,
                                               @Value("${app.notificaciones.url}") String url) {
        return builder.baseUrl(url).build();
    }

    // B11: movimientos de creditos del propio jugador (creditos.yaml), con su token.
    @Bean
    public RestClient finanzasRestClient(RestClient.Builder builder,
                                         @Value("${app.finanzas.url}") String url) {
        return builder.baseUrl(url).build();
    }

    // B11: torneos es dato publico (torneos.yaml, GET /torneos sin token).
    @Bean
    public RestClient torneosRestClient(RestClient.Builder builder,
                                        @Value("${app.torneos.url}") String url) {
        return builder.baseUrl(url).build();
    }

    // B11: la lista negra se llama con la URL completa de verificar.
    @Bean
    public RestClient listaNegraRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
