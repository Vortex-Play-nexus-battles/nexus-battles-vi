package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class ClienteDeUsuarios implements FuenteDeUsuarios {

    private static final String RUTA = "/api/v1/admin/jugadores/indicadores?desde={desde}&hasta={hasta}";

    private final RestClient http;
    private final String base;

    /** @param base servidor de ms-identidad sin {@code /api/v1} (la ruta del contrato ya lo lleva) */
    public ClienteDeUsuarios(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public Indicadores consultar(String desde, String hasta, String autorizacion) {
        try {
            RestClient.RequestHeadersSpec<?> peticion = http.get().uri(base + RUTA, desde, hasta);
            if (autorizacion != null && !autorizacion.isBlank()) {
                peticion = peticion.header(HttpHeaders.AUTHORIZATION, autorizacion);
            }
            Indicadores indicadores = peticion.retrieve().body(Indicadores.class);
            if (indicadores == null) {
                throw new NoDisponible("ms-identidad respondio vacio");
            }
            return indicadores;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden sinPermiso) {
            throw new NoDisponible("ms-identidad nego el permiso (se necesita un administrador con GESTIONAR_CUENTAS; "
                    + "una credencial de servicio no basta)");
        } catch (HttpClientErrorException.BadRequest rango) {
            throw new NoDisponible("ms-identidad no acepto el rango pedido (tiene un tope tecnico de dias)");
        } catch (RestClientException noResponde) {
            throw new NoDisponible("ms-identidad no responde: " + noResponde.getMessage());
        }
    }
}
