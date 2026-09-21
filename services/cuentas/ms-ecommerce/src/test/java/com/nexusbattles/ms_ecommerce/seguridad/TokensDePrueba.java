package com.nexusbattles.ms_ecommerce.seguridad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tokens de acceso <b>reales</b> para las pruebas de este modulo: firmados
 * RS256 con una clave generada al cargar la clase y verificados por un
 * {@link NimbusJwtDecoder} de verdad, el mismo tipo que Boot construye en
 * produccion a partir del JWKS.
 *
 * <p>Es la version minima de {@code EmisorDeTokensDePrueba} de
 * shared/libs/plataforma-seguridad (testFixtures), que este modulo no puede
 * enlazar por seguir en Maven. Reproduce la forma de ms-identidad (ADR-002):
 * sujeto = apodo, {@code uid}, {@code rol}, {@code ver}.
 */
public final class TokensDePrueba {

    private static final RSAKey CLAVE;
    private static final RSAKey CLAVE_AJENA;

    static {
        try {
            CLAVE = new RSAKeyGenerator(2048).keyID("prueba").generate();
            CLAVE_AJENA = new RSAKeyGenerator(2048).keyID("ajena").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private TokensDePrueba() {
    }

    /** Decodificador real con la clave publica del emisor de prueba. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Decodificador {
        @Bean
        JwtDecoder jwtDecoder() throws JOSEException {
            return NimbusJwtDecoder.withPublicKey(CLAVE.toRSAPublicKey()).build();
        }
    }

    public static String deJugador(String apodo, UUID uid) {
        return firmar(apodo, Map.of("uid", uid.toString(), "rol", "JUGADOR", "ver", 1), Duration.ofMinutes(15), CLAVE);
    }

    public static String deUsuario(String apodo, UUID uid, String rol) {
        return firmar(apodo, Map.of("uid", uid.toString(), "rol", rol, "ver", 1), Duration.ofMinutes(15), CLAVE);
    }

    public static String deServicio(String clientId) {
        return firmar(clientId, Map.of("azp", clientId, "rol", "SERVICIO"), Duration.ofMinutes(15), CLAVE);
    }

    public static String deKeycloak(UUID sujeto, String preferredUsername, List<String> roles) {
        return firmar(sujeto.toString(),
                Map.of("preferred_username", preferredUsername, "realm_access", Map.of("roles", roles)),
                Duration.ofMinutes(15), CLAVE);
    }

    public static String caducado(String apodo, UUID uid) {
        return firmar(apodo, Map.of("uid", uid.toString(), "rol", "JUGADOR"), Duration.ofMinutes(-5), CLAVE);
    }

    public static String firmadoPorOtro(String apodo, UUID uid) {
        return firmar(apodo, Map.of("uid", uid.toString(), "rol", "JUGADOR"), Duration.ofMinutes(15), CLAVE_AJENA);
    }

    private static String firmar(String sujeto, Map<String, Object> claims, Duration vigencia, RSAKey clave) {
        Instant ahora = Instant.now();
        Instant caducidad = ahora.plus(vigencia);
        Instant emision = vigencia.isNegative() ? caducidad.minusSeconds(60) : ahora.minusSeconds(1);
        JWTClaimsSet.Builder cuerpo = new JWTClaimsSet.Builder()
                .subject(sujeto)
                .issuer("ms-identidad-de-prueba")
                .issueTime(Date.from(emision))
                .expirationTime(Date.from(caducidad));
        claims.forEach(cuerpo::claim);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(clave.getKeyID()).build(), cuerpo.build());
            jwt.sign(new RSASSASigner(clave));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
