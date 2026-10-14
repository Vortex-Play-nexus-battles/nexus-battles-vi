package com.nexusbattles.ms_chatbot.chat.texto;

import java.util.List;

/**
 * 7.4.2 «manejar errores ortográficos y gramaticales comunes».
 *
 * <p>Distancia de edición con transposición de letras vecinas (Damerau, en su
 * forma de «alineación óptima»): «subatsas» está a 1 de «subastas», igual que
 * «subsatas» o «subastsa». Con la distancia de Levenshtein clásica una
 * transposición cuesta 2, y es uno de los errores de tecleo más comunes.
 *
 * <p>La tolerancia es la misma en todo el chatbot: dos palabras «coinciden» si
 * son iguales, o si la de referencia tiene al menos {@value #LONGITUD_TOLERADA}
 * letras y están a una edición. Las palabras cortas solo coinciden exactas:
 * entre «mis» y «mas» no hay error, hay otra palabra.
 */
public final class DistanciaDeEdicion {

    public static final int LONGITUD_TOLERADA = 5;

    private DistanciaDeEdicion() {
    }

    /** Ediciones (insertar, borrar, cambiar o intercambiar dos vecinas) entre {@code a} y {@code b}. */
    public static int entre(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + costo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    /** {@code referencia} y {@code escrita} son la misma palabra, con un error de tecleo como mucho. */
    public static boolean coinciden(String referencia, String escrita) {
        if (referencia.isEmpty() || escrita.isEmpty()) {
            return false;
        }
        if (referencia.equals(escrita)) {
            return true;
        }
        return referencia.length() >= LONGITUD_TOLERADA
            && Math.abs(referencia.length() - escrita.length()) <= 1
            && entre(referencia, escrita) <= 1;
    }

    /**
     * La frase (normalizada) aparece en el mensaje (normalizado) como una
     * secuencia de palabras seguidas, cada una igual o con un error de tecleo.
     */
    public static boolean contieneFrase(String mensajeNormalizado, String fraseNormalizada) {
        if (mensajeNormalizado.contains(fraseNormalizada)) {
            return true;
        }
        List<String> mensaje = List.of(mensajeNormalizado.split(" "));
        List<String> frase = List.of(fraseNormalizada.split(" "));
        for (int inicio = 0; inicio + frase.size() <= mensaje.size(); inicio++) {
            boolean todas = true;
            for (int k = 0; k < frase.size() && todas; k++) {
                todas = coinciden(frase.get(k), mensaje.get(inicio + k));
            }
            if (todas) {
                return true;
            }
        }
        return false;
    }
}
