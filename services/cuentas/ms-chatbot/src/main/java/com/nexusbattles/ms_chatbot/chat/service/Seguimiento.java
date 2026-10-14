package com.nexusbattles.ms_chatbot.chat.service;

import com.nexusbattles.ms_chatbot.chat.texto.DistanciaDeEdicion;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 1.5.0 — gestión de diálogo por reglas (7.4.2 «reconocer la intención y el
 * contexto», 7.4.5 «recordar el contexto de consultas anteriores»; 7.4.9
 * recomienda un «sistema de gestión de diálogos»).
 *
 * <p>Una frase corta que no pregunta nada nuevo, sino que se refiere a la
 * respuesta anterior: «más detalle», «en inglés», «repítelo», «no entendí».
 * {@code ChatService} la resuelve con la última respuesta del bot en esa misma
 * conversación, sin volver a buscar en la base.
 *
 * <p>Solo cuenta si el mensaje es esa frase (con un error de tecleo como
 * mucho por palabra larga) rodeada, como mucho, de palabras de relleno («por
 * favor», «eso», «please», «can you»). «Explícame mejor cómo se publica una
 * subasta», «quiero jugar de nuevo» o «amplía el inventario» son preguntas
 * nuevas, no seguimientos, y siguen el camino normal.
 */
enum Seguimiento {

    /** Responde el tema anterior con todo el detalle. */
    MAS_DETALLE,
    /** Responde el tema anterior en inglés. */
    EN_INGLES,
    /** Responde el tema anterior en español. */
    EN_ESPANOL,
    /** Responde otra vez el tema anterior, igual. */
    REPETIR,
    /** La respuesta no le sirvió: se ofrece soporte humano y temas relacionados. */
    NO_SIRVIO;

    private static final Map<Seguimiento, List<String>> FRASES = Map.of(
        MAS_DETALLE, List.of("mas detalle", "mas detalles", "mas detallado", "con mas detalle", "en detalle",
            "explicame mejor", "explica mejor", "explicamelo mejor", "explicalo mejor", "explicame mas",
            "dime mas", "cuentame mas", "amplialo", "more detail", "more details", "in detail", "tell me more",
            "explain more", "explain better", "explain it better", "more info", "elaborate"),
        EN_INGLES, List.of("en ingles", "ingles", "in english", "english"),
        EN_ESPANOL, List.of("en espanol", "espanol", "en castellano", "castellano", "in spanish", "spanish"),
        REPETIR, List.of("repite", "repitelo", "repitemelo", "repetir", "otra vez", "de nuevo", "repeat",
            "repeat that", "say that again", "again", "one more time"),
        NO_SIRVIO, List.of("no entendi", "no entiendo", "no me sirvio", "no me sirve", "no sirvio", "no sirve",
            "eso no", "no es eso", "no era eso", "no me ayudo", "no me ayuda", "no es lo que pregunte",
            "didnt help", "doesnt help", "not helpful", "i dont understand", "dont understand", "thats not it",
            "not what i asked", "useless")
    );

    // Lo unico que puede acompanar a la frase, antes o despues de ella: cortesia
    // y verbos de pedir, sin tema. Con una palabra de tema ya es otra pregunta.
    private static final Set<String> RELLENO = Set.of(
        "por", "favor", "porfa", "porfavor", "plis", "eso", "esto", "lo", "la", "tu", "respuesta", "anterior",
        "nada", "bien", "pues", "ok", "vale", "bueno", "oye", "ahora", "y", "si", "pero", "todo", "gracias",
        "puedes", "podrias", "responde", "respondeme", "responder", "contesta", "contestame", "dime", "dimelo",
        "dilo", "escribe", "escribelo", "hmm", "mmm",
        "please", "pls", "plz", "can", "could", "you", "that", "it", "this", "your", "answer", "the", "previous",
        "last", "okay", "so", "now", "say", "respond", "reply", "write", "just", "oh", "thanks", "but", "all",
        "me", "to", "about");

    // El normalizador cambia el apostrofo por un espacio («don't» -> «don t»):
    // se vuelven a juntar las contracciones inglesas para reconocer las frases.
    private static final Pattern NEGACION = Pattern.compile(
        "\\b(don|doesn|didn|isn|wasn|aren|weren|can|couldn|won|wouldn|shouldn|haven|hasn) t\\b");
    private static final Pattern ES_CONTRAIDO = Pattern.compile("\\b(that|it|what|there|here) s\\b");
    private static final Pattern I_AM = Pattern.compile("\\bi m\\b");

    /** El seguimiento que es este mensaje, si lo es. */
    static Optional<Seguimiento> de(String mensaje) {
        String normalizado = juntarContracciones(NormalizadorTexto.normalizar(mensaje));
        if (normalizado.isBlank()) {
            return Optional.empty();
        }
        List<String> palabras = List.of(normalizado.split(" "));
        for (Seguimiento seguimiento : values()) {
            for (String frase : FRASES.get(seguimiento)) {
                if (esLaFraseConRelleno(palabras, List.of(frase.split(" ")))) {
                    return Optional.of(seguimiento);
                }
            }
        }
        return Optional.empty();
    }

    private static String juntarContracciones(String normalizado) {
        String junto = NEGACION.matcher(normalizado).replaceAll("$1t");
        junto = ES_CONTRAIDO.matcher(junto).replaceAll("$1s");
        return I_AM.matcher(junto).replaceAll("im");
    }

    // La frase aparece como palabras seguidas (cada una igual o a un error de
    // tecleo) y todo lo demas del mensaje es relleno.
    private static boolean esLaFraseConRelleno(List<String> palabras, List<String> frase) {
        for (int inicio = 0; inicio + frase.size() <= palabras.size(); inicio++) {
            if (coincideEn(palabras, frase, inicio) && soloRellenoFuera(palabras, inicio, inicio + frase.size())) {
                return true;
            }
        }
        return false;
    }

    private static boolean coincideEn(List<String> palabras, List<String> frase, int inicio) {
        for (int k = 0; k < frase.size(); k++) {
            if (!DistanciaDeEdicion.coinciden(frase.get(k), palabras.get(inicio + k))) {
                return false;
            }
        }
        return true;
    }

    private static boolean soloRellenoFuera(List<String> palabras, int desde, int hasta) {
        for (int i = 0; i < palabras.size(); i++) {
            if ((i < desde || i >= hasta) && !RELLENO.contains(palabras.get(i))) {
                return false;
            }
        }
        return true;
    }
}
