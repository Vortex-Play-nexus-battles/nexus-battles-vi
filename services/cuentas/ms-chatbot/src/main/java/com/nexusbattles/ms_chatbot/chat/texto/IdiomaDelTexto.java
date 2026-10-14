package com.nexusbattles.ms_chatbot.chat.texto;

import java.util.Set;

/**
 * 7.4.2 «comprender consultas en español/english»: en qué idioma escribió el
 * usuario, para responderle en el mismo cuando su preferencia es
 * AUTOMÁTICO (ms-chatbot.yaml 1.3.6).
 *
 * <p>Cuenta las palabras gramaticales de cada idioma en el texto normalizado
 * (sin tildes ni mayúsculas). Gana el inglés solo si tiene más: ante la duda,
 * español, que es el idioma de la interfaz. Los nombres propios del juego
 * («Espada de una mano») no deciden nada por sí solos: «what does Espada de
 * una mano do» sigue siendo inglés.
 *
 * <p>Sin estado; trabaja sobre lo que devuelve {@link NormalizadorTexto}.
 */
public final class IdiomaDelTexto {

    private static final Set<String> INGLES = Set.of(
        "the", "my", "is", "are", "was", "were", "what", "how", "where", "when", "which", "who", "whom", "why",
        "can", "could", "do", "does", "did", "i", "you", "your", "show", "tell", "give", "have", "has", "had",
        "get", "there", "please", "help", "about", "with", "this", "that", "it", "of", "to", "in", "on", "for",
        "and", "or", "not", "should", "would", "will", "am", "an", "be", "been", "want", "need", "know", "much",
        "many", "any", "some", "from", "at", "by", "if", "hi", "hello", "thanks", "thank", "bye", "yes", "next",
        "mine", "our", "their", "here", "now", "more", "explain", "dont", "cant", "im", "ive", "whats", "hows");

    private static final Set<String> ESPANOL = Set.of(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "que", "como", "donde", "cuando",
        "cual", "cuales", "quien", "por", "para", "mi", "mis", "tu", "tus", "su", "sus", "es", "son", "esta",
        "estan", "estoy", "tengo", "tienes", "tiene", "puedo", "puedes", "quiero", "hay", "y", "o", "se", "lo",
        "le", "con", "sin", "muestrame", "dame", "dime", "ayuda", "ayudame", "hola", "gracias", "porque", "pero",
        "mas", "muy", "al", "en", "yo", "me", "te", "nos", "si", "ya", "eso", "esto", "cuanto", "cuantos",
        "cuantas", "necesito", "hacer", "hago", "explicame", "chao", "adios", "buenas", "buenos", "otra", "otro");

    private IdiomaDelTexto() {
    }

    /** {@code true} si el texto normalizado está escrito en inglés. */
    public static boolean esIngles(String normalizado) {
        if (normalizado == null || normalizado.isBlank()) {
            return false;
        }
        int ingles = 0;
        int espanol = 0;
        for (String palabra : normalizado.split(" ")) {
            if (INGLES.contains(palabra)) {
                ingles++;
            }
            if (ESPANOL.contains(palabra)) {
                espanol++;
            }
        }
        return ingles > espanol;
    }
}
