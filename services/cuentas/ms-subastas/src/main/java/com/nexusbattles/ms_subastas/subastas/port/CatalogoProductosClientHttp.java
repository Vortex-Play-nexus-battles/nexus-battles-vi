package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Adaptador real de GET /api/v1/productos/{id}. */
@Component
public class CatalogoProductosClientHttp implements CatalogoProductosClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    @Autowired
    public CatalogoProductosClientHttp(
            @Value("${app.catalogo.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.catalogo.timeout-ms:5000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(), objectMapper,
                Duration.ofMillis(timeoutMs));
    }

    public CatalogoProductosClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public Optional<Producto> buscar(UUID productoId) {
        if (productoId == null) throw new CatalogoProductosClientException("El id de producto es obligatorio");
        HttpRequest request = HttpRequest.newBuilder(uri(productoId))
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CatalogoProductosClientException("No se pudo contactar al catálogo", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogoProductosClientException("Consulta al catálogo interrumpida", e);
        }
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() != 200) {
            throw new CatalogoProductosClientException("Respuesta inesperada del catálogo: " + response.statusCode());
        }
        return Optional.of(parsear(response.body(), productoId));
    }

    private URI uri(UUID productoId) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), "/api/v1/productos/" + productoId, null, null);
        } catch (URISyntaxException e) {
            throw new CatalogoProductosClientException("No se pudo construir la URL del catálogo", e);
        }
    }

    private Producto parsear(String body, UUID solicitado) {
        try {
            ProductoCreado dto = objectMapper.readValue(body, ProductoCreado.class);
            if (dto.id() == null || dto.nombre() == null || dto.tipo() == null || dto.estado() == null) {
                throw new CatalogoProductosClientException("Respuesta del catálogo incompleta");
            }
            UUID id = UUID.fromString(dto.id());
            if (!solicitado.equals(id)) throw new CatalogoProductosClientException("El catálogo devolvió otro producto");
            TipoProducto tipo = TipoProducto.valueOf(dto.tipo());
            boolean subastable = !dto.premium() && !"SUSPENDIDO".equalsIgnoreCase(dto.estado());
            return new Producto(id, dto.nombre(), tipo, null, dto.imagen(), dto.descripcion(), null, subastable);
        } catch (CatalogoProductosClientException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new CatalogoProductosClientException("Respuesta del catálogo inválida", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductoCreado(String id, String nombre, String imagen, String descripcion,
                                   String tipo, boolean premium, String estado) { }
}
