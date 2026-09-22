package com.nexusbattles.ms_identidad.notificaciones.client;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el servicio de notificaciones.
 *
 * <p>Lleva la credencial de servicio de ms-identidad ({@link CredencialPropia},
 * ADR-005): {@code POST /api/v1/internal/notifications} es una ruta entre
 * servicios y notificaciones la reserva a tokens de servicio.
 */
@Configuration
public class NotificacionClientConfig {

    @Bean
    public RestClient notificacionRestClient(CredencialPropia credencial) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000); // 2 segundos
        requestFactory.setReadTimeout(2000);    // 2 segundos

        return RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(credencial)
            .build();
    }
}
