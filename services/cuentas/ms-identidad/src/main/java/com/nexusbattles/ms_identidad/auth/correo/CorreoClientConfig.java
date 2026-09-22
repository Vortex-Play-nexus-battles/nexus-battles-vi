package com.nexusbattles.ms_identidad.auth.correo;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el servicio de correo.
 *
 * <p>Lleva la credencial de servicio de ms-identidad en cada peticion
 * ({@link CredencialPropia}, ADR-005): correo solo atiende a servicios
 * autenticados, porque desde el se pueden enviar codigos de recuperacion y
 * de confirmacion de cuenta a cualquier direccion.
 */
@Configuration
public class CorreoClientConfig {

    @Bean
    public RestClient correoRestClient(CredencialPropia credencial) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000); // 2 segundos
        requestFactory.setReadTimeout(2000);    // 2 segundos

        return RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(credencial)
            .build();
    }
}
