package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoResumenDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class TorneosClientHttp implements TorneosClient {

    private final RestClient torneosRestClient;

    public TorneosClientHttp(@Qualifier("torneosRestClient") RestClient torneosRestClient) {
        this.torneosRestClient = torneosRestClient;
    }

    @Override
    public List<TorneoResumenDto> listar() {
        List<TorneoResumenDto> torneos = torneosRestClient.get()
            .uri("/torneos")
            .retrieve()
            .body(new ParameterizedTypeReference<List<TorneoResumenDto>>() { });
        return torneos == null ? List.of() : torneos;
    }

    @Override
    public TorneoDetalleDto obtener(UUID torneoId) {
        return torneosRestClient.get()
            .uri("/torneos/{torneoId}", torneoId)
            .retrieve()
            .body(TorneoDetalleDto.class);
    }
}
