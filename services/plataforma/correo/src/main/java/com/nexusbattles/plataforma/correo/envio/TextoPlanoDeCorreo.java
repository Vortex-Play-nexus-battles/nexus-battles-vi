package com.nexusbattles.plataforma.correo.envio;

import java.util.regex.Pattern;

/**
 * La version en texto plano del correo, derivada del HTML.
 *
 * <p><b>Por que se deriva y no se escribe aparte.</b> Dos plantillas por
 * correo son dos plantillas que se desincronizan: la de texto se queda con el
 * mensaje del mes pasado y nadie se entera, porque casi nadie la lee. Derivar
 * garantiza que las dos versiones digan lo mismo siempre.
 *
 * <p><b>Por que hace falta.</b> Un mensaje solo-HTML pesa en casi todos los
 * filtros de correo no deseado, y deja sin contenido a quien lee en texto
 * plano o con un lector de pantalla que prefiere esa parte.
 *
 * <p>No pretende ser un conversor completo de HTML: quita el marcado, respeta
 * los saltos de parrafo y devuelve las entidades mas comunes. Para lo que
 * envia este servicio -- un saludo, un codigo y un pie -- es suficiente.
 */
final class TextoPlanoDeCorreo {

    private static final Pattern INVISIBLE =
            Pattern.compile("<(script|style|head)\\b[^>]*>.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SALTO =
            Pattern.compile("<(br|/p|/div|/tr|/h[1-6])\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");
    private static final Pattern LINEAS_DE_MAS = Pattern.compile("\\n{3,}");
    private static final Pattern ESPACIOS = Pattern.compile("[ \\t\\x0B\\f\\r]+");

    private TextoPlanoDeCorreo() {}

    static String desdeHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String texto = INVISIBLE.matcher(html).replaceAll(" ");
        texto = SALTO.matcher(texto).replaceAll("\n");
        texto = ETIQUETA.matcher(texto).replaceAll("");
        texto = entidades(texto);
        texto = ESPACIOS.matcher(texto).replaceAll(" ");
        // Se limpia linea a linea: si no, los espacios del sangrado del HTML
        // quedan al principio de cada frase.
        StringBuilder limpio = new StringBuilder();
        for (String linea : texto.split("\n")) {
            limpio.append(linea.trim()).append('\n');
        }
        return LINEAS_DE_MAS.matcher(limpio.toString()).replaceAll("\n\n").trim();
    }

    private static String entidades(String texto) {
        return texto.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&aacute;", "a")
                .replace("&eacute;", "e")
                .replace("&iacute;", "i")
                .replace("&oacute;", "o")
                .replace("&uacute;", "u")
                .replace("&ntilde;", "n");
    }
}