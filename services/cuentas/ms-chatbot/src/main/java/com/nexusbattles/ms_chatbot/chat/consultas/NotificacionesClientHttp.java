package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificacionesClientHttp implements NotificacionesClient {

    private final RestClient notificacionesRestClient;

    public NotificacionesClientHttp(@Qualifier("notificacionesRestClient") RestClient notificacionesRestClient) {
        this.notificacionesRestClient = notificacionesRestClient;
    }

    @Override
    public BandejaResponseDto consultarBandeja(String tokenBearer, String uid) {
        return notificacionesRestClient.get()
            .uri("/users/{usuarioId}/notifications", uid)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(BandejaResponseDto.class);
    }
}
