package com.nexusbattles.ms_chatbot.chat.consultas;

import java.util.List;
import java.util.Set;

// Revision de plataforma (7.4.4): antes de consultar el inventario en vivo se
// clasifica QUE pide el jugador, en vez de excluir frases sueltas:
//
//   * orientacion: un consejo, como hacer algo o que pasa si... ("dame
//     consejos para organizar mi inventario", "que objetos me recomiendas
//     usar", "como equipo a mi heroe", "pierdo mis objetos") -> sigue a la
//     base de conocimiento;
//   * sus propios datos ("que tengo en mi inventario", "muestrame mis
//     heroes", "cuantos heroes tengo") -> se consulta el servicio real.
//
// Trabaja sobre el texto ya normalizado (sin tildes ni signos, en minusculas),
// igual que el resto del motor. Sin estado.
final class IntencionDeConsulta {

    // Raices que piden orientacion: consejo o recomendacion, y las preguntas
    // de reglas sobre perder cosas ("pierdo mis objetos", "lose my gear").
    private static final List<String> RAICES_DE_ORIENTACION = List.of(
        "consejo", "recomiend", "recomend", "organiz", "ordenar", "sugier", "sugerenci", "conviene",
        "tip", "advice", "recommend", "suggest",
        "pierd", "perd", "lose", "lost");

    // "como" seguido de uno de estos pide como hacer algo.
    private static final Set<String> TRAS_COMO_PIDE_COMO_HACER = Set.of(
        "puedo", "debo", "hago", "se", "podria", "deberia");

    // "how" seguido de uno de estos pide como hacer algo.
    private static final Set<String> TRAS_HOW_PIDE_COMO_HACER = Set.of("to", "do", "can", "should");

    // Palabras que dicen que el jugador pregunta por lo SUYO.
    private static final Set<String> PIDE_LO_SUYO = Set.of(
        "mi", "mis", "tengo", "muestrame", "ensename", "cuantos", "cuantas", "my", "mine");

    // De que trata el inventario. "heroes" y "personajes" en plural: "mi
    // heroe" suele ser una pregunta de reglas ("como subo de nivel a mi
    // heroe"), no una consulta de lo que se tiene.
    private static final Set<String> TEMA_INVENTARIO = Set.of(
        "inventario", "inventory", "heroes", "personajes", "armas", "items", "objetos", "equipo",
        "equipamiento", "gear", "equipment");

    private IntencionDeConsulta() {
    }

    // Habla del inventario, pregunta por lo suyo y no pide orientacion.
    static boolean consultaSuInventario(String normalizado) {
        return hablaDeInventario(normalizado) && pideSusDatos(normalizado) && !pideOrientacion(normalizado);
    }

    static boolean pideOrientacion(String normalizado) {
        List<String> palabras = palabras(normalizado);
        for (int i = 0; i < palabras.size(); i++) {
            String palabra = palabras.get(i);
            if (RAICES_DE_ORIENTACION.stream().anyMatch(palabra::startsWith)) {
                return true;
            }
            if (i + 1 < palabras.size() && pideComoHacer(palabra, palabras.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    static boolean pideSusDatos(String normalizado) {
        return palabras(normalizado).stream().anyMatch(PIDE_LO_SUYO::contains);
    }

    static boolean hablaDeInventario(String normalizado) {
        return palabras(normalizado).stream().anyMatch(TEMA_INVENTARIO::contains);
    }

    // "como puedo", "como organizar" (infinitivo), "como equipo" (verbo en
    // primera persona: termina en "o"), "how to", "how do". "como esta mi
    // inventario" o "como va" no cuentan: ahi pregunta por su estado.
    private static boolean pideComoHacer(String palabra, String siguiente) {
        if ("how".equals(palabra)) {
            return TRAS_HOW_PIDE_COMO_HACER.contains(siguiente);
        }
        return "como".equals(palabra)
            && (TRAS_COMO_PIDE_COMO_HACER.contains(siguiente) || esInfinitivo(siguiente)
            || esPrimeraPersona(siguiente));
    }

    private static boolean esInfinitivo(String palabra) {
        return palabra.length() >= 4
            && (palabra.endsWith("ar") || palabra.endsWith("er") || palabra.endsWith("ir"));
    }

    private static boolean esPrimeraPersona(String palabra) {
        return palabra.length() >= 4 && palabra.endsWith("o");
    }

    private static List<String> palabras(String normalizado) {
        return normalizado == null || normalizado.isBlank() ? List.of() : List.of(normalizado.split(" "));
    }
}
