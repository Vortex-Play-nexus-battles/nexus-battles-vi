package com.nexusbattles.ms_identidad.auth.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Endpoint de token para el grant {@code client_credentials} (RFC 6749 §4.4)
 * — ADR-005.
 *
 * <p>Es la parte de un servidor de autorizacion que la plataforma necesita
 * hoy y no tiene: un lugar donde un servicio presente su {@code client_id} y
 * su secreto y reciba un token con el que hablarle a otro servicio. Habla el
 * mismo protocolo que Keycloak en {@code /protocol/openid-connect/token},
 * asi que {@code TokenDeServicioOAuth2} (plataforma-seguridad) lo consume sin
 * cambios: solo apunta {@code DIRECTORIO_ACTIVO_URL} aqui.
 *
 * <p>El cliente se autentica con HTTP Basic ({@code client_secret_basic}, la
 * forma por defecto de Spring) o con {@code client_id}/{@code client_secret}
 * en el cuerpo ({@code client_secret_post}). Cualquier otro grant responde
 * {@code unsupported_grant_type}; un cliente desconocido o con secreto
 * equivocado responde 401 {@code invalid_client}, sin decir cual de las dos
 * cosas fallo.
 *
 * <p>Este controlador no pasa por {@code SecurityInterceptor}: ese solo actua
 * sobre rutas con {@code @RequirePermission}, y aqui la credencial es la del
 * cliente, no la de un usuario.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class TokenController {

    private static final Logger BITACORA = LoggerFactory.getLogger(TokenController.class);
    private static final String GRANT_ADMITIDO = "client_credentials";

    private final ClientesDeServicio clientes;
    private final EmisorDeTokensDeServicio emisor;

    public TokenController(ClientesDeServicio clientes, EmisorDeTokensDeServicio emisor) {
        this.clientes = clientes;
        this.emisor = emisor;
    }

    @PostMapping(value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> emitir(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientIdDelCuerpo,
            @RequestParam(name = "client_secret", required = false) String secretoDelCuerpo,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {

        if (grantType == null || grantType.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "Falta grant_type.");
        }
        if (!GRANT_ADMITIDO.equals(grantType)) {
            return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "Solo se admite el grant client_credentials.");
        }

        Credencial credencial = credencialDe(autorizacion, clientIdDelCuerpo, secretoDelCuerpo);
        if (credencial == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Falta la autenticacion del cliente (Basic o client_id/client_secret).");
        }
        if (!clientes.autentica(credencial.clientId(), credencial.secreto())) {
            BITACORA.warn("Credencial de servicio rechazada para client_id={}", credencial.clientId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ms-identidad\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", "invalid_client",
                            "error_description", "Cliente desconocido o secreto incorrecto."));
        }

        EmisorDeTokensDeServicio.TokenEmitido token = emisor.emitir(credencial.clientId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of(
                        "access_token", token.valor(),
                        "token_type", "Bearer",
                        "expires_in", token.expiraEnSegundos()));
    }

    private record Credencial(String clientId, String secreto) {
    }

    private static Credencial credencialDe(String autorizacion, String clientId, String secreto) {
        if (autorizacion != null && autorizacion.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decodificado = new String(
                        Base64.getDecoder().decode(autorizacion.substring(6).strip()), StandardCharsets.UTF_8);
                int separador = decodificado.indexOf(':');
                if (separador > 0) {
                    return new Credencial(decodificado.substring(0, separador), decodificado.substring(separador + 1));
                }
            } catch (IllegalArgumentException ignorada) {
                // Basic mal formado: se trata como ausente.
            }
            return null;
        }
        if (clientId != null && !clientId.isBlank() && secreto != null) {
            return new Credencial(clientId, secreto);
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String codigo, String descripcion) {
        return ResponseEntity.status(estado)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("error", codigo, "error_description", descripcion));
    }
}
