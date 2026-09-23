package com.nexusbattles.ms_chatbot.chat.consultas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientesExternosConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
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
