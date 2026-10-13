package com.nexusbattles.ms_identidad.auth.segundofactor;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Codigos de recuperacion del segundo factor — HU-AUT-007 CA-02.
 *
 * <p>Para entrar cuando la aplicacion de autenticacion no esta a mano (telefono
 * perdido, cambiado o sin bateria). Cada uno sirve una vez; se entregan al
 * activar y el servidor solo guarda su resumen BCrypt, igual que una
 * contrasena.
 *
 * <p><b>Forma</b>: 10 caracteres del mismo alfabeto que los codigos de correo
 * (sin 0/O ni 1/I/L, que se confunden al copiarlos a mano), en dos bloques de
 * cinco: {@code K7QX2-M9PRT}. 31^10 son unos 8·10^14 (casi 50 bits); con la
 * politica de bloqueo de la cuenta, acertarlo a ciegas no es una opcion. La
 * cantidad la fija {@code IDENTIDAD_2FA_CODIGOS_RECUPERACION} (10 por omision,
 * PROVISIONAL: ningun documento la da).
 *
 * <p>{@link SecureRandom}, nunca {@code Random}.
 */
@Component
public class CodigosDeRecuperacion {

    static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LONGITUD = 10;
    private static final int BLOQUE = 5;

    private final SecureRandom azar = new SecureRandom();

    /** {@code cantidad} codigos distintos, ya con el guion que los parte en dos bloques. */
    public List<String> generar(int cantidad) {
        if (cantidad < 1) {
            throw new IllegalArgumentException("Hay que generar al menos un codigo de recuperacion.");
        }
        Set<String> codigos = new LinkedHashSet<>();
        while (codigos.size() < cantidad) {
            StringBuilder codigo = new StringBuilder(LONGITUD + 1);
            for (int i = 0; i < LONGITUD; i++) {
                if (i == BLOQUE) {
                    codigo.append('-');
                }
                codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
            }
            codigos.add(codigo.toString());
        }
        return new ArrayList<>(codigos);
    }

    /**
     * Como lo escribe la persona -> como se resumio: sin espacios ni guiones y
     * en mayusculas. Se recorta a 32 caracteres: lo que se compara con BCrypt
     * nunca pasa de ahi (BCrypt solo mira 72 bytes y Spring rechaza los mas
     * largos con una excepcion).
     */
    public static String normalizar(String entrada) {
        if (entrada == null) {
            return "";
        }
        String limpio = entrada.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return limpio.length() > 32 ? limpio.substring(0, 32) : limpio;
    }

    /** Si un valor ya normalizado puede ser un codigo de recuperacion (si no, ni se compara). */
    public static boolean pareceUnCodigo(String normalizado) {
        if (normalizado == null || normalizado.length() != LONGITUD) {
            return false;
        }
        for (int i = 0; i < normalizado.length(); i++) {
            if (ALFABETO.indexOf(normalizado.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
