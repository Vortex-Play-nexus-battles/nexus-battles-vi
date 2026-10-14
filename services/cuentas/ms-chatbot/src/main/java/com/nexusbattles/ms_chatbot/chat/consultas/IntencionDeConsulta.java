package com.nexusbattles.ms_chatbot.chat.consultas;

import java.util.List;
import java.util.Set;

// Revision de plataforma (7.4.4): antes de consultar datos en vivo se
// clasifica QUE pide el jugador, en vez de excluir frases sueltas:
//
//   * orientacion: un consejo, como hacer algo o que pasa si... ("dame
//     consejos para organizar mi inventario", "que objetos me recomiendas
//     usar", "como equipo a mi heroe", "pierdo mis objetos") -> sigue a la
//     base de conocimiento;
//   * sus propios datos ("que tengo en mi inventario", "muestrame mis
//     heroes", "cuantos heroes tengo") -> se consulta el servicio real.
//
// 1.11.0: lo mismo para subastas, notificaciones, transacciones y misiones,
// no solo para el inventario. Hasta aqui esas cuatro solo se reconocian por
// frases fijas ("mis subastas", "mi mision"), y «¿tengo alguna misión
// activa?», «¿cuáles son mis últimas transacciones?» o «¿qué subastas
// tengo?» acababan en un tema general de la base. Y las preguntas de reglas
// sobre lo propio («¿puedo cancelar mi puja?», «¿qué pasa si abandono mi
// misión?», «¿tengo que pagar para subastar?») ya no se toman por una
// consulta de datos, tampoco cuando traen una de esas frases.
//
// Trabaja sobre el texto ya normalizado (sin tildes ni signos, en minusculas),
// igual que el resto del motor. Sin estado.
final class IntencionDeConsulta {

    /** De que datos de su cuenta puede hablar una pregunta. */
    enum Dominio {
        // "heroes" y "personajes" en plural: "mi heroe" suele ser una pregunta
        // de reglas ("como subo de nivel a mi heroe"), no de lo que se tiene.
        INVENTARIO(Set.of("inventario", "inventory", "heroes", "personajes", "armas", "items", "objetos", "equipo",
            "equipamiento", "gear", "equipment")),
        SUBASTAS(Set.of("subasta", "subastas", "puja", "pujas", "auction", "auctions", "bid", "bids")),
        NOTIFICACIONES(Set.of("notificacion", "notificaciones", "aviso", "avisos", "alerta", "alertas",
            "notification", "notifications", "alert", "alerts")),
        // Solo "transacciones": "movimiento" tambien es una jugada del combate
        // ("mi mejor movimiento"); los movimientos de creditos van por frase.
        MOVIMIENTOS(Set.of("transaccion", "transacciones", "transaction", "transactions")),
        MISIONES(Set.of("mision", "misiones", "mission", "missions", "quest", "quests"));

        private final Set<String> palabras;

        Dominio(Set<String> palabras) {
            this.palabras = palabras;
        }
    }

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

    // 1.11.0: frases que hacen de la pregunta una de reglas (que pasa si,
    // obligacion, permiso, por que), aunque hable de lo suyo.
    private static final List<String> FRASES_DE_REGLAS = List.of(
        "que pasa si", "que sucede si", "que ocurre si", "what happens if", "what happens to", "what if",
        "tengo que", "hay que", "tienes que", "have to", "need to", "do i need",
        "por que", "why", "para que sirve", "what is the point",
        "debo", "deberia", "should i", "se puede", "is it possible");

    // "puedo" / "can i" piden permiso ("puedo cancelar mi puja"), salvo si lo
    // que sigue es mirar lo suyo ("puedo ver mis subastas").
    private static final Set<String> PERMISO = Set.of("puedo", "podria");
    private static final Set<String> MIRAR = Set.of("ver", "consultar", "revisar", "mirar", "saber", "see", "check",
        "view", "know");

    // Palabras que dicen que el jugador pregunta por lo SUYO.
    private static final Set<String> PIDE_LO_SUYO = Set.of(
        "mi", "mis", "tengo", "muestrame", "ensename", "cuantos", "cuantas", "my", "mine");

    private IntencionDeConsulta() {
    }

    // Habla del inventario, pregunta por lo suyo y no pide orientacion.
    static boolean consultaSuInventario(String normalizado) {
        return consultaSus(normalizado, Dominio.INVENTARIO);
    }

    // 1.11.0: habla de ese dominio, pregunta por lo suyo y no es de reglas.
    static boolean consultaSus(String normalizado, Dominio dominio) {
        return hablaDe(normalizado, dominio) && pideSusDatos(normalizado) && !pideReglas(normalizado);
    }

    // Orientacion (consejo, como hacer algo) o una pregunta de reglas.
    static boolean pideReglas(String normalizado) {
        if (pideOrientacion(normalizado)) {
            return true;
        }
        String conBordes = " " + normalizado + " ";
        if (FRASES_DE_REGLAS.stream().anyMatch(frase -> conBordes.contains(" " + frase + " "))) {
            return true;
        }
        List<String> palabras = palabras(normalizado);
        for (int i = 0; i < palabras.size(); i++) {
            String siguiente = i + 1 < palabras.size() ? palabras.get(i + 1) : "";
            boolean permiso = PERMISO.contains(palabras.get(i))
                || ("can".equals(palabras.get(i)) || "could".equals(palabras.get(i))) && "i".equals(siguiente);
            if (permiso) {
                String verbo = "i".equals(siguiente) && i + 2 < palabras.size() ? palabras.get(i + 2) : siguiente;
                if (!MIRAR.contains(verbo)) {
                    return true;
                }
            }
        }
        return false;
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

    // Lo suyo: "mi", "mis", "tengo"... o "I have" / "have I" en ingles.
    static boolean pideSusDatos(String normalizado) {
        String conBordes = " " + normalizado + " ";
        return palabras(normalizado).stream().anyMatch(PIDE_LO_SUYO::contains)
            || conBordes.contains(" i have ") || conBordes.contains(" have i ");
    }

    static boolean hablaDeInventario(String normalizado) {
        return hablaDe(normalizado, Dominio.INVENTARIO);
    }

    static boolean hablaDe(String normalizado, Dominio dominio) {
        return palabras(normalizado).stream().anyMatch(dominio.palabras::contains);
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
