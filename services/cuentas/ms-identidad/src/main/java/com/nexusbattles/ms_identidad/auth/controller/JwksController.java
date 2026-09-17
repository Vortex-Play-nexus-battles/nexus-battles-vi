package com.nexusbattles.ms_identidad.auth.controller;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Publica la clave <b>publica</b> con la que cualquiera puede verificar los
 * tokens que emite este servicio, en el formato JWK Set de la RFC 7517.
 *
 * <p>Es el equivalente del {@code /protocol/openid-connect/certs} de Keycloak.
 * Los servicios de la plataforma lo configuran como {@code jwk-set-uri} y
 * Spring Security se encarga del resto: descargarlo, cachearlo y elegir la
 * clave por {@code kid}. Cuando la identidad se mueva a Keycloak solo cambia
 * esa URL. Ver {@code docs/gobierno/ADR-002-identidad-de-usuario.md}.
 *
 * <p><b>Es publico a proposito</b> y no expone ningun secreto: una clave
 * publica sirve para comprobar firmas, nunca para producirlas. Tiene que ser
 * accesible sin autenticacion porque lo consulta quien todavia no ha validado
 * ningun token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class JwksController {

    private final ClavesDeFirma claves;

    public JwksController(ClavesDeFirma claves) {
        this.claves = claves;
    }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        RSAPublicKey publica = claves.publica();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", claves.identificador(),
                "n", enBase64Url(publica.getModulus()),
                "e", enBase64Url(publica.getPublicExponent())
        )));
    }

    /**
     * Entero sin signo en base64url sin relleno, como exige la RFC 7518 §6.3.1.
     *
     * <p>{@link BigInteger#toByteArray()} antepone un cero cuando el bit mas
     * alto esta puesto, para marcar el signo; ese cero no forma parte del valor
     * y hay que quitarlo o el modulo publicado no coincide con el de la clave.
     */
    private static String enBase64Url(BigInteger valor) {
        byte[] bytes = valor.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] sinSigno = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, sinSigno, 0, sinSigno.length);
            bytes = sinSigno;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
