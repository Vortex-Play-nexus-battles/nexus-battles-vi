package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MisionActivaDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class MisionesClientHttp implements MisionesClient {

    private static final ParameterizedTypeReference<List<MisionActivaDto>> LISTA_DE_MISIONES =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient misionesRestClient;

    public MisionesClientHttp(@Qualifier("misionesRestClient") RestClient misionesRestClient) {
        this.misionesRestClient = misionesRestClient;
    }

    @Override
    public List<MisionActivaDto> enCurso(String tokenBearer) {
        List<MisionActivaDto> misiones = misionesRestClient.get()
            .uri("/api/v1/misiones/en-curso")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(LISTA_DE_MISIONES);
        return misiones == null ? List.of() : misiones;
    }
}
