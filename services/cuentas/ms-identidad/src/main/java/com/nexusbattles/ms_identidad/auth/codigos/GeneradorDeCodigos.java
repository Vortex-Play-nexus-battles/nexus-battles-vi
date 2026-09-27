package com.nexusbattles.ms_identidad.auth.codigos;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Genera los codigos que viajan al correo y normaliza los que teclea la
 * persona.
 *
 * <p><b>Alfabeto sin caracteres que se confunden al transcribir</b>: ni 0/O,
 * ni 1/I/L. 31 simbolos y 8 posiciones son unas 8,5·10^11 combinaciones
 * (casi 40 bits): con 5 intentos por codigo, acertar a ciegas es del orden de
 * uno entre cien mil millones. Cabe de sobra en el {@code maxLength: 12} de
 * {@code contracts/openapi/correo.yaml} y en el rango 6..16 de
 * {@code ms-identidad-auth.yaml}.
 *
 * <p>{@link SecureRandom}, nunca {@code Random}: un generador predecible
 * convierte el codigo en una formalidad.
 */
@Component
public class GeneradorDeCodigos {

    static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LONGITUD = 8;

    /**
     * Lo que se compara con BCrypt nunca pasa de aqui. BCrypt solo mira los
     * primeros 72 bytes y la version de Spring rechaza los mas largos con una
     * excepcion: un «codigo» de un kilobyte seria un 500 gratis. Un codigo
     * real mide 8; cualquier cosa mas larga ya es incorrecta.
     */
    static final int LONGITUD_MAXIMA_COMPARABLE = 32;

    private final SecureRandom azar;

    public GeneradorDeCodigos() {
        this(new SecureRandom());
    }

    GeneradorDeCodigos(SecureRandom azar) {
        this.azar = azar;
    }

    public String nuevo() {
        StringBuilder codigo = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    /**
     * Como lo escribe la persona -> como se genero: sin espacios ni guiones
     * (quien lo copia en dos bloques, «K7QX-2M9P») y en mayusculas.
     */
    public static String normalizar(String entrada) {
        if (entrada == null) {
            return "";
        }
        String limpio = entrada.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return limpio.length() > LONGITUD_MAXIMA_COMPARABLE ? limpio.substring(0, LONGITUD_MAXIMA_COMPARABLE) : limpio;
    }
}
