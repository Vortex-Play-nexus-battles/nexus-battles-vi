package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Cliente de {@link FuenteDeReportes}: misma politica de fallos que {@link ClienteDeUsuarios}. */
public class ClienteDeReportes implements FuenteDeReportes {

    private static final String RUTA = "/api/v1/comentarios/moderacion/reportes/frecuencia?desde={desde}&hasta={hasta}";

    private final RestClient http;
    private final String base;

    /** @param base servidor de comentarios sin {@code /api/v1} (la ruta del contrato ya lo lleva) */
    public ClienteDeReportes(RestClient http, String base) {
        this.http = http;
        this.base = base.replaceAll("/+$", "");
    }

    @Override
    public Frecuencia consultar(OffsetDateTime desde, OffsetDateTime hasta, String autorizacion) {
        try {
            RestClient.RequestHeadersSpec<?> peticion = http.get()
                    .uri(base + RUTA, desde.toInstant().toString(), hasta.toInstant().toString());
            if (autorizacion != null && !autorizacion.isBlank()) {
                peticion = peticion.header(HttpHeaders.AUTHORIZATION, autorizacion);
            }
            Frecuencia frecuencia = peticion.retrieve().body(Frecuencia.class);
            if (frecuencia == null) {
                throw new NoDisponible(CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA, "comentarios respondio vacio");
            }
            Optional<String> falta = frecuencia.incompleto();
            if (falta.isPresent()) {
                throw new NoDisponible(CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA,
                        "comentarios respondio sin «" + falta.get()
                        + "» (contrato comentarios 1.15.0): no se publica un 0 que comentarios no dio");
            }
            return frecuencia;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden sinPermiso) {
            throw new NoDisponible(CausaDeIndisponibilidad.SIN_PERMISO,
                    "comentarios nego el permiso (la frecuencia de reportes es de quien modera)");
        } catch (HttpClientErrorException.BadRequest periodo) {
            throw new NoDisponible(CausaDeIndisponibilidad.RANGO_NO_ADMITIDO,
                    "comentarios no acepto el periodo pedido (como mucho 366 dias)");
        } catch (ResourceAccessException | RestClientResponseException noResponde) {
            throw new NoDisponible(CausaDeIndisponibilidad.NO_RESPONDE,
                    "comentarios no responde: " + noResponde.getMessage());
        } catch (RestClientException respuestaIlegible) {
            throw new NoDisponible(CausaDeIndisponibilidad.RESPUESTA_INCOMPLETA,
                    "comentarios respondio algo que no es la frecuencia del contrato: "
                    + respuestaIlegible.getMessage());
        }
    }
}
