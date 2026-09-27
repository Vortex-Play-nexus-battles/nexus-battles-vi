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
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador HTTP real de {@link ResolutorDeProducto}: consume
 * {@code GET /api/v1/productos/{id}} (productos.yaml, HU-INV-007/PR #203).
 * Mismo patron que {@link ResolutorDeEstadisticasHeroeHttp}: HttpClient plano
 * con timeout, URI armada con el constructor de 5 argumentos (nunca
 * URLEncoder) y deserializacion con un record privado
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} que ignora el resto del
 * esquema {@code ProductoCreado} — este resolutor solo necesita tipo, nombre,
 * prototipo, estado (para no agregar un producto SUSPENDIDO) y, desde B4, la
 * parte de una armadura.
 *
 * <p>B4: la consulta lleva el token de quien llamo al inventario
 * ({@link PortadorDelLlamador}): sin token, el catalogo ya no muestra los
 * productos suspendidos. Si el catalogo rechaza ese token (401: su JWKS no
 * responde, o el token vencio en el camino), se repite sin el: la lectura
 * publica sigue sirviendo todo lo que no esta suspendido, y un fallo de
 * identidad del catalogo no deja al inventario sin catalogo.
 */
@Component
public class ResolutorDeProductoHttp implements ResolutorDeProducto {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Supplier<Optional<String>> portador;

    @Autowired
    public ResolutorDeProductoHttp(@Value("${productos.base-url}") String baseUrl) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                PortadorDelLlamador::actual);
    }

    /** Sin reenviar credencial: la lectura publica. */
    public ResolutorDeProductoHttp(URI baseUri, HttpClient httpClient) {
        this(baseUri, httpClient, Optional::empty);
    }

    public ResolutorDeProductoHttp(URI baseUri, HttpClient httpClient, Supplier<Optional<String>> portador) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.portador = portador;
    }

    @Override
    public DetalleProducto resolver(String productoId) {
        URI uri = construirUriDeProducto(productoId);

        Optional<String> token = portador.get();
        HttpResponse<String> respuesta = consultar(uri, token, productoId);
        if (respuesta.statusCode() == 401 && token.isPresent()) {
            respuesta = consultar(uri, Optional.empty(), productoId);
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

    private HttpResponse<String> consultar(URI uri, Optional<String> token, String productoId) {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5));
        token.ifPresent(valor -> peticion.header("Authorization", "Bearer " + valor));
        try {
            return httpClient.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ResolutorDeProductoException(
                    "No se pudo contactar al servicio de productos para '" + productoId + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResolutorDeProductoException(
                    "Consulta a productos interrumpida para '" + productoId + "'", e);
        }
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
            return new DetalleProducto(
                    producto.nombre(), producto.tipo(), producto.prototipo(), producto.estado(), producto.parte());
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
    private record ProductoJson(String nombre, String tipo, String prototipo, String estado, String parte) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProblemaJson(String detail) {
    }
}
