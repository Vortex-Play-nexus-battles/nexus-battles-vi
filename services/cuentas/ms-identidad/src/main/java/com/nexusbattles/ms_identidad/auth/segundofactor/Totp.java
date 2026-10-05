package com.nexusbattles.ms_identidad.auth.segundofactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * TOTP de la RFC 6238 — HU-AUT-007 (RF-AUT-007).
 *
 * <p>El documento no fija el mecanismo del segundo factor; se usa el que
 * entiende cualquier aplicacion de autenticacion sin configurar nada: HMAC-SHA1,
 * 6 digitos, pasos de 30 s contados desde la epoca Unix (T0 = 0). Son los
 * valores por omision de la URI {@code otpauth://} y no son ajustables: una
 * aplicacion que no los lea los supondria igual.
 *
 * <p><b>Ventana de ±1 paso.</b> El reloj del telefono y el del servidor no
 * coinciden al segundo, y quien teclea el codigo tarda; se acepta el del paso
 * actual y el de sus dos vecinos (90 s en total), que es lo que recomienda la
 * RFC (§5.2) y no mas.
 *
 * <p><b>Sin repeticion.</b> Quien llama pasa el ultimo paso aceptado para esa
 * cuenta y solo se aceptan pasos POSTERIORES: un codigo ya usado —o uno
 * anterior a el— no vuelve a valer aunque siga dentro de su ventana (RFC 6238
 * §5.2: «the verifier MUST NOT accept the second attempt of the OTP after the
 * successful validation has been issued for the first OTP»).
 */
public final class Totp {

    public static final String ALGORITMO = "SHA1";
    public static final int DIGITOS = 6;
    public static final int PERIODO_SEGUNDOS = 30;
    /** Pasos de holgura a cada lado del actual. */
    public static final int VENTANA = 1;

    private static final int[] POTENCIAS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000,
        100_000_000};

    private Totp() {
    }

    /** El paso de tiempo (contador T de la RFC) al que pertenece un instante. */
    public static long pasoDe(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), PERIODO_SEGUNDOS);
    }

    /** El codigo de 6 digitos de un paso. */
    public static String codigo(byte[] secreto, long paso) {
        return codigo(secreto, paso, DIGITOS);
    }

    /**
     * El codigo de un paso con los digitos pedidos (6 en produccion; 8 para
     * los vectores de la RFC).
     */
    public static String codigo(byte[] secreto, long paso, int digitos) {
        if (secreto == null || secreto.length == 0) {
            throw new IllegalArgumentException("Un secreto TOTP no puede estar vacio.");
        }
        if (digitos < 1 || digitos >= POTENCIAS.length) {
            throw new IllegalArgumentException("Digitos fuera de rango: " + digitos);
        }
        byte[] resumen = hmacSha1(secreto, ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
        // Truncado dinamico (RFC 4226 §5.3).
        int desplazamiento = resumen[resumen.length - 1] & 0x0F;
        int binario = ((resumen[desplazamiento] & 0x7F) << 24)
                | ((resumen[desplazamiento + 1] & 0xFF) << 16)
                | ((resumen[desplazamiento + 2] & 0xFF) << 8)
                | (resumen[desplazamiento + 3] & 0xFF);
        StringBuilder texto = new StringBuilder(Integer.toString(binario % POTENCIAS[digitos]));
        while (texto.length() < digitos) {
            texto.insert(0, '0');
        }
        return texto.toString();
    }

    /**
     * Busca el codigo en el paso actual y sus vecinos.
     *
     * @param secreto el secreto TOTP ya descifrado
     * @param codigo  lo que escribio la persona (se toleran espacios)
     * @param ahora   el instante de la comprobacion
     * @param ultimoPasoUsado el ultimo paso aceptado para esta cuenta, o
     *        {@code null} si no hay ninguno: solo se aceptan pasos posteriores
     * @return el paso al que corresponde el codigo, o vacio si no vale
     */
    public static OptionalLong verificar(byte[] secreto, String codigo, Instant ahora, Long ultimoPasoUsado) {
        String limpio = codigo == null ? "" : codigo.replaceAll("\\s", "");
        if (!limpio.matches("[0-9]{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        byte[] tecleado = limpio.getBytes(StandardCharsets.US_ASCII);
        long actual = pasoDe(ahora);
        // Se comparan los tres pasos aunque uno ya haya coincidido, y en tiempo
        // constante: que el tiempo de respuesta no diga cual de ellos era.
        long aceptado = Long.MIN_VALUE;
        for (long paso = actual - VENTANA; paso <= actual + VENTANA; paso++) {
            boolean posterior = ultimoPasoUsado == null || paso > ultimoPasoUsado;
            byte[] esperado = codigo(secreto, paso).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(esperado, tecleado) && posterior && aceptado == Long.MIN_VALUE) {
                aceptado = paso;
            }
        }
        return aceptado == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(aceptado);
    }

    private static byte[] hmacSha1(byte[] clave, byte[] mensaje) {
        try {
            // HMAC-SHA1 es el algoritmo de TOTP (RFC 6238 §1.2 sobre HOTP, RFC 4226
            // §5.3) y el único que leen de forma fiable las aplicaciones de
            // autenticación (la URI otpauth no garantiza que respeten otro). En un
            // HMAC no aplica la debilidad de SHA-1 ante colisiones (RFC 6194 §3.2):
            // es un uso aceptado, no un resumen de contraseñas ni una firma.
            Mac mac = Mac.getInstance("HmacSHA1"); // NOSONAR java:S4790 — TOTP RFC 6238, ver arriba

            mac.init(new SecretKeySpec(clave, "RAW"));
            return mac.doFinal(mensaje);
        } catch (GeneralSecurityException imposible) {
            // HmacSHA1 es obligatorio en toda JVM (Java SE, «Standard Algorithm Names»).
            throw new IllegalStateException("La JVM no ofrece HmacSHA1.", imposible);
        }
    }
}
