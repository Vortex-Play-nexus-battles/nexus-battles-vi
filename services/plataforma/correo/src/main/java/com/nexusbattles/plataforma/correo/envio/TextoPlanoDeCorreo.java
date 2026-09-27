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
 * los saltos de parrafo, separa las celdas de una tabla y devuelve las
 * entidades mas comunes. Para lo que envia este servicio -- un saludo, un
 * codigo, el detalle de una compra y un pie -- es suficiente.
 */
final class TextoPlanoDeCorreo {

    private static final Pattern INVISIBLE =
            Pattern.compile("<(script|style|head)\\b[^>]*>.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SALTO =
            Pattern.compile("<(br|/p|/div|/tr|/h[1-6])\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    /**
     * Fin de celda. Sin separador, una fila del detalle de compra saldria
     * pegada: "Espada2100.00 COP200.00 COP".
     */
    private static final Pattern FIN_DE_CELDA = Pattern.compile("</t[dh]\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");
    private static final Pattern LINEAS_DE_MAS = Pattern.compile("\\n{3,}");
    private static final Pattern ESPACIOS = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    /** Cualquier blanco del fuente, saltos de linea incluidos. */
    private static final Pattern BLANCOS = Pattern.compile("\\s+");
    /** Separadores de celda que quedan al principio o al final de una linea. */
    private static final Pattern SEPARADOR_EN_LOS_BORDES = Pattern.compile("^[\\s|]+|[\\s|]+$");
    private static final String SEPARADOR = " | ";

    private TextoPlanoDeCorreo() {}

    static String desdeHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String texto = INVISIBLE.matcher(html).replaceAll(" ");
        // En HTML un salto de linea del fuente es un espacio mas: los saltos
        // de verdad los ponen las etiquetas de bloque. Sin esto, una frase
        // partida en dos lineas de la plantilla salia partida, y cada celda
        // de una fila escrita en su propia linea salia en una linea aparte.
        texto = BLANCOS.matcher(texto).replaceAll(" ");
        texto = SALTO.matcher(texto).replaceAll("\n");
        texto = FIN_DE_CELDA.matcher(texto).replaceAll(SEPARADOR);
        texto = ETIQUETA.matcher(texto).replaceAll("");
        texto = entidades(texto);
        // Se limpia linea a linea: si no, los espacios del sangrado del HTML
        // quedan al principio de cada frase, y las tablas de maquetacion
        // dejan separadores sueltos en los bordes.
        StringBuilder limpio = new StringBuilder();
        for (String linea : texto.split("\n")) {
            String sinEspaciosDeMas = ESPACIOS.matcher(linea).replaceAll(" ");
            limpio.append(SEPARADOR_EN_LOS_BORDES.matcher(sinEspaciosDeMas).replaceAll("")).append('\n');
        }
        return LINEAS_DE_MAS.matcher(limpio.toString()).replaceAll("\n\n").trim();
    }

    private static String entidades(String texto) {
        return texto.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&aacute;", "a")
                .replace("&eacute;", "e")
                .replace("&iacute;", "i")
                .replace("&oacute;", "o")
                .replace("&uacute;", "u")
                .replace("&ntilde;", "n")
                // La ultima: si fuera antes, "&amp;lt;" acabaria en "<" en vez de "&lt;".
                .replace("&amp;", "&");
    }
}
