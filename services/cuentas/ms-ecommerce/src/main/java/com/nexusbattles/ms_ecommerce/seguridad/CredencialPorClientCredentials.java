package com.nexusbattles.ms_ecommerce.seguridad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Credencial de servicio por el grant {@code client_credentials} (RFC 6749
 * §4.4) contra el emisor de la plataforma: hoy ms-identidad,
 * {@code POST /api/v1/auth/token} (ADR-005).
 *
 * <p><b>Por que no la biblioteca de la plataforma.</b> Los modulos Gradle usan
 * {@code TokenDeServicioOAuth2} de shared/libs/plataforma-seguridad; este sigue
 * en Maven y no puede enlazarla (settings.gradle lo lista como pendiente de
 * migrar). Se reproducen sus reglas, que son las que la plataforma no
 * negocia: el cliente se autentica con {@code client_secret_basic}, el token
 * se guarda y se renueva {@value #MARGEN_SEGUNDOS} segundos antes de caducar,
 * y un fallo del emisor es «credencial no disponible», nunca un token
 * inventado. Lo que se gana haciendolo con el {@link RestClient} del servicio:
 * los mismos tiempos de espera que el resto de sus llamadas. El cliente de
 * token de Spring Security no los trae, y un emisor colgado dejaria colgada la
 * compra con el.
 *
 * <p>No depende de ninguna peticion en curso: la usa igual la compra que la
 * tarea programada que termina las ordenes a medias.
 */
public final class CredencialPorClientCredentials implements CredencialDeServicio {

    private static final Logger log = LoggerFactory.getLogger(CredencialPorClientCredentials.class);

    /** Cuanto antes de la caducidad se considera el token vencido. */
    static final long MARGEN_SEGUNDOS = 30;

    private final RestClient cliente;
    private final String urlDelEmisor;
    private final String clientId;
    private final String autorizacionBasica;
    private final Clock reloj;

    private String token;
    private Instant renovarDesde = Instant.MIN;

    /**
     * @param cliente      el {@code RestClient} con los tiempos de espera del servicio
     * @param urlDelEmisor URL completa del endpoint de token
     *                     ({@code http://srv-ms-identidad:8089/api/v1/auth/token})
     * @param clientId     identidad del servicio en el emisor
     * @param clientSecret su secreto; nunca se registra ni se expone
     */
    public CredencialPorClientCredentials(RestClient cliente, String urlDelEmisor, String clientId,
                                          String clientSecret, Clock reloj) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.urlDelEmisor = exigirTexto(urlDelEmisor, "la URL del emisor (DIRECTORIO_ACTIVO_URL)");
        this.clientId = exigirTexto(clientId, "el client_id del servicio (DIRECTORIO_ACTIVO_CLIENT_ID)");
        String secreto = exigirTexto(clientSecret, "el client_secret del servicio (DIRECTORIO_ACTIVO_CLIENT_SECRET)");
        this.autorizacionBasica = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + secreto).getBytes(StandardCharsets.UTF_8));
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public boolean configurada() {
        return true;
    }

    @Override
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
            log.warn("El emisor no entrego la credencial de servicio de {}: {}", clientId, fallo.getMessage());
            throw new CredencialDeServicioNoDisponibleException(
                    "No se pudo obtener la credencial de servicio de " + clientId, fallo);
        }
        if (respuesta == null || respuesta.accessToken() == null || respuesta.accessToken().isBlank()) {
            throw new CredencialDeServicioNoDisponibleException(
                    "El emisor respondio sin token para " + clientId);
        }
        return respuesta;
    }

    private static String exigirTexto(String valor, String que) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + que + ": sin eso el servicio no puede identificarse.");
        }
        return valor;
    }

    /** La respuesta del endpoint de token; se ignora todo lo demas. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDeToken(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }
}
