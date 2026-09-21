package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.ArbitroDeTorneo;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * {@code POST /torneos/{id}/encuentros/{n}/resultado} con {@code ganadorUid}
 * (torneos.yaml 1.1.0), con la credencial de servicio de este modulo.
 */
public class ClienteTorneos implements ArbitroDeTorneo {

    private final RestClient http;
    private final String base;

    public ClienteTorneos(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public void informarGanador(UUID idTorneo, int numero, UUID ganadorUid, UUID idPartida) {
        try {
            http.post()
                    .uri(base + "/torneos/{id}/encuentros/{n}/resultado", idTorneo, numero)
                    .body(new Resultado(ganadorUid.toString(), idPartida.toString()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException rechazo) {
            throw new TorneoNoDisponible("torneos respondio " + rechazo.getStatusCode().value() + ": "
                    + rechazo.getResponseBodyAsString());
        } catch (RestClientException noResponde) {
            throw new TorneoNoDisponible("torneos no responde: " + noResponde.getMessage());
        }
    }

    record Resultado(String ganadorUid, String partidaId) { }
}
