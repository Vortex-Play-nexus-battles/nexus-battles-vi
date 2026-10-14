package nexus.combate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class ClienteCatalogoBotinHttp implements CatalogoBotin {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenDeServicio token;

    public ClienteCatalogoBotinHttp(URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public ClienteCatalogoBotinHttp(URI baseUri, HttpClient httpClient) {
        this(baseUri, httpClient, null);
    }

    public ClienteCatalogoBotinHttp(URI baseUri, HttpClient httpClient, TokenDeServicio token) {
        this.baseUri = Objects.requireNonNull(baseUri, "La URL de productos es obligatoria");
        this.httpClient = Objects.requireNonNull(httpClient, "El cliente HTTP es obligatorio");
        this.objectMapper = new ObjectMapper();
        this.token = token;
    }

    @Override
    public ProductoBotin consultar(String productoId) {
        exigirTexto(productoId, "productoId");
        HttpRequest.Builder constructor = HttpRequest.newBuilder(
                        construirUri("/api/v1/productos/" + productoId))
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5));
        autenticar(constructor);
        HttpRequest peticion = constructor.build();
        HttpResponse<String> respuesta = enviar(peticion, "consultar el producto " + productoId);
        if (respuesta.statusCode() == 404) {
            throw new IntegracionBotinException("El producto " + productoId + " no existe");
        }
        if (respuesta.statusCode() != 200) {
            throw new IntegracionBotinException(
                    "Productos respondio " + respuesta.statusCode() + " al consultar " + productoId);
        }
        try {
            ProductoJson producto = objectMapper.readValue(respuesta.body(), ProductoJson.class);
            return new ProductoBotin(
                    producto.id(),
                    producto.nombre(),
                    producto.tipo(),
                    producto.parte(),
                    producto.tasaDeCaida());
        } catch (IOException | RuntimeException excepcion) {
            throw new IntegracionBotinException(
                    "La respuesta de productos no contiene un objeto de botin valido",
                    excepcion);
        }
    }

    private HttpResponse<String> enviar(HttpRequest peticion, String operacion) {
        try {
            return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException excepcion) {
            throw new IntegracionBotinException("No se pudo " + operacion, excepcion);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
            throw new IntegracionBotinException("Se interrumpio la operacion para " + operacion, excepcion);
        }
    }

    private URI construirUri(String ruta) {
        try {
            return new URI(baseUri.getScheme(), baseUri.getAuthority(), ruta, null, null);
        } catch (URISyntaxException excepcion) {
            throw new IntegracionBotinException("No se pudo construir la URL de productos", excepcion);
        }
    }

    private void autenticar(HttpRequest.Builder peticion) {
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token.portador());
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductoJson(
            String id,
            String nombre,
            TipoBotin tipo,
            ParteArmaduraBotin parte,
            BigDecimal tasaDeCaida) {
    }
}
