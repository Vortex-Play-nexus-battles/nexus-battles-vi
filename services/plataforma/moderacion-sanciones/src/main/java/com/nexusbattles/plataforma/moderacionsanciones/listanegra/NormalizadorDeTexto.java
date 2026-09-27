package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * La unica funcion de normalizacion de la lista negra: el texto a verificar y
 * cada termino pasan por ella, y se comparan sus resultados
 * ({@code contracts/openapi/moderacion-lista-negra.yaml} 2.0.x, «Como se
 * compara»).
 *
 * <p><b>Por que existe.</b> «spiderman» paso como apodo delante del profesor.
 * La tabla estaba vacia, pero aunque no lo hubiera estado, la comparacion
 * era {@code texto.contains(termino)} tras quitar tildes: «spider-man»,
 * «spider man» o «sp1derman» habrian pasado igual. Aqui se deja un solo
 * algoritmo, puro y probado, en vez de un {@code contains} escondido en un
 * servicio.
 *
 * <p><b>Pasos</b>, en este orden:
 * <ol>
 *   <li>Unicode NFKC: letras de ancho completo, ligaduras («ﬁ»), letras en
 *       circulo y demas formas de compatibilidad pasan a su letra base.</li>
 *   <li>minusculas con {@link Locale#ROOT}: nada de reglas turcas segun la
 *       maquina donde corra.</li>
 *   <li>NFD y fuera las marcas diacriticas ({@code \p{M}}): «spíderman» es
 *       «spiderman».</li>
 *   <li>fuera los caracteres de formato y de control que no son espacio: el
 *       espacio de ancho cero, el guion blando, la union de palabras...
 *       («spi&#8203;derman» es «spiderman»).</li>
 *   <li>leetspeak controlado, solo {@code 0→o 1→i 3→e 4→a 5→s 7→t @→a $→s}
 *       (ni una equivalencia mas que las del contrato).</li>
 *   <li>forma <b>compacta</b>: sin separadores (todo lo que no es letra ni
 *       cifra: espacios, guiones, puntos, guiones bajos, emojis) y con cada
 *       racha de tres o mas letras iguales reducida a una. La racha se reduce
 *       DESPUES de quitar separadores, para que «spi-i-i-derman» tambien sea
 *       «spiderman».</li>
 *   <li><b>palabras</b>: el texto cortado por los separadores, cada trozo con
 *       el mismo leetspeak y la misma reduccion de rachas. Una palabra con
 *       cifras al principio o al final cuenta tambien sin ellas («Messi10» es
 *       la palabra «messi» y «Puta69» la palabra «puta»): un numero pegado no
 *       convierte a una persona o a un insulto en otra cosa. Las cifras entre
 *       letras son leetspeak, no un corte («m3ss1» es «messi»).</li>
 * </ol>
 *
 * <p><b>Limites conocidos</b> (lo que esta funcion NO atrapa, a proposito o por
 * ahora):
 * <ul>
 *   <li>homoglifos de otros alfabetos: una «а» cirilica o una «ο» griega no se
 *       traducen a la letra latina que imitan (NFKC no lo hace y una tabla de
 *       confusables es otro trabajo);</li>
 *   <li>leetspeak fuera de la tabla del contrato ({@code | ! 8 9 +}, «v» por
 *       «u»...) y letras sustituidas por comodines ({@code sp*derman});</li>
 *   <li>letras intercaladas o de relleno («spideXrman»), variantes fonéticas
 *       («espaiderman») y traducciones («hombre araña»);</li>
 *   <li>una racha DOBLE del termino escrita triple en el texto: «zorrro»
 *       queda en «zoro» y no contiene «zorro». Es la consecuencia directa de
 *       reducir las rachas de tres o mas «a una», como pide el contrato;</li>
 *   <li>la forma compacta junta palabras vecinas: un termino en modo
 *       SUBCADENA puede aparecer entre dos palabras legitimas («esta linea»
 *       contiene «stalin»). Por eso los terminos que se forman asi con
 *       facilidad van en modo PALABRA; ver {@link DetectorDeTerminos}.</li>
 * </ul>
 *
 * <p><b>Si esta funcion cambia</b>, la forma guardada de los terminos
 * ({@code terminos_prohibidos.normalizado}) deja de ser comparable: hace falta
 * una migracion que la recalcule, como hizo V5 con las filas anteriores.
 */
public final class NormalizadorDeTexto {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    /** Formato (ancho cero, guion blando, BOM...) y controles que no son espacios. */
    private static final Pattern INVISIBLES = Pattern.compile("[\\p{Cf}[\\p{Cc}&&[^\\t\\n\\r\\f\\x0B]]]+");

    /** Todo lo que no forma parte de una palabra ANTES del leetspeak: @ y $ son letras disfrazadas. */
    private static final Pattern FUERA_DE_PALABRA = Pattern.compile("[^\\p{L}\\p{N}@$]+");

    /** Todo lo que no es letra ni cifra DESPUES del leetspeak. */
    private static final Pattern SEPARADORES = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern RACHAS = Pattern.compile("(\\p{L})\\1{2,}");

    private static final Pattern CIFRAS_AL_PRINCIPIO = Pattern.compile("^\\p{N}+");

    private static final Pattern CIFRAS_AL_FINAL = Pattern.compile("\\p{N}+$");

    private NormalizadorDeTexto() {
    }

    /**
     * Una palabra del texto normalizada.
     *
     * @param completa             la palabra entera, con su leetspeak
     * @param sinCifrasEnLosBordes la misma sin las cifras del principio y del
     *                             final, o {@code null} si no tenia o si no
     *                             queda nada
     */
    public record Palabra(String completa, String sinCifrasEnLosBordes) {

        public Palabra {
            Objects.requireNonNull(completa);
        }

        /** Si esta palabra es {@code forma}, entera o sin sus cifras de los bordes. */
        public boolean es(String forma) {
            return completa.equals(forma) || forma.equals(sinCifrasEnLosBordes);
        }
    }

    /**
     * El resultado de normalizar: la forma compacta y las palabras.
     *
     * @param compacta sin separadores; es la que se guarda como
     *                 {@code normalizado} de un termino
     * @param palabras en el orden del texto
     */
    public record FormaNormalizada(String compacta, List<Palabra> palabras) {

        public FormaNormalizada {
            Objects.requireNonNull(compacta);
            palabras = List.copyOf(palabras);
        }
    }

    /** Normaliza un texto o un termino. {@code null} se trata como vacio. */
    public static FormaNormalizada normalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return new FormaNormalizada("", List.of());
        }
        String base = base(texto);
        String compacta = reducirRachas(SEPARADORES.matcher(leetspeak(base)).replaceAll(""));
        List<Palabra> palabras = new ArrayList<>();
        for (String trozo : FUERA_DE_PALABRA.split(base)) {
            if (trozo.isEmpty()) {
                continue;
            }
            String completa = palabra(trozo);
            String recortado = CIFRAS_AL_FINAL.matcher(CIFRAS_AL_PRINCIPIO.matcher(trozo).replaceFirst(""))
                    .replaceFirst("");
            String sinCifras = recortado.isEmpty() || recortado.equals(trozo) ? null : palabra(recortado);
            palabras.add(new Palabra(completa, sinCifras));
        }
        return new FormaNormalizada(compacta, palabras);
    }

    /** Atajo: la forma compacta, que es la que se guarda y se compara en SUBCADENA. */
    public static String compacta(String texto) {
        return normalizar(texto).compacta();
    }

    /** Pasos 1 a 4: NFKC, minusculas, sin diacriticos y sin invisibles. */
    private static String base(String texto) {
        String compatible = Normalizer.normalize(texto, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String sinMarcas = MARCAS.matcher(Normalizer.normalize(compatible, Normalizer.Form.NFD)).replaceAll("");
        return INVISIBLES.matcher(sinMarcas).replaceAll("");
    }

    private static String palabra(String trozo) {
        return reducirRachas(SEPARADORES.matcher(leetspeak(trozo)).replaceAll(""));
    }

    /** Paso 5: exactamente la tabla del contrato. */
    static String leetspeak(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            salida.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> c;
            });
        }
        return salida.toString();
    }

    /** Tres o mas letras iguales seguidas quedan en una; dos se respetan («zorro»). */
    static String reducirRachas(String texto) {
        return RACHAS.matcher(texto).replaceAll("$1");
    }
}
