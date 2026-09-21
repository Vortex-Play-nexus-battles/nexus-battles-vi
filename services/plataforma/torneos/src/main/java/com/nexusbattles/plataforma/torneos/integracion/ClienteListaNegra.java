package com.nexusbattles.plataforma.torneos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.torneos.torneo.FiltroDeNombres;
import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** moderacion-lista-negra.yaml: POST /lista-negra/verificar. Sin respuesta no se registra (fail-closed). */
public class ClienteListaNegra implements FiltroDeNombres {

    private final RestClient http;
    private final String url;

    public ClienteListaNegra(RestClient http, String url) {
        this.http = http;
        this.url = url;
    }

    @Override
    public boolean aprobado(String texto) {
        try {
            Verificacion respuesta = http.post()
                    .uri(url)
                    .body(new Solicitud(texto))
                    .retrieve()
                    .body(Verificacion.class);
            if (respuesta == null) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE,
                        "la lista negra respondio vacio");
            }
            return respuesta.aprobado();
        } catch (RestClientException noResponde) {
            throw new TorneoRechazado(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE,
                    "la lista negra no responde: " + noResponde.getMessage());
        }
    }

    record Solicitud(String texto) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verificacion(boolean aprobado, String motivo) { }
}
