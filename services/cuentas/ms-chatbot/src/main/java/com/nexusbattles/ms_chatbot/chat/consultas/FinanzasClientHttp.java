package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FinanzasClientHttp implements FinanzasClient {

    private final RestClient finanzasRestClient;

    public FinanzasClientHttp(@Qualifier("finanzasRestClient") RestClient finanzasRestClient) {
        this.finanzasRestClient = finanzasRestClient;
    }

    @Override
    public PaginaMovimientosDto movimientos(String tokenBearer, String uid, int tamano) {
        return finanzasRestClient.get()
            .uri("/creditos/{uid}/movimientos?page=0&size={tamano}", uid, tamano)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(PaginaMovimientosDto.class);
    }
}
