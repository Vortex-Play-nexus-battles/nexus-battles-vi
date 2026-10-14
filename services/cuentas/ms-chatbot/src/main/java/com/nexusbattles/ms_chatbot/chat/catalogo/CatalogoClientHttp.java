package com.nexusbattles.ms_chatbot.chat.catalogo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogoClientHttp implements CatalogoClient {

    private final RestClient productosRestClient;

    public CatalogoClientHttp(@Qualifier("productosRestClient") RestClient productosRestClient) {
        this.productosRestClient = productosRestClient;
    }

    // Sin cabecera Authorization a proposito: con token de servicio o de
    // administrador el catalogo devuelve la proyeccion completa (campos
    // internos y suspendidos). El chat solo debe ver lo que ve un visitante.
    @Override
    public PaginaDelCatalogoDto pagina(int numero, int tamano) {
        return productosRestClient.get()
            .uri("/api/v1/productos?page={pagina}&size={tamano}", numero, tamano)
            .retrieve()
            .body(PaginaDelCatalogoDto.class);
    }
}
