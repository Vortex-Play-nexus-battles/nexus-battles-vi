package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador HTTP REST de InventarioClient para comunicarse con ms-inventario.
 */
public class InventarioClientHttp implements InventarioClient {

    private static final Logger log = LoggerFactory.getLogger(InventarioClientHttp.class);

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public InventarioClientHttp(String baseUrl, long timeoutMs, ObjectMapper objectMapper) {
        this(URI.create(baseUrl),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper,
                Duration.ofMillis(timeoutMs));
    }

    public InventarioClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public Optional<ElementoInventario> buscar(String elementoInventarioId) {
        if (elementoInventarioId == null || elementoInventarioId.isBlank()) {
            throw new InventarioClientException("El identificador del elemento de inventario es obligatorio");
        }
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/inventario/elementos/" + elementoInventarioId))
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = enviar(request);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new InventarioClientException("Error inesperado al buscar en inventario: " + response.statusCode());
        }
        return Optional.of(parsearElemento(response.body(), elementoInventarioId));
    }

    @Override
    public void reservar(String elementoInventarioId, UUID propietarioId, UUID subastaId, String idempotencyKey) {
        if (elementoInventarioId == null || elementoInventarioId.isBlank()) {
            throw new InventarioClientException("El identificador del elemento de inventario es obligatorio");
        }
        SolicitudReserva solicitud = new SolicitudReserva(propietarioId, subastaId);
        String cuerpo = serializar(solicitud);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/v1/inventario/elementos/" + elementoInventarioId + "/reservas"))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        HttpResponse<String> response = enviar(builder.build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new InventarioClientException("Error al reservar elemento en inventario: " + response.statusCode());
        }
    }

    @Override
    public void liberarReserva(String elementoInventarioId, UUID subastaId, String idempotencyKey) {
        if (elementoInventarioId == null || elementoInventarioId.isBlank()) {
            throw new InventarioClientException("El identificador del elemento de inventario es obligatorio");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/v1/inventario/elementos/" + elementoInventarioId + "/reservas/" + subastaId))
                .timeout(timeout)
                .DELETE();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        HttpResponse<String> response = enviar(builder.build());
        if (response.statusCode() != 404 && (response.statusCode() < 200 || response.statusCode() >= 300)) {
            throw new InventarioClientException("Error al liberar reserva en inventario: " + response.statusCode());
        }
    }

    @Override
    public void transferirProducto(String elementoInventarioId, UUID nuevoPropietarioId, UUID subastaId, String idempotencyKey) {
        if (elementoInventarioId == null || elementoInventarioId.isBlank()) {
            throw new InventarioClientException("El identificador del elemento de inventario es obligatorio");
        }
        if (nuevoPropietarioId == null) {
            throw new InventarioClientException("El nuevo propietario es obligatorio para la transferencia");
        }
        SolicitudTransferencia solicitud = new SolicitudTransferencia(nuevoPropietarioId, subastaId);
        String cuerpo = serializar(solicitud);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/v1/inventario/elementos/" + elementoInventarioId + "/transferencias"))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        HttpResponse<String> response = enviar(builder.build());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new InventarioClientException("Error al transferir elemento en inventario: " + response.statusCode());
        }
    }

    private URI uri(String ruta) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), ruta, null, null);
        } catch (URISyntaxException e) {
            throw new InventarioClientException("No se pudo construir la URL de inventario: " + ruta, e);
        }
    }

    private HttpResponse<String> enviar(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InventarioClientException("No se pudo contactar a ms-inventario", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InventarioClientException("Petición a ms-inventario interrumpida", e);
        }
    }

    private String serializar(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new InventarioClientException("Error al serializar cuerpo para ms-inventario", e);
        }
    }

    private ElementoInventario parsearElemento(String body, String esperadoId) {
        try {
            ElementoJson dto = objectMapper.readValue(body, ElementoJson.class);
            UUID productoId = dto.productoId() != null ? UUID.fromString(dto.productoId()) : UUID.randomUUID();
            UUID propietarioId = dto.propietarioId() != null ? UUID.fromString(dto.propietarioId()) : null;
            return new ElementoInventario(dto.id() != null ? dto.id() : esperadoId, productoId, propietarioId, dto.enUso());
        } catch (IOException | IllegalArgumentException e) {
            throw new InventarioClientException("Respuesta de inventario inválida", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ElementoJson(String id, String productoId, String propietarioId, boolean enUso) {}

    private record SolicitudReserva(UUID propietarioId, UUID subastaId) {}

    private record SolicitudTransferencia(UUID nuevoPropietarioId, UUID subastaId) {}
}
