package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.plataforma.observabilidad.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

// Clientes HTTP hacia inventario, subastas y notificaciones (HU-CHA-008).
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
@Configuration
public class ClientesExternosConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder(InterceptorDeTraza interceptorDeTraza) {
        return RestClient.builder().requestInterceptor(interceptorDeTraza);
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
}
