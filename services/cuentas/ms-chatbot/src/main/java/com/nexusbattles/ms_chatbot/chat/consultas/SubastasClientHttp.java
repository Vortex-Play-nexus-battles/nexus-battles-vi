package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SubastasClientHttp implements SubastasClient {

    private final RestClient subastasRestClient;

    public SubastasClientHttp(@Qualifier("subastasRestClient") RestClient subastasRestClient) {
        this.subastasRestClient = subastasRestClient;
    }

    @Override
    public MiResumenDto consultarMiResumen(String tokenBearer) {
        return subastasRestClient.get()
            .uri("/mis-pujas/resumen")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(MiResumenDto.class);
    }
}
