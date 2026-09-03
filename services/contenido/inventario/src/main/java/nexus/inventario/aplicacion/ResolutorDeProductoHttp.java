package nexus.inventario.aplicacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador HTTP real de {@link ResolutorDeProducto}: consume
 * {@code GET /api/v1/productos/{id}} (productos.yaml, HU-INV-007/PR #203,
 * endpoint publico sin autenticacion). Mismo patron que
 * {@link ResolutorDeEstadisticasHeroeHttp}: HttpClient plano con timeout,
 * URI armada con el constructor de 5 argumentos (nunca URLEncoder) y
 * deserializacion con un record privado
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} que ignora el resto
 * del esquema {@code ProductoCreado} (id, imagen, descripcion, tiraje,
 * premium, estado, version, fechas y los campos propios de cada tipo) —
 * este resolutor solo necesita tipo, nombre y prototipo.
 */
@Component
public class ResolutorDeProductoHttp implements ResolutorDeProducto {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResolutorDeProductoHttp(@Value("${productos.base-url}") String baseUrl) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public ResolutorDeProductoHttp(URI baseUri, HttpClient httpClient) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public DetalleProducto resolver(String productoId) {
        URI uri = construirUriDeProducto(productoId);

        HttpRequest peticion = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> respuesta;
        try {
            respuesta = httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ResolutorDeProductoException(
                    "No se pudo contactar al servicio de productos para '" + productoId + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResolutorDeProductoException(
                    "Consulta a productos interrumpida para '" + productoId + "'", e);
        }

        if (respuesta.statusCode() == 404) {
            throw new ProductoNoEncontradoException(productoId, extraerDetalleDeProblema(respuesta.body()));
        }

        if (respuesta.statusCode() != 200) {
            throw new ResolutorDeProductoException(
                    "Respuesta inesperada (" + respuesta.statusCode() + ") al consultar '" + productoId + "'");
        }

        return parsearProducto(respuesta.body());
    }

    private URI construirUriDeProducto(String productoId) {
        try {
            return new URI(
                    baseUri.getScheme(),
                    baseUri.getAuthority(),
                    "/api/v1/productos/" + productoId,
                    null,
                    null);
        } catch (URISyntaxException e) {
            throw new ResolutorDeProductoException(
                    "Id de producto invalido para construir la URL: '" + productoId + "'", e);
        }
    }

    private DetalleProducto parsearProducto(String cuerpoJson) {
        try {
            ProductoJson producto = objectMapper.readValue(cuerpoJson, ProductoJson.class);
            return new DetalleProducto(producto.nombre(), producto.tipo(), producto.prototipo());
        } catch (IOException e) {
            throw new ResolutorDeProductoException("Respuesta de productos no se pudo interpretar: " + e.getMessage(), e);
        }
    }

    private String extraerDetalleDeProblema(String cuerpoJson) {
        try {
            ProblemaJson problema = objectMapper.readValue(cuerpoJson, ProblemaJson.class);
            return problema.detail() != null ? problema.detail() : "Producto no disponible";
        } catch (IOException e) {
            return "Producto no disponible";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductoJson(String nombre, String tipo, String prototipo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemaJson(String detail) {
    }
}
