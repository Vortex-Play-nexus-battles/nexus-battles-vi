package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * La credencial de servicio de esta bitácora — SA-18, ADR-001 por el emisor de
 * ADR-005 ({@code POST /api/v1/auth/token} de ms-identidad, grant
 * {@code client_credentials}).
 *
 * <p>Las mismas reglas que la de ms-ecommerce, que tampoco puede enlazar la
 * biblioteca Gradle de la plataforma: el cliente se autentica con
 * {@code client_secret_basic}, el token se guarda y se renueva
 * {@value #MARGEN_SEGUNDOS} segundos antes de caducar, y un fallo del emisor es
 * «credencial no disponible», nunca un token inventado. El secreto no se
 * registra ni se expone.
 */
public final class CredencialDeServicio {

    /** Cuánto antes de la caducidad se considera el token vencido. */
    static final long MARGEN_SEGUNDOS = 30;

    private final RestClient cliente;
    private final String urlDelEmisor;
    private final String clientId;
    private final String autorizacionBasica;
    private final Clock reloj;

    private String token;
    private Instant renovarDesde = Instant.MIN;

    public CredencialDeServicio(RestClient cliente, String urlDelEmisor, String clientId, String clientSecret,
                                Clock reloj) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.urlDelEmisor = exigirTexto(urlDelEmisor, "la URL del emisor (DIRECTORIO_ACTIVO_URL)");
        this.clientId = exigirTexto(clientId, "el client_id de la bitácora (DIRECTORIO_ACTIVO_CLIENT_ID)");
        String secreto = exigirTexto(clientSecret, "el client_secret de la bitácora (DIRECTORIO_ACTIVO_CLIENT_SECRET)");
        this.autorizacionBasica = "Basic " + Base64.getEncoder()
            .encodeToString((clientId + ":" + secreto).getBytes(StandardCharsets.UTF_8));
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    /** Un token vigente para {@code Authorization: Bearer}. */
    public synchronized String portador() {
        Instant ahora = reloj.instant();
        if (token != null && ahora.isBefore(renovarDesde)) {
            return token;
        }
        RespuestaDeToken respuesta = pedirToken();
        token = respuesta.accessToken();
        long vigencia = respuesta.expiresIn() == null ? 0 : respuesta.expiresIn();
        renovarDesde = ahora.plus(Duration.ofSeconds(Math.max(0, vigencia - MARGEN_SEGUNDOS)));
        return token;
    }

    private RespuestaDeToken pedirToken() {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "client_credentials");
        RespuestaDeToken respuesta;
        try {
            respuesta = cliente.post()
                .uri(urlDelEmisor)
                .header(HttpHeaders.AUTHORIZATION, autorizacionBasica)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(formulario)
                .retrieve()
                .body(RespuestaDeToken.class);
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new CatalogoNoDisponibleException(
                "El emisor no entregó la credencial de servicio de " + clientId + ": " + fallo.getMessage(), fallo);
        }
        if (respuesta == null || respuesta.accessToken() == null || respuesta.accessToken().isBlank()) {
            throw new CatalogoNoDisponibleException("El emisor respondió sin token para " + clientId);
        }
        return respuesta;
    }

    private static String exigirTexto(String valor, String que) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + que + ".");
        }
        return valor;
    }

    /** La respuesta del endpoint de token; se ignora todo lo demás. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeToken(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn) {
    }
}
