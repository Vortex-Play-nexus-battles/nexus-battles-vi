package com.nexusbattles.comun.seguridad.pruebas;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emisor de tokens de acceso <b>reales</b> para las pruebas de los servicios.
 *
 * <p>Firma con una clave RSA generada al cargar la clase y publica la clave
 * publica en un JWKS servido por HTTP en un puerto libre, exactamente como
 * hace ms-identidad en {@code GET /api/v1/auth/jwks} (ADR-002). Asi una prueba
 * puede apuntar el {@code jwk-set-uri} del servicio a este emisor y ejercitar
 * la cadena de seguridad de verdad: firma verificada, caducidad comprobada,
 * roles traducidos por {@code ConversorRolesJwt}, {@code uid} leido por
 * {@code IdentidadDelToken}.
 *
 * <p>Lo que <b>no</b> se prueba con esto es la forma en que ms-identidad
 * fabrica sus tokens: eso lo prueba ms-identidad. Aqui se reproduce su forma
 * (sujeto = apodo, {@code uid}, {@code rol}, {@code ver}, {@code kid}) para
 * que lo que un servicio acepte en pruebas sea lo que aceptara en produccion.
 *
 * <p>Un {@code JwtDecoder} falso que acepta cualquier cadena (el patron
 * anterior de varias pruebas) demuestra que las rutas estan protegidas, pero
 * no que el servicio distinga un token legitimo de uno manipulado, caducado o
 * firmado por otro. Este emisor ofrece los tres casos negativos a proposito.
 *
 * <h2>Uso</h2>
 *
 * <pre>{@code
 * @WebMvcTest(MiController.class)
 * @Import({SecurityConfig.class, DecodificadorDePrueba.class})
 * class MiControllerTest {
 *     String token = EmisorDeTokensDePrueba.emisor().tokenDeJugador("lyra", UUID.randomUUID());
 *     mockMvc.perform(post("/api/v1/...").header("Authorization", "Bearer " + token))...
 * }
 * }</pre>
 *
 * <p>O, en un {@code @SpringBootTest}, sin importar nada:
 * {@code @DynamicPropertySource static void jwks(DynamicPropertyRegistry r) { EmisorDeTokensDePrueba.registrarJwks(r); }}.
 */
public final class EmisorDeTokensDePrueba {

    /** Emisor ({@code iss}) que llevan los tokens. Distinto del real a proposito. */
    public static final String EMISOR = "ms-identidad-de-prueba";

    /** Rol con el que ms-identidad marca un token de servicio (ADR-005). */
    public static final String ROL_SERVICIO = "SERVICIO";

    private static final EmisorDeTokensDePrueba INSTANCIA = new EmisorDeTokensDePrueba();

    private final RSAKey clave;
    private final RSAKey claveAjena;
    private final HttpServer servidorJwks;

    private EmisorDeTokensDePrueba() {
        try {
            this.clave = new RSAKeyGenerator(2048)
                    .keyID("prueba-" + UUID.randomUUID().toString().substring(0, 8))
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            this.claveAjena = new RSAKeyGenerator(2048).keyID("ajena").generate();
            this.servidorJwks = levantarJwks(new JWKSet(clave.toPublicJWK()).toString());
        } catch (JOSEException | IOException e) {
            throw new IllegalStateException("No se pudo levantar el emisor de tokens de prueba", e);
        }
    }

    /** El emisor compartido por todas las pruebas de la maquina virtual. */
    public static EmisorDeTokensDePrueba emisor() {
        return INSTANCIA;
    }

    /**
     * Apunta el {@code jwk-set-uri} del servicio a este emisor. Para
     * {@code @DynamicPropertySource}.
     */
    public static void registrarJwks(DynamicPropertyRegistry registro) {
        registro.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", INSTANCIA::urlDelJwks);
    }

    /** URL del JWKS, con la forma que consume Spring Security. */
    public String urlDelJwks() {
        return "http://127.0.0.1:" + servidorJwks.getAddress().getPort() + "/api/v1/auth/jwks";
    }

    /** Identificador de la clave ({@code kid}) con la que se firma. */
    public String kid() {
        return clave.getKeyID();
    }

    /**
     * Decodificador real contra el JWKS de este emisor: el mismo tipo que
     * Boot construye en produccion a partir de {@code jwk-set-uri}.
     */
    public JwtDecoder decodificador() {
        return NimbusJwtDecoder.withJwkSetUri(urlDelJwks()).build();
    }

    // ------------------------------------------------------------------
    // Tokens con la forma de ms-identidad (ADR-002)
    // ------------------------------------------------------------------

    /** Token de un jugador: sujeto = apodo, {@code uid}, {@code rol=JUGADOR}. */
    public String tokenDeJugador(String apodo, UUID uid) {
        return tokenDeUsuario(apodo, uid, "JUGADOR");
    }

    /** Token de usuario con el rol dado (JUGADOR, MODERADOR, ADMINISTRADOR, SUPER_ADMINISTRADOR). */
    public String tokenDeUsuario(String apodo, UUID uid, String rol) {
        return token().sujeto(apodo).uid(uid).rol(rol).firmar();
    }

    /**
     * Token de servicio (ADR-001/ADR-005): sujeto y {@code azp} = client_id,
     * {@code rol=SERVICIO}, sin {@code uid} porque no habla ningun usuario.
     */
    public String tokenDeServicio(String clientId) {
        return token().sujeto(clientId).azp(clientId).rol(ROL_SERVICIO).firmar();
    }

    /** Token con la forma de Keycloak: sujeto = id estable, roles en {@code realm_access}. */
    public String tokenDeKeycloak(UUID sujeto, String preferredUsername, List<String> roles) {
        return token()
                .sujeto(sujeto.toString())
                .claim("preferred_username", preferredUsername)
                .claim("realm_access", Map.of("roles", roles))
                .firmar();
    }

    /** Token de jugador ya caducado: la cadena debe responder 401. */
    public String tokenCaducado(String apodo, UUID uid) {
        return token().sujeto(apodo).uid(uid).rol("JUGADOR").caducaEn(Duration.ofMinutes(-5)).firmar();
    }

    /**
     * Token bien formado pero firmado con una clave que <b>no</b> esta en el
     * JWKS: la cadena debe responder 401. Es el caso de un token fabricado.
     */
    public String tokenFirmadoPorOtro(String apodo, UUID uid) {
        return token().sujeto(apodo).uid(uid).rol("JUGADOR").firmarCon(claveAjena);
    }

    /** Constructor de tokens a medida. */
    public Token token() {
        return new Token();
    }

    /** Un token en construccion; {@link #firmar()} lo cierra. */
    public final class Token {
        private final Map<String, Object> claims = new LinkedHashMap<>();
        private String sujeto = "jugador";
        private Duration vigencia = Duration.ofMinutes(15);

        private Token() {
            claims.put("ver", 1);
        }

        public Token sujeto(String sujeto) {
            this.sujeto = sujeto;
            return this;
        }

        public Token uid(UUID uid) {
            claims.put("uid", uid.toString());
            return this;
        }

        public Token rol(String rol) {
            claims.put("rol", rol);
            return this;
        }

        public Token azp(String clientId) {
            claims.put("azp", clientId);
            return this;
        }

        public Token claim(String nombre, Object valor) {
            claims.put(nombre, valor);
            return this;
        }

        /** Negativa para emitir un token ya caducado. */
        public Token caducaEn(Duration vigencia) {
            this.vigencia = vigencia;
            return this;
        }

        public String firmar() {
            return firmarCon(clave);
        }

        private String firmarCon(RSAKey conClave) {
            Instant ahora = Instant.now();
            Instant caducidad = ahora.plus(vigencia);
            // Un token caducado tambien tiene que haberse emitido antes de caducar.
            Instant emision = vigencia.isNegative() ? caducidad.minusSeconds(60) : ahora.minusSeconds(1);
            JWTClaimsSet.Builder cuerpo = new JWTClaimsSet.Builder()
                    .subject(sujeto)
                    .issuer(EMISOR)
                    .issueTime(Date.from(emision))
                    .expirationTime(Date.from(caducidad));
            claims.forEach(cuerpo::claim);
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(conClave.getKeyID()).build(),
                        cuerpo.build());
                jwt.sign(new RSASSASigner(conClave));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException("No se pudo firmar el token de prueba", e);
            }
        }
    }

    private static HttpServer levantarJwks(String jwks) throws IOException {
        byte[] cuerpo = jwks.getBytes(StandardCharsets.UTF_8);
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/auth/jwks", intercambio -> {
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
        return servidor;
    }
}
