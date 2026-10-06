package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

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
            Optional<String> falta = indicadores.incompleto();
            if (falta.isPresent()) {
                throw new NoDisponible("ms-identidad respondio sin «" + falta.get()
                        + "» (contrato ms-identidad-admin 1.3.0): no se publica un 0 que identidad no dio");
            }
            return indicadores;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden sinPermiso) {
            throw new NoDisponible("ms-identidad nego el permiso (se necesita un administrador con GESTIONAR_CUENTAS; "
                    + "una credencial de servicio no basta)");
        } catch (HttpClientErrorException.BadRequest rango) {
            throw new NoDisponible("ms-identidad no acepto el rango pedido (tiene un tope tecnico de dias)");
        } catch (ResourceAccessException | RestClientResponseException noResponde) {
            // Caido, plazo vencido (2 s para conectar, 3 s para responder) o un error suyo (5xx).
            throw new NoDisponible("ms-identidad no responde: " + noResponde.getMessage());
        } catch (RestClientException respuestaIlegible) {
            // Respondio, pero con algo que no se lee como los indicadores: JSON roto u otro tipo de contenido.
            throw new NoDisponible("ms-identidad respondio algo que no son los indicadores del contrato: "
                    + respuestaIlegible.getMessage());
        }
    }
}
