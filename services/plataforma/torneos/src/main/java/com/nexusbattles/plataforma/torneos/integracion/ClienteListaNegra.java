package com.nexusbattles.plataforma.torneos.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.torneos.torneo.FiltroDeNombres;
import com.nexusbattles.plataforma.torneos.torneo.TorneoRechazado;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * moderacion-lista-negra.yaml 2.0.0: POST /lista-negra/verificar con
 * {@code contexto: NOMBRE_EQUIPO} (7.9: «el nombre y avatar deben cumplir la
 * politica explicita en el registro de usuarios»; la politica de moderacion
 * aplica RECHAZAR en ese contexto). Sin respuesta no se registra (fail-closed).
 */
public class ClienteListaNegra implements FiltroDeNombres {

    static final String CONTEXTO = "NOMBRE_EQUIPO";

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
                    .body(new Solicitud(texto, CONTEXTO))
                    .retrieve()
                    .body(Verificacion.class);
            if (respuesta == null) {
                throw new TorneoRechazado(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE,
                        "la lista negra respondio vacio");
            }
            return respuesta.aprobado();
        } catch (RestClientException noResponde) {
            throw new TorneoRechazado(TorneoRechazado.Motivo.LISTA_NEGRA_NO_DISPONIBLE,
                    "la lista negra no responde: " + noResponde.getClass().getSimpleName());
        }
    }

    record Solicitud(String texto, String contexto) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verificacion(boolean aprobado, String motivo) { }
}
