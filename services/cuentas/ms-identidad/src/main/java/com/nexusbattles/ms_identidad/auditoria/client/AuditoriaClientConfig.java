package com.nexusbattles.ms_identidad.auditoria.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AuditoriaClientConfig {

    @Bean
    public RestClient auditoriaRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
