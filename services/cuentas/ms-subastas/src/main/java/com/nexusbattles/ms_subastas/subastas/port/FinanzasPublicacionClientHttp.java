package com.nexusbattles.ms_subastas.subastas.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adaptador de comisiones de HU-SUB-001, independiente del cliente de creditos de pujas. */
@Component
public class FinanzasPublicacionClientHttp implements FinanzasPublicacionClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    @Autowired
    public FinanzasPublicacionClientHttp(
            @Value("${app.finanzas.base-url:http://localhost:8093/api/v1}") String baseUrl,
            @Value("${app.finanzas.timeout-ms:5000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper, Duration.ofMillis(timeoutMs));
    }

    public FinanzasPublicacionClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        Objects.requireNonNull(baseUri, "La URL de finanzas es obligatoria");
        if (!("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null || baseUri.getQuery() != null || baseUri.getFragment() != null
                || baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("La URL de finanzas debe ser una base HTTP valida");
        }
        this.baseUri = URI.create(baseUri.toString().replaceAll("/+$", "") + "/");
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("El timeout debe ser positivo");
    }

    @Override
    public void debitarComision(UUID jugadorUid, BigDecimal monto, UUID subastaId, String concepto) {
        if (jugadorUid == null) throw new FinanzasPublicacionClientException("El uid del jugador es obligatorio");
        if (monto == null || monto.signum() <= 0) throw new FinanzasPublicacionClientException("El monto debe ser positivo");
        validarTexto(concepto, "concepto");
        String refId = refId(subastaId);
        post("debitar", new Debito(jugadorUid, monto, refId, concepto), refId);
    }

    @Override
    public void compensarDebito(UUID subastaId, String motivo) {
        validarTexto(motivo, "motivo");
        String refId = refId(subastaId);
        post("reversar", new Reversa(refId, motivo), refId);
    }

    private static String refId(UUID subastaId) {
        if (subastaId == null) throw new FinanzasPublicacionClientException("El id de subasta es obligatorio");
        return "sub-publicacion-" + subastaId;
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new FinanzasPublicacionClientException("El " + campo + " es obligatorio");
    }

    private void post(String operacion, Object payload, String refId) {
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new FinanzasPublicacionClientException("No se pudo serializar la solicitud a finanzas", e);
        }
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("creditos/" + operacion))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FinanzasPublicacionClientException("No se pudo contactar a finanzas", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinanzasPublicacionClientException("Operacion de finanzas interrumpida", e);
        }
        if (response.statusCode() != 200) {
            throw new FinanzasPublicacionClientException("Respuesta inesperada de finanzas: " + response.statusCode());
        }
        try {
            Resultado resultado = objectMapper.readValue(response.body(), Resultado.class);
            if (resultado == null || !refId.equals(resultado.refId())) {
                throw new FinanzasPublicacionClientException("Finanzas devolvio un refId distinto o ausente");
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new FinanzasPublicacionClientException("Respuesta de finanzas invalida", e);
        }
    }

    private record Debito(UUID uid, BigDecimal monto, String refId, String concepto) { }
    private record Reversa(String refId, String motivo) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Resultado(String refId) { }
}
