package com.nexusbattles.comun.seguridad.servicio;

import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Credencial de servicio obtenida con el grant {@code client_credentials}
 * (RFC 6749 §4.4) contra el emisor de la plataforma — ADR-001.
 *
 * <p>No implementa nada del protocolo: delega en Spring Security OAuth2 Client,
 * que pide el token al emisor con {@code client_id}/{@code client_secret}, lo
 * guarda y lo vuelve a pedir cuando esta por caducar. Aqui solo se fija la
 * configuracion que la plataforma no negocia:
 *
 * <ul>
 *   <li>el emisor es el que apunta {@code DIRECTORIO_ACTIVO_URL} (la URL del
 *       realm de Keycloak) y el endpoint de token es el estandar OIDC de ese
 *       realm, {@code /protocol/openid-connect/token};</li>
 *   <li>el cliente se autentica con {@code client_secret_basic};</li>
 *   <li>el token se renueva {@value #MARGEN_SEGUNDOS} segundos antes de caducar,
 *       para que una peticion que sale justo al limite no llegue con un token
 *       vencido.</li>
 * </ul>
 *
 * <p>El «principal» que exige el gestor de Spring es el propio servicio: no hay
 * usuario, y por eso funciona igual desde un {@code @Scheduled} que desde una
 * peticion.
 */
public final class TokenDeServicioOAuth2 implements TokenDeServicio {

    /** Identificador interno del registro; no viaja a ningun sitio. */
    static final String REGISTRO = "servicio";

    /** Cuanto antes de la caducidad se considera el token vencido. */
    static final long MARGEN_SEGUNDOS = 30;

    private final String clientId;
    private final AuthorizedClientServiceOAuth2AuthorizedClientManager gestor;

    /**
     * @param urlDelEmisor URL del realm (por ejemplo
     *                     {@code https://keycloak/realms/nexus-battles}); sin
     *                     barra final
     * @param clientId     identidad del servicio en el emisor
     * @param clientSecret su secreto; nunca se registra ni se expone
     * @param reloj        reloj con el que se juzga la caducidad (inyectable
     *                     para las pruebas)
     */
    public TokenDeServicioOAuth2(String urlDelEmisor, String clientId, String clientSecret, Clock reloj) {
        exigirTexto(urlDelEmisor, "la URL del emisor (DIRECTORIO_ACTIVO_URL)");
        exigirTexto(clientId, "el client_id del servicio (DIRECTORIO_ACTIVO_CLIENT_ID)");
        exigirTexto(clientSecret, "el client_secret del servicio (DIRECTORIO_ACTIVO_CLIENT_SECRET)");
        Objects.requireNonNull(reloj, "reloj");

        this.clientId = clientId;

        ClientRegistration registro = ClientRegistration.withRegistrationId(REGISTRO)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(endpointDeToken(urlDelEmisor))
                .build();

        InMemoryClientRegistrationRepository registros = new InMemoryClientRegistrationRepository(registro);
        InMemoryOAuth2AuthorizedClientService autorizados = new InMemoryOAuth2AuthorizedClientService(registros);

        OAuth2AuthorizedClientProvider proveedor = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(cc -> cc
                        .clock(reloj)
                        .clockSkew(Duration.ofSeconds(MARGEN_SEGUNDOS)))
                .build();

        this.gestor = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registros, autorizados);
        this.gestor.setAuthorizedClientProvider(proveedor);
    }

    /**
     * Endpoint de token del emisor. Publico para que las pruebas lo comprueben.
     *
     * <p>Dos formas de configurarlo (ADR-005):
     * <ul>
     *   <li>la URL de un realm de Keycloak ({@code https://kc/realms/nexus}):
     *       se le anade la ruta OIDC estandar;</li>
     *   <li>la URL completa de un endpoint de token, reconocible porque termina
     *       en {@code /token} ({@code http://srv-ms-identidad:8089/api/v1/auth/token}):
     *       se usa tal cual. Es el caso de ms-identidad, que emite credenciales
     *       de servicio mientras no haya Keycloak.</li>
     * </ul>
     */
    public static String endpointDeToken(String urlDelEmisor) {
        String base = urlDelEmisor.endsWith("/")
                ? urlDelEmisor.substring(0, urlDelEmisor.length() - 1)
                : urlDelEmisor;
        if (base.endsWith("/token")) {
            return base;
        }
        return base + "/protocol/openid-connect/token";
    }

    @Override
    public String portador() {
        OAuth2AuthorizeRequest solicitud = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRO)
                .principal(clientId)
                .build();
        try {
            OAuth2AuthorizedClient autorizado = gestor.authorize(solicitud);
            if (autorizado == null) {
                throw new CredencialDeServicioNoDisponible(
                        "El emisor no entrego credencial para el servicio " + clientId, null);
            }
            return autorizado.getAccessToken().getTokenValue();
        } catch (OAuth2AuthorizationException fallo) {
            throw new CredencialDeServicioNoDisponible(
                    "No se pudo obtener la credencial del servicio " + clientId + ": "
                            + fallo.getError().getErrorCode(), fallo);
        }
    }

    private static void exigirTexto(String valor, String que) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + que + ": sin eso el servicio no puede identificarse.");
        }
    }
}
