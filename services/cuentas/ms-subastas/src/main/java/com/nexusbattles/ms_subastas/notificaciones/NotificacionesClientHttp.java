package com.nexusbattles.ms_subastas.notificaciones;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * Adaptador de {@code POST /internal/notifications}. Mismo estilo que
 * {@code CatalogoProductosClientHttp}: HttpClient del JDK y constructor
 * inyectable para poder probarlo sin levantar el otro servicio.
 */
@Component
public class NotificacionesClientHttp implements NotificacionesClient {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesClientHttp.class);

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    @Autowired
    public NotificacionesClientHttp(
            @Value("${app.notificaciones.base-url:http://localhost:8085/api/v1}") String baseUrl,
            @Value("${app.notificaciones.timeout-ms:3000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this(URI.create(baseUrl),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper, Duration.ofMillis(timeoutMs));
    }

    public NotificacionesClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public void entregar(Aviso aviso) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(baseUri + "/internal/notifications"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoDe(aviso)))
                .build();

        final HttpResponse<String> respuesta;
        try {
            respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new NotificacionesClientException("No se pudo contactar al modulo de notificaciones", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotificacionesClientException("Entrega del aviso interrumpida", e);
        }

        if (respuesta.statusCode() == 201) {
            return;
        }

        // 409 significa "ya existe un aviso con ese identificador": el intento
        // anterior si llego y lo que se perdio fue la respuesta. El aviso esta
        // donde tiene que estar, asi que esto es exito, no fallo. Tratarlo como
        // error dejaria la fila sin marcar y el drenador la reintentaria para
        // siempre.
        if (respuesta.statusCode() == 409) {
            log.debug("El aviso {} ya estaba en la bandeja; se da por entregado", aviso.eventoId());
            return;
        }

        // 400 es culpa de este servicio (payload mal armado) y reintentarlo no
        // lo va a arreglar, pero tampoco se descarta en silencio un aviso que
        // la historia exige: se deja sin marcar y visible en el log.
        throw new NotificacionesClientException(
                "Respuesta inesperada del modulo de notificaciones: " + respuesta.statusCode());
    }

    private String cuerpoDe(Aviso aviso) {
        try {
            return objectMapper.writeValueAsString(new AvisoJson(
                    aviso.destinatarioId().toString(),
                    aviso.eventoId().toString(),
                    aviso.tipo().name(),
                    aviso.titulo(),
                    aviso.cuerpo(),
                    // El contrato pide date-time; el ObjectMapper de este
                    // servicio no desactiva WRITE_DATES_AS_TIMESTAMPS, asi que
                    // un Instant saldria como numero epoch. Se formatea aqui.
                    DateTimeFormatter.ISO_INSTANT.format(aviso.creadaEn())));
        } catch (JsonProcessingException e) {
            throw new NotificacionesClientException("No se pudo serializar el aviso", e);
        }
    }

    /** Campos exactos que exige el contrato de notificaciones 1.0.0. */
    private record AvisoJson(String usuarioId, String id, String tipo,
                             String titulo, String cuerpo, String creadaEn) { }
}
