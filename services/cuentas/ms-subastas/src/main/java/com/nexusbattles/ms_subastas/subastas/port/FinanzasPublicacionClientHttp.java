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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import com.nexusbattles.ms_subastas.seguridad.PortadorDeServicio;

/** Adaptador de comisiones de HU-SUB-001, independiente del cliente de creditos de pujas. */
@Component
public class FinanzasPublicacionClientHttp implements FinanzasPublicacionClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final PortadorDeServicio credencial;

    /**
     * @param tokenDeServicio credencial de ms-subastas ante ms-finanzas (ADR-005),
     *        presente cuando {@code DIRECTORIO_ACTIVO_CLIENT_ID} esta configurado.
     *        Desde #455 {@code /creditos/debitar} y {@code /creditos/reversar}
     *        exigen {@code ROLE_SERVICIO}: sin credencial, la comision de
     *        publicacion no se puede cobrar y HU-SUB-001 responde 503.
     */
    @Autowired
    public FinanzasPublicacionClientHttp(
            @Value("${app.finanzas.base-url:http://localhost:8093/api/v1}") String baseUrl,
            @Value("${app.finanzas.timeout-ms:5000}") long timeoutMs,
            ObjectMapper objectMapper,
            ObjectProvider<TokenDeServicio> tokenDeServicio) {
        this(URI.create(baseUrl), HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(),
                objectMapper, Duration.ofMillis(timeoutMs), credencialDesde(tokenDeServicio));
    }

    public FinanzasPublicacionClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this(baseUri, httpClient, objectMapper, timeout, PortadorDeServicio.ninguno());
    }

    public FinanzasPublicacionClientHttp(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper,
                                         Duration timeout, PortadorDeServicio credencial) {
        this.credencial = Objects.requireNonNull(credencial, "credencial");
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

    private static final Logger log = LoggerFactory.getLogger(FinanzasPublicacionClientHttp.class);

    private static PortadorDeServicio credencialDesde(ObjectProvider<TokenDeServicio> proveedor) {
        TokenDeServicio token = proveedor.getIfAvailable();
        if (token == null) {
            // No se falla el arranque: este adaptador es un @Component que vive
            // tambien en los entornos con el doble de creditos. Pero se avisa
            // una vez, porque contra el ms-finanzas real cada comision moriria
            // con 401 y HU-SUB-001 no se podria demostrar.
            log.warn("ms-subastas no tiene credencial de servicio (DIRECTORIO_ACTIVO_CLIENT_ID vacio): "
                    + "las comisiones de publicacion saldran sin Authorization y ms-finanzas las rechazara.");
            return PortadorDeServicio.ninguno();
        }
        return PortadorDeServicio.de(token);
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
        HttpRequest request = firmada(HttpRequest.newBuilder(baseUri.resolve("creditos/" + operacion)))
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

    /** Credencial de ms-subastas (ADR-005); sin ella, la comision no se intenta cobrar. */
    private HttpRequest.Builder firmada(HttpRequest.Builder peticion) {
        try {
            return credencial.firmar(peticion);
        } catch (CredencialDeServicioNoDisponible sinCredencial) {
            throw new FinanzasPublicacionClientException(
                    "Sin credencial de servicio no se puede llamar a finanzas", sinCredencial);
        }
    }

    private record Debito(UUID uid, BigDecimal monto, String refId, String concepto) { }
    private record Reversa(String refId, String motivo) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Resultado(String refId) { }
}
