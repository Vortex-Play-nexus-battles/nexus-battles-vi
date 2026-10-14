package nexus.combate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ClienteTransferidorEquipoHttp implements TransferidorEquipo {

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenDeServicio token;

    public ClienteTransferidorEquipoHttp(URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public ClienteTransferidorEquipoHttp(URI baseUri, HttpClient httpClient) {
        this(baseUri, httpClient, null);
    }

    public ClienteTransferidorEquipoHttp(URI baseUri, HttpClient httpClient, TokenDeServicio token) {
        this.baseUri = Objects.requireNonNull(baseUri, "La URL de inventario es obligatoria");
        this.httpClient = Objects.requireNonNull(httpClient, "El cliente HTTP es obligatorio");
        this.objectMapper = new ObjectMapper();
        this.token = token;
    }

    @Override
    public void transferir(String operacionId, List<TransferenciaEquipo> transferencias) {
        exigirTexto(operacionId, "operacionId");
        Objects.requireNonNull(transferencias, "Las transferencias son obligatorias");
        if (transferencias.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos una transferencia");
        }
        String cuerpo;
        try {
            cuerpo = objectMapper.writeValueAsString(new SolicitudTransferencia(transferencias));
        } catch (IOException excepcion) {
            throw new IntegracionBotinException("No se pudo preparar la transferencia", excepcion);
        }
        HttpRequest.Builder constructor = HttpRequest.newBuilder(construirUri())
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Idempotency-Key", operacionId)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5));
        if (token != null) {
            constructor.header("Authorization", "Bearer " + token.portador());
        }
        HttpRequest peticion = constructor.build();
        HttpResponse<String> respuesta = enviar(peticion);
        if (respuesta.statusCode() != 200) {
            throw new IntegracionBotinException(
                    "Inventario respondio " + respuesta.statusCode() + " al transferir el equipo");
        }
    }

    private HttpResponse<String> enviar(HttpRequest peticion) {
        try {
            return httpClient.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException excepcion) {
            throw new IntegracionBotinException("No se pudo transferir el equipo", excepcion);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
            throw new IntegracionBotinException("Se interrumpio la transferencia del equipo", excepcion);
        }
    }

    private URI construirUri() {
        try {
            return new URI(
                    baseUri.getScheme(),
                    baseUri.getAuthority(),
                    "/api/v1/inventario/transferencias-combate",
                    null,
                    null);
        } catch (URISyntaxException excepcion) {
            throw new IntegracionBotinException("No se pudo construir la URL de inventario", excepcion);
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }

    private record SolicitudTransferencia(List<TransferenciaEquipo> transferencias) {
    }
}
