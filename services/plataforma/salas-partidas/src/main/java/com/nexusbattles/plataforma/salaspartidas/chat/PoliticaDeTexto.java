package com.nexusbattles.plataforma.salaspartidas.chat;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lo que un mensaje de chat tiene que cumplir ademas de la lista negra —
 * auditoria de DEV del 30-sep (7.3.3, HU-COM-007 «patrones sospechosos»).
 *
 * <p>En el chat general entro «un bloque enorme de ASCII art»: 500 caracteres
 * de simbolos y saltos de linea que la lista negra no tiene por que ver, porque
 * no contienen ninguna palabra. La lista negra decide QUE se dice; esto decide
 * si lo que llega es un mensaje o un dibujo, una inundacion o un truco con
 * caracteres invisibles. Puro: sin Spring ni red, lo prueba
 * {@code PoliticaDeTextoTest}.
 *
 * <p>Dos pasos:
 * <ol>
 *   <li>{@link #depurar}: lo que se publica. Unicode NFKC (las letras de ancho
 *       completo y las ligaduras pasan a su letra base: lo mismo que ve la
 *       lista negra), fuera los caracteres de control y de formato invisibles
 *       (anchos cero, marcas de direccion que invierten el texto), los saltos
 *       de linea en una sola forma y nunca mas de una linea en blanco
 *       seguida.</li>
 *   <li>{@link #problema}: si aun asi no es un mensaje. Demasiadas lineas, una
 *       racha larga del mismo caracter, o un texto largo hecho sobre todo de
 *       simbolos.</li>
 * </ol>
 *
 * <p>Los numeros son PROVISIONALES (D-37, pendiente del PO): ningun requisito
 * los fija. Se eligieron para no tocar una conversacion normal —un parrafo,
 * una lista corta, «jajaja», un «!!!»— y viven en la configuracion
 * ({@code chat.texto.*}), no en el codigo.
 */
public final class PoliticaDeTexto {

    /**
     * Controles (salvo el salto de linea, que se trata aparte) y caracteres de
     * formato invisibles: espacio de ancho cero, guion blando, marcas que
     * invierten la direccion del texto... El union de ancho cero (U+200D) se
     * respeta: es el que junta los emojis compuestos.
     */
    private static final Pattern INVISIBLES = Pattern.compile("[[\\p{Cf}&&[^\\u200D]][\\p{Cc}&&[^\\n]]]");

    private static final Pattern TRES_O_MAS_SALTOS = Pattern.compile("\\n{3,}");

    private PoliticaDeTexto() {
    }

    /**
     * @param maximoDeLineas         cuantas lineas caben en un mensaje
     * @param maximaRepeticion       cuantas veces seguidas puede ir el mismo caracter
     * @param largoParaMirarSimbolos desde cuantos caracteres visibles se mira la
     *                               proporcion de letras (un «:)» no es un dibujo)
     * @param minimoDeLetrasPorCiento que parte de esos caracteres tienen que ser
     *                               letras o cifras
     */
    public record Limites(int maximoDeLineas, int maximaRepeticion, int largoParaMirarSimbolos,
                          int minimoDeLetrasPorCiento) {

        /** Los valores por omision de {@code application.yml} (D-37). */
        public static final Limites POR_OMISION = new Limites(6, 15, 40, 50);

        public Limites {
            if (maximoDeLineas < 1 || maximaRepeticion < 2 || largoParaMirarSimbolos < 1
                    || minimoDeLetrasPorCiento < 0 || minimoDeLetrasPorCiento > 100) {
                throw new IllegalArgumentException("Limites de texto del chat fuera de rango: "
                        + maximoDeLineas + ", " + maximaRepeticion + ", " + largoParaMirarSimbolos + ", "
                        + minimoDeLetrasPorCiento);
            }
        }
    }

    /**
     * El texto tal como se publica y se guarda: NFKC, sin invisibles, con los
     * saltos de linea en {@code \n}, sin mas de una linea en blanco seguida y
     * sin espacios en los extremos. {@code null} queda en cadena vacia.
     */
    public static String depurar(String texto) {
        if (texto == null) {
            return "";
        }
        String unificado = Normalizer.normalize(texto, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ');
        String sinInvisibles = INVISIBLES.matcher(unificado).replaceAll("");
        return TRES_O_MAS_SALTOS.matcher(sinInvisibles).replaceAll("\n\n").strip();
    }

    /**
     * Por que un texto ya depurado no es un mensaje que se pueda publicar, o
     * vacio si lo es. La explicacion va tal cual al autor (problem details).
     */
    public static Optional<String> problema(String depurado, Limites limites) {
        Objects.requireNonNull(limites);
        if (depurado == null || depurado.isEmpty()) {
            return Optional.empty();
        }
        long lineas = depurado.chars().filter(c -> c == '\n').count() + 1;
        if (lineas > limites.maximoDeLineas()) {
            return Optional.of("El mensaje tiene demasiadas líneas: como máximo "
                    + limites.maximoDeLineas() + ".");
        }
        if (rachaMasLarga(depurado) > limites.maximaRepeticion()) {
            return Optional.of("El mensaje repite demasiadas veces seguidas el mismo carácter.");
        }
        long visibles = depurado.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        if (visibles >= limites.largoParaMirarSimbolos()) {
            long letras = depurado.codePoints().filter(Character::isLetterOrDigit).count();
            if (letras * 100 < (long) limites.minimoDeLetrasPorCiento() * visibles) {
                return Optional.of("El mensaje parece un dibujo hecho con símbolos: escríbelo con palabras.");
            }
        }
        return Optional.empty();
    }

    /** La racha mas larga del mismo caracter (por punto de codigo, espacios incluidos). */
    static int rachaMasLarga(String texto) {
        int mejor = 0;
        int actual = 0;
        int anterior = -1;
        for (int i = 0; i < texto.length(); ) {
            int c = texto.codePointAt(i);
            actual = c == anterior ? actual + 1 : 1;
            mejor = Math.max(mejor, actual);
            anterior = c;
            i += Character.charCount(c);
        }
        return mejor;
    }
}
