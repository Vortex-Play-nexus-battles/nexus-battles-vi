package com.nexusbattles.ms_identidad.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Par de claves con el que este servicio firma los tokens de acceso.
 *
 * <h2>Por que RSA y no un secreto compartido</h2>
 *
 * <p>Los tokens que emite ms-identidad ya no los consume solo ms-identidad:
 * salas-partidas, moderacion-sanciones y el resto de la plataforma los
 * verifican para aplicar sus reglas por rol. Con la firma HMAC anterior, cada
 * servicio que quisiera verificar un token necesitaba la misma clave secreta,
 * y esa clave tambien sirve para <b>emitir</b>: repartirla habria dado a ocho
 * servicios la capacidad de fabricar tokens de cualquier usuario.
 *
 * <p>Con RSA los demas solo reciben la clave <b>publica</b>, por el JWKS que
 * publica {@link com.nexusbattles.ms_identidad.auth.controller.JwksController}.
 * Pueden verificar; no pueden firmar. Es el mismo modelo que usa Keycloak, asi
 * que cuando la identidad se mueva alli bastara con cambiar la URL del JWKS en
 * los servicios que lo consumen. Ver
 * {@code docs/gobierno/ADR-002-identidad-de-usuario.md}.
 *
 * <h2>De donde sale la clave</h2>
 *
 * <p>De {@code app.jwt.clave-privada} (PKCS#8 en base64) cuando esta definida:
 * ese es el modo de cualquier despliegue con mas de una instancia o que deba
 * sobrevivir a un reinicio sin invalidar las sesiones abiertas.
 *
 * <p>Si no esta definida se genera un par al arrancar y se avisa por bitacora.
 * Es comodo en local y en el host de desarrollo de una sola instancia, con una
 * consecuencia explicita: al reiniciar el servicio, los tokens emitidos antes
 * dejan de validar y hay que iniciar sesion otra vez.
 *
 * <p>Regla 10: aqui no se versiona ningun valor real.
 */
@Component
public class ClavesDeFirma {

    private static final Logger BITACORA = LoggerFactory.getLogger(ClavesDeFirma.class);
    private static final int TAMANO_RSA = 2048;

    private final PrivateKey privada;
    private final RSAPublicKey publica;
    private final String identificador;

    public ClavesDeFirma(@Value("${app.jwt.clave-privada:}") String clavePrivadaBase64) {
        KeyPair par = clavePrivadaBase64 == null || clavePrivadaBase64.isBlank()
                ? generarEfimero()
                : leerDeConfiguracion(clavePrivadaBase64);
        this.privada = par.getPrivate();
        this.publica = (RSAPublicKey) par.getPublic();
        this.identificador = huellaDe(this.publica);
    }

    public PrivateKey privada() {
        return privada;
    }

    public RSAPublicKey publica() {
        return publica;
    }

    /**
     * Identificador de la clave ({@code kid}). Va en la cabecera de cada token
     * y en el JWKS, para que un verificador sepa cual de las claves publicadas
     * le toca usar cuando haya rotacion.
     */
    public String identificador() {
        return identificador;
    }

    private static KeyPair generarEfimero() {
        BITACORA.warn("app.jwt.clave-privada no esta definida: se genera un par RSA efimero. "
                + "Los tokens emitidos dejaran de ser validos cuando este servicio se reinicie, "
                + "y una segunda instancia no podria verificar los tokens de la primera. "
                + "Define la variable para un despliegue estable.");
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(TAMANO_RSA);
            return generador.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La maquina virtual de Java no ofrece RSA", e);
        }
    }

    private static KeyPair leerDeConfiguracion(String clavePrivadaBase64) {
        try {
            byte[] pkcs8 = Base64.getDecoder().decode(limpiar(clavePrivadaBase64));
            KeyFactory fabrica = KeyFactory.getInstance("RSA");
            PrivateKey privada = fabrica.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            return new KeyPair(publicaDesde(privada, fabrica), privada);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "app.jwt.clave-privada no es una clave RSA PKCS#8 valida en base64. "
                            + "Generala con: openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 "
                            + "| openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0", e);
        }
    }

    private static java.security.PublicKey publicaDesde(PrivateKey privada, KeyFactory fabrica)
            throws InvalidKeySpecException {
        java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) privada;
        return fabrica.generatePublic(
                new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private static String limpiar(String pem) {
        return pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    /** Huella estable de la clave publica, para usarla como {@code kid}. */
    private static String huellaDe(RSAPublicKey publica) {
        try {
            byte[] resumen = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(publica.getModulus().toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La maquina virtual de Java no ofrece SHA-256", e);
        }
    }
}
