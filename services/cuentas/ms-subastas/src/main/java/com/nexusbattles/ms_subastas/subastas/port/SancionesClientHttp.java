package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumidor del contrato moderacion-sanciones-consulta.yaml, sin fallback.
 *
 * <p>B2: desde la 1.4.0 {@code /activa} exige token (un jugador solo la suya,
 * un servicio la de cualquiera); la peticion va firmada con la credencial de
 * servicio de ms-subastas. Sin ella cada consulta daria 401 y, como este
 * cliente falla cerrado, nadie podria publicar ni pujar.
 */
@Component
public class SancionesClientHttp implements SancionesClient {
    private final URI baseUri;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final PortadorDeServicio portador;

    @Autowired
    public SancionesClientHttp(@Value("${app.sanciones.base-url:http://localhost:8086}") String baseUrl,
            @Value("${app.sanciones.timeout-ms:5000}") long timeoutMs, ObjectMapper mapper,
            ObjectProvider<TokenDeServicio> tokenDeServicio) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                mapper, Duration.ofMillis(timeoutMs), portadorDe(tokenDeServicio));
    }

    public SancionesClientHttp(URI baseUri, HttpClient http, ObjectMapper mapper, Duration timeout) {
        this(baseUri, http, mapper, timeout, PortadorDeServicio.ninguno());
    }

    public SancionesClientHttp(URI baseUri, HttpClient http, ObjectMapper mapper, Duration timeout,
            PortadorDeServicio portador) {
        this.baseUri = baseUri;
        this.http = http;
        this.mapper = mapper;
        this.timeout = timeout;
        this.portador = portador;
    }

    @Override
    public boolean tieneSancionActiva(UUID usuarioId) {
        if (usuarioId == null) throw new SancionesClientException("El uid es obligatorio para consultar sanciones");
        var request = portador.firmar(HttpRequest.newBuilder(
                        baseUri.resolve("/api/v1/sanciones/usuarios/" + usuarioId + "/activa")))
                .timeout(timeout).header("Accept", "application/json").GET().build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SancionesClientException("Respuesta inesperada de sanciones: " + response.statusCode());
            }
            var json = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(response.body());
            if (json == null || !json.isObject() || !json.path("sancionActiva").isBoolean()) {
                throw new SancionesClientException("Respuesta de sanciones invalida: sancionActiva debe ser booleano");
            }
            return json.get("sancionActiva").booleanValue();
        } catch (IOException e) {
            throw new SancionesClientException("Fallo HTTP o respuesta invalida de sanciones", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SancionesClientException("Consulta de sanciones interrumpida", e);
        }
    }

    /**
     * La credencial si esta configurada. Sin ella la peticion sale sin
     * {@code Authorization} y el destino responde 401, que este adaptador trata
     * como fallo (nunca como exito): el error se ve en la bitacora y en la
     * prueba de humo, no se esconde.
     */
    private static PortadorDeServicio portadorDe(ObjectProvider<TokenDeServicio> proveedor) {
        TokenDeServicio token = proveedor.getIfAvailable();
        return token == null ? PortadorDeServicio.ninguno() : PortadorDeServicio.de(token);
    }
}
