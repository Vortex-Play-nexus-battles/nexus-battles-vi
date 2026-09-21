package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;

public class ClienteDeModeracion implements FuenteDeModeracion {

    private final RestClient http;
    private final String base;

    public ClienteDeModeracion(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public Agregados consultar(OffsetDateTime desde, OffsetDateTime hasta) {
        try {
            Agregados agregados = http.get()
                    .uri(base + "/sanciones/metricas?desde={desde}&hasta={hasta}", desde.toString(), hasta.toString())
                    .retrieve()
                    .body(Agregados.class);
            if (agregados == null) {
                throw new FuenteNoDisponible("moderacion-sanciones respondio vacio");
            }
            return agregados;
        } catch (RestClientException noResponde) {
            throw new FuenteNoDisponible("moderacion-sanciones no responde: " + noResponde.getMessage());
        }
    }
}
