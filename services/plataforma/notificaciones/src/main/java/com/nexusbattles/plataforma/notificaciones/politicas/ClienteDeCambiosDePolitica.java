package com.nexusbattles.plataforma.notificaciones.politicas;

import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /parametros/politicas/cambios/posteriores} de
 * admin-parametros.yaml (1.1.0), con la traza de la peticion en curso
 * (regla 5) y sin credencial: la lectura es publica, como el catalogo de
 * parametros, y un token caducado solo la haria fallar. Los tiempos de espera
 * los pone {@link ConfiguracionDePoliticas}.
 *
 * <p>Sin reintentos: la llamada ocurre al entregar lo pendiente a una sesion;
 * si no hay lote, la siguiente sesion lo vuelve a pedir.
 */
public class ClienteDeCambiosDePolitica implements CambiosDePolitica {

    /** La ruta del contrato; la base ({@code PARAMETROS_URL}) ya lleva {@code /api/v1}. */
    static final String RUTA = "/parametros/politicas/cambios/posteriores";

    private final RestClient http;
    private final String base;
    private final int limite;

    public ClienteDeCambiosDePolitica(RestClient http, String base, int limite) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
        this.limite = limite;
    }

    @Override
    public LoteDeCambiosDePolitica consultar(Instant desde) {
        RespuestaDeLote respuesta;
        try {
            respuesta = http.get()
                    .uri(base + RUTA + "?desde={desde}&limite={limite}", desde.toString(), limite)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RespuestaDeLote.class);
        } catch (RestClientResponseException rechazo) {
            throw new PoliticasNoDisponibles("admin-parametros respondio " + rechazo.getStatusCode().value());
        } catch (RestClientException noResponde) {
            throw new PoliticasNoDisponibles("admin-parametros no responde: " + noResponde.getClass().getSimpleName());
        }
        if (respuesta == null || respuesta.hasta() == null || respuesta.cambios() == null) {
            throw new PoliticasNoDisponibles("admin-parametros respondio un lote que no se entiende");
        }
        return new LoteDeCambiosDePolitica(respuesta.hasta(), !Boolean.FALSE.equals(respuesta.completo()),
                respuesta.cambios());
    }

    /** El esquema LoteDeCambiosDePolitica del contrato, tal como llega. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeLote(Instant hasta, Boolean completo, List<CambioDePoliticaDeUso> cambios) {
    }
}
