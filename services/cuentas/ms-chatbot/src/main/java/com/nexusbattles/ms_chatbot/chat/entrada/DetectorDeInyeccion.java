package com.nexusbattles.ms_chatbot.chat.entrada;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 7.4.8 «detección y bloqueo de intentos de inyección de código».
 *
 * <p>El chatbot nunca ejecuta lo que escribe el usuario: las consultas van con
 * parámetros, la respuesta es JSON y la ventana pinta con {@code textContent}.
 * Eso protege, pero no detecta ni bloquea nada, y el documento pide las dos
 * cosas. Esto reconoce las formas conocidas de un intento (etiquetas y
 * manejadores de HTML, instrucciones SQL encadenadas, expresiones de
 * plantilla, comandos del sistema, operadores de NoSQL y rutas hacia ficheros
 * del sistema) para que la petición se rechace antes de guardarse o de llegar
 * a otro servicio.
 *
 * <p>Se buscan formas, no palabras sueltas: «unión», «seleccionar», «script»,
 * «javascript» o «borrar» en una pregunta normal no disparan nada;
 * {@code UNION SELECT}, {@code <script>}, {@code javascript:alert(1)} o
 * {@code '; DROP TABLE} sí. Las pruebas recorren todas las variantes de la
 * base de conocimiento para que ninguna pregunta legítima se bloquee.
 *
 * <p>Sin estado y seguro entre hilos.
 */
public final class DetectorDeInyeccion {

    /** Qué tipo de intento se reconoció. Viaja a la métrica, nunca el texto. */
    public enum Tipo { HTML, SQL, PLANTILLA, COMANDO, NOSQL, RUTA }

    private record Forma(Tipo tipo, Pattern patron) {
    }

    private static final int SIN_MAYUSCULAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Forma> FORMAS = List.of(
        // Etiquetas que ejecutan o cargan algo.
        forma(Tipo.HTML, "<\\s*/?\\s*(?:script|iframe|frame|frameset|object|embed|applet|meta|base|link|style"
            + "|svg|math|form|img|video|audio|source|body|html)\\b"),
        // Un manejador de eventos dentro de una etiqueta (<a onclick=...>, <x onerror=...>).
        forma(Tipo.HTML, "<[a-z][^<>]{0,200}\\bon[a-z]{3,25}\\s*="),
        // Un esquema que ejecuta código, pegado a lo que ejecuta (no «javascript: es...»).
        forma(Tipo.HTML, "\\b(?:javascript|vbscript|livescript):(?!\\s)"),
        forma(Tipo.HTML, "\\bdata\\s*:\\s*text/html"),
        // SQL: tautología tras una comilla (' OR '1'='1), UNION SELECT, sentencias
        // encadenadas tras «;» o tras cerrar una comilla, y funciones de espera.
        forma(Tipo.SQL, "['\"`]\\s*\\)?\\s*(?:or|and|\\|\\||&&)\\s*\\(?\\s*['\"`]?\\s*\\w+\\s*['\"`]?\\s*"
            + "(?:=|<>|!=|like)\\s*['\"`]?\\s*\\w+"),
        forma(Tipo.SQL, "\\bunion\\b(?:\\s+|/\\*.*?\\*/)+(?:all\\s+|distinct\\s+)?select\\b"),
        forma(Tipo.SQL, ";\\s*(?:drop|truncate|alter)\\s+(?:table|database|schema|user|role|view|index)\\b"),
        forma(Tipo.SQL, "(?:;|['\"`]\\s*\\)?\\s*;)\\s*(?:delete\\s+from|insert\\s+into|update\\s+[\\w.\"`]+\\s+set"
            + "|select\\s+[\\w*,\\s]+\\s+from)\\b"),
        forma(Tipo.SQL, "\\b(?:xp_cmdshell|information_schema|pg_sleep\\s*\\(|sleep\\s*\\(\\s*\\d"
            + "|benchmark\\s*\\(\\s*\\d|waitfor\\s+delay)"),
        // Expresiones de plantilla y de lenguaje de expresiones (Log4Shell, SpEL, SSTI, JSP).
        forma(Tipo.PLANTILLA, "\\$\\{\\s*(?:jndi|env|sys|java|ctx|lower|upper|date|main|bundle|spring|base64)\\s*:"),
        forma(Tipo.PLANTILLA, "[$#]\\{[^{}]{0,200}\\bT\\s*\\(\\s*java\\."),
        forma(Tipo.PLANTILLA, "\\{\\{[^{}]{0,200}(?:__class__|__proto__|__globals__|__subclasses__|__import__"
            + "|constructor|config|self\\.|request\\.|lipsum|cycler)[^{}]{0,200}\\}\\}"),
        forma(Tipo.PLANTILLA, "<%[=@!]?[^%]{0,200}%>"),
        // Un comando del sistema encadenado (;, &&, ||, |), entre comillas
        // invertidas o sustituido con $( ), con la forma con que se usa.
        forma(Tipo.COMANDO, "(?:;|&&|\\|\\||\\||`|\\$\\()\\s*(?:rm\\s+-[rf]|cat\\s+/|curl\\s+\\S|wget\\s+\\S|nc\\s+-"
            + "|ncat\\s|netcat\\s|bash\\s+-|sh\\s+-c|zsh\\s+-|powershell\\b|pwsh\\b|cmd(?:\\.exe)?\\s+/c"
            + "|chmod\\s+[0-7+]|chown\\s|python3?\\s+-c|perl\\s+-e|php\\s+-r|ruby\\s+-e|whoami\\b|uname\\s+-a)"),
        // Operadores de consulta de MongoDB dentro de un objeto JSON.
        forma(Tipo.NOSQL, "[{,]\\s*[\"']?\\$(?:where|ne|gt|gte|lt|lte|regex|in|nin|exists|expr|function"
            + "|accumulator)[\"']?\\s*:"),
        // Rutas que suben de directorio o apuntan a ficheros del sistema.
        forma(Tipo.RUTA, "(?:\\.\\.[/\\\\]){2,}|/etc/(?:passwd|shadow|hosts)\\b|c:\\\\windows\\\\")
    );

    private DetectorDeInyeccion() {
    }

    private static Forma forma(Tipo tipo, String expresion) {
        return new Forma(tipo, Pattern.compile(expresion, SIN_MAYUSCULAS));
    }

    /** El tipo del primer intento reconocido, o vacío si el texto es una pregunta normal. */
    public static Optional<Tipo> detectar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        for (Forma forma : FORMAS) {
            if (forma.patron().matcher(texto).find()) {
                return Optional.of(forma.tipo());
            }
        }
        return Optional.empty();
    }
}
