package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventarioClientHttp implements InventarioClient {

    private final RestClient inventarioRestClient;

    public InventarioClientHttp(@Qualifier("inventarioRestClient") RestClient inventarioRestClient) {
        this.inventarioRestClient = inventarioRestClient;
    }

    @Override
    public PaginaInventarioDto consultarInventario(String tokenBearer, int pagina) {
        return inventarioRestClient.get()
            .uri("/api/v1/inventario/elementos?pagina={pagina}", pagina)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(PaginaInventarioDto.class);
    }
}
