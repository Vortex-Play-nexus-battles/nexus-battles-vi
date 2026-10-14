package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiPublicacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaDeSubastasDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ParticipacionDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

// ms-subastas-pujas.yaml (resumen y participaciones), ms-subastas-panel.yaml
// (publicaciones) con el token del propio jugador, y ms-subastas-listado.yaml
// (las activas) sin credencial: es dato publico.
@Component
public class SubastasClientHttp implements SubastasClient {

    private static final ParameterizedTypeReference<List<ParticipacionDto>> PARTICIPACIONES =
        new ParameterizedTypeReference<>() {
        };
    private static final ParameterizedTypeReference<List<MiPublicacionDto>> PUBLICACIONES =
        new ParameterizedTypeReference<>() {
        };

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

    @Override
    public List<ParticipacionDto> misParticipaciones(String tokenBearer) {
        List<ParticipacionDto> participaciones = subastasRestClient.get()
            .uri("/mis-pujas")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(PARTICIPACIONES);
        return participaciones == null ? List.of() : participaciones;
    }

    @Override
    public List<MiPublicacionDto> misPublicacionesActivas(String tokenBearer) {
        List<MiPublicacionDto> publicaciones = subastasRestClient.get()
            .uri(uri -> uri.path("/mis-subastas/publicadas").queryParam("estado", "ACTIVA").build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenBearer)
            .retrieve()
            .body(PUBLICACIONES);
        return publicaciones == null ? List.of() : publicaciones;
    }

    @Override
    public PaginaDeSubastasDto activas(String busqueda, int cuantas) {
        PaginaDeSubastasDto pagina = subastasRestClient.get()
            .uri(uri -> {
                uri.path("/subastas")
                    .queryParam("size", cuantas)
                    .queryParam("ordenarPor", "TIEMPO_RESTANTE");
                if (busqueda != null && !busqueda.isBlank()) {
                    uri.queryParam("q", busqueda);
                }
                return uri.build();
            })
            .retrieve()
            .body(PaginaDeSubastasDto.class);
        return pagina == null ? new PaginaDeSubastasDto(List.of(), 0) : pagina;
    }
}
