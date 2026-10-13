package com.nexusbattles.ms_identidad.auth.segundofactor;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Base32 de la RFC 4648 (§6): el formato en el que las aplicaciones de
 * autenticacion (Google Authenticator, Microsoft Authenticator, FreeOTP,
 * Aegis...) esperan el secreto TOTP cuando se escribe a mano o viaja en la URI
 * {@code otpauth://}.
 *
 * <p>Escrito aqui y no tomado de una biblioteca: son cuarenta lineas, el
 * servicio no tiene commons-codec entre sus dependencias y anadir una solo
 * para esto no compensa. Lo cubren los vectores de la propia RFC
 * ({@code Base32Test}).
 *
 * <p>Codifica sin relleno ({@code =}), que es como lo publican todas las
 * aplicaciones; decodifica con o sin relleno, en mayusculas o minusculas y
 * con espacios, que es como lo teclea una persona.
 */
public final class Base32 {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    public static String codificar(byte[] datos) {
        StringBuilder salida = new StringBuilder((datos.length * 8 + 4) / 5);
        int acumulado = 0;
        int bits = 0;
        for (byte b : datos) {
            acumulado = (acumulado << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                salida.append(ALFABETO.charAt((acumulado >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(ALFABETO.charAt((acumulado << (5 - bits)) & 0x1F));
        }
        return salida.toString();
    }

    /**
     * @throws IllegalArgumentException si trae un caracter que no es del alfabeto
     */
    public static byte[] decodificar(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("No hay nada que decodificar.");
        }
        String limpio = texto.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream salida = new ByteArrayOutputStream(limpio.length() * 5 / 8);
        int acumulado = 0;
        int bits = 0;
        for (int i = 0; i < limpio.length(); i++) {
            int valor = ALFABETO.indexOf(limpio.charAt(i));
            if (valor < 0) {
                throw new IllegalArgumentException("Caracter fuera del alfabeto Base32 en la posicion " + i + ".");
            }
            acumulado = (acumulado << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.write((acumulado >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return salida.toByteArray();
    }
}
