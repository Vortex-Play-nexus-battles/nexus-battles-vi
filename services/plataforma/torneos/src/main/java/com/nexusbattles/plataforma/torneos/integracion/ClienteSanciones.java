package com.nexusbattles.plataforma.torneos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.torneos.torneo.ConsultaDeSanciones;
import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/** moderacion-sanciones-consulta.yaml 1.1.0: GET /sanciones/usuarios/{uid}/activa. Fail-closed (D-14). */
public class ClienteSanciones implements ConsultaDeSanciones {

    private final RestClient http;
    private final String base;

    public ClienteSanciones(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public boolean sancionado(UUID jugador) {
        try {
            Respuesta respuesta = http.get()
                    .uri(base + "/sanciones/usuarios/{uid}/activa", jugador)
                    .retrieve()
                    .body(Respuesta.class);
            if (respuesta == null) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.SANCIONES_NO_DISPONIBLES,
                        "sanciones respondio vacio");
            }
            return respuesta.sancionActiva();
        } catch (RestClientException noResponde) {
            throw new TorneoRechazado(TorneoRechazado.Motivo.SANCIONES_NO_DISPONIBLES,
                    "no se pudo consultar las sanciones: " + noResponde.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(boolean sancionActiva, String tipo) { }
}
