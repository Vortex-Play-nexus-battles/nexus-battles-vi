package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * El secreto TOTP, cifrado antes de tocar la base — HU-AUT-007.
 *
 * <p><b>Por que cifrado y no resumido.</b> Una contrasena se guarda con BCrypt
 * porque solo hace falta compararla. El secreto TOTP no se compara: se usa
 * como clave HMAC para calcular el codigo de cada momento, asi que el servidor
 * necesita recuperarlo. Lo minimo es que una copia de la base (un respaldo,
 * una consulta de diagnostico) no baste para generar los codigos de nadie: se
 * guarda cifrado con AES-GCM y la clave vive fuera de la base, en
 * {@code IDENTIDAD_2FA_CLAVE} (regla 10: ningun valor real en el repositorio).
 *
 * <p><b>Formato</b>: {@code v1:} + base64(IV de 12 bytes || cifrado || etiqueta
 * de 16 bytes). GCM autentica: un valor alterado, o descifrado con otra clave,
 * falla en vez de devolver basura. El contexto (la cuenta) entra como datos
 * asociados: un secreto copiado a la fila de otra cuenta tampoco se descifra.
 *
 * <p><b>Sin clave el servicio arranca igual.</b> El login de quien no tiene
 * segundo factor no la necesita; enrolarse responde 503
 * {@code segundo-factor-no-disponible}. Una clave que no es base64 o que no
 * mide 16, 24 o 32 bytes cuenta como ausente, y se dice en la bitacora sin
 * imprimirla.
 */
@Component
public class CifradoDeSecretos {

    private static final Logger log = LoggerFactory.getLogger(CifradoDeSecretos.class);

    static final String VERSION = "v1:";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKey clave;
    private final SecureRandom azar = new SecureRandom();

    public CifradoDeSecretos(@Value("${identidad.segundo-factor.clave:}") String claveEnBase64) {
        this.clave = leer(claveEnBase64);
    }

    private static SecretKey leer(String claveEnBase64) {
        if (claveEnBase64 == null || claveEnBase64.isBlank()) {
            log.warn("IDENTIDAD_2FA_CLAVE sin valor: el segundo factor no se puede activar en este entorno "
                    + "(el login de las cuentas sin segundo factor no cambia).");
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveEnBase64.strip());
        } catch (IllegalArgumentException noEsBase64) {
            log.error("IDENTIDAD_2FA_CLAVE no es base64: el segundo factor queda no disponible.");
            return null;
        }
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            log.error("IDENTIDAD_2FA_CLAVE mide {} bytes y AES necesita 16, 24 o 32: el segundo factor queda "
                    + "no disponible.", bytes.length);
            return null;
        }
        return new SecretKeySpec(bytes, "AES");
    }

    /** Si hay una clave utilizable: sin ella no se puede enrolar ni comprobar un codigo TOTP. */
    public boolean disponible() {
        return clave != null;
    }

    /**
     * @param claro    el secreto TOTP
     * @param contexto a quien pertenece (entra como datos asociados de GCM)
     */
    public String cifrar(byte[] claro, String contexto) {
        exigirClave();
        try {
            byte[] iv = new byte[BYTES_IV];
            azar.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            cifrador.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cifrador.doFinal(claro);
            return VERSION + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException imposible) {
            throw new IllegalStateException("AES-GCM no esta disponible en esta JVM.", imposible);
        }
    }

    /**
     * @throws SegundoFactorRechazadoException {@code NO_DISPONIBLE} si no hay
     *         clave, o si el valor guardado no se puede leer con ella (otra
     *         clave, alterado, de otra cuenta)
     */
    public byte[] descifrar(String guardado, String contexto) {
        exigirClave();
        try {
            if (guardado == null || !guardado.startsWith(VERSION)) {
                throw new GeneralSecurityException("Formato desconocido.");
            }
            byte[] todo = Base64.getDecoder().decode(guardado.substring(VERSION.length()));
            if (todo.length <= BYTES_IV) {
                throw new GeneralSecurityException("Demasiado corto.");
            }
            Cipher descifrador = Cipher.getInstance(TRANSFORMACION);
            descifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_IV));
            descifrador.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            return descifrador.doFinal(todo, BYTES_IV, todo.length - BYTES_IV);
        } catch (GeneralSecurityException | IllegalArgumentException ilegible) {
            // Ni el valor ni la clave van a la bitacora: solo de quien es y que
            // no se pudo. El contexto es la cuenta («usuario:<id>»).
            log.error("No se pudo descifrar el segundo factor de {} (¿cambio IDENTIDAD_2FA_CLAVE?).", contexto);
            throw new SegundoFactorRechazadoException(Motivo.NO_DISPONIBLE,
                    "No pudimos comprobar el código de tu aplicación ahora mismo. Usa un código de recuperación "
                            + "o inténtalo más tarde.");
        }
    }

    private void exigirClave() {
        if (clave == null) {
            throw new SegundoFactorRechazadoException(Motivo.NO_DISPONIBLE,
                    "La verificación en dos pasos no está disponible en este momento. Inténtalo más tarde.");
        }
    }
}
