package com.nexusbattles.ms_identidad.auth.correo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CorreoClientConfig {

    @Bean
    public RestClient correoRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000); // 2 segundos
        requestFactory.setReadTimeout(2000);    // 2 segundos

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
