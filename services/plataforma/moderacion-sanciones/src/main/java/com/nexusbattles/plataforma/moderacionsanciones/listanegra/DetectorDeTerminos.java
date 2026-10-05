package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Que terminos de la lista negra aparecen en un texto ya normalizado
 * (moderacion-lista-negra.yaml 2.0.x). Puro: sin Spring, sin base de datos,
 * sin cache; lo prueba {@code DetectorDeTerminosTest} con la regresion del
 * caso «spiderman».
 *
 * <p><b>SUBCADENA</b>: la forma compacta del termino aparece dentro de la
 * forma compacta del texto. Es lo que atrapa «xXspidermanXx» y «spider man».
 * Su precio es que la forma compacta junta palabras vecinas: «esta linea» es
 * «estalinea» y contiene «stalin». Un termino que se forma asi con facilidad
 * entre palabras corrientes debe ir en modo PALABRA.
 *
 * <p><b>PALABRA</b>: casa si
 * <ul>
 *   <li>alguna palabra del texto es la forma compacta del termino (entera o
 *       sin sus cifras de los bordes: «Messi10»), o</li>
 *   <li>las palabras del termino aparecen seguidas, en orden y enteras, en el
 *       texto («coca-cola» con el termino «coca cola»), o</li>
 *   <li>el texto compacto entero es el termino («p.u.t.a», «p u t a»).</li>
 * </ul>
 * Asi «computadora», «disputa» o «vehiculo» no caen por contener un termino
 * corto. Su limite es el reves: «xXmessiXx» no casa con «messi» en este modo.
 *
 * <p><b>Plural y genero de los insultos</b> (auditoria de DEV del 30-sep: en
 * el chat general pasaron «putas», «malparida» y «pendeja» con «puta»,
 * «malparido» y «pendejo» en la lista). Solo para la categoria OFENSIVO —una
 * marca o una persona no se declinan—:
 * <ul>
 *   <li>PALABRA tambien casa con el plural: «putas», «culos», «idiotas»
 *       ({@code +s} tras vocal, {@code +es} tras consonante). No se cambia el
 *       genero ni se forman diminutivos: «perra» no puede atrapar a «perro» ni
 *       «perrita» a la mascota de nadie. Un femenino que haga falta se da de
 *       alta como termino propio («puta» y «puto» van por separado).</li>
 *   <li>SUBCADENA de seis letras o mas que acaba en «o» o «a» casa tambien con
 *       su raiz sin esa vocal: «malparid» atrapa malparido, malparida,
 *       malparidos y malparidas. Las de menos letras no se recortan: la raiz
 *       seria tan corta que apareceria entre palabras corrientes.</li>
 *   <li>Y el numero (RFINAL-02, informes del 4-oct: «gilipolla» pasaba con
 *       «gilipollas» en la lista). Un termino dado de alta en plural —acaba en
 *       «as» u «os» y su singular tiene seis letras o mas— casa tambien con su
 *       singular y con la raiz de ese singular: «gilipollas» atrapa
 *       gilipolla y gilipollo. Es lo que D-38 ya decia («raiz de genero y
 *       numero en SUBCADENA») y solo se cumplia para los terminos dados de alta
 *       en singular.</li>
 * </ul>
 */
public final class DetectorDeTerminos {

    /** Una SUBCADENA se recorta a su raiz solo desde este largo (normalizado). */
    static final int LARGO_MINIMO_PARA_RAIZ = 6;

    private DetectorDeTerminos() {
    }

    /**
     * @param texto    el texto ya pasado por {@link NormalizadorDeTexto}
     * @param terminos los terminos activos, en el orden en que se quieren las
     *                 coincidencias
     * @return los terminos que aparecen, sin repetir y en el orden recibido
     */
    public static List<TerminoActivo> coincidencias(NormalizadorDeTexto.FormaNormalizada texto,
                                                    Collection<TerminoActivo> terminos) {
        Set<TerminoActivo> encontrados = new LinkedHashSet<>();
        for (TerminoActivo termino : terminos) {
            if (!termino.normalizado().isEmpty() && aparece(texto, termino)) {
                encontrados.add(termino);
            }
        }
        return new ArrayList<>(encontrados);
    }

    private static boolean aparece(NormalizadorDeTexto.FormaNormalizada texto, TerminoActivo termino) {
        String forma = termino.normalizado();
        boolean seDeclina = termino.categoria() == CategoriaDeTermino.OFENSIVO;
        if (termino.modo() == ModoDeCoincidencia.SUBCADENA) {
            if (texto.compacta().contains(forma)) {
                return true;
            }
            if (!seDeclina) {
                return false;
            }
            for (String raiz : raices(forma)) {
                if (texto.compacta().contains(raiz)) {
                    return true;
                }
            }
            return false;
        }
        Set<String> formas = seDeclina ? conPlural(forma) : Set.of(forma);
        if (formas.contains(texto.compacta())) {
            return true;
        }
        for (NormalizadorDeTexto.Palabra palabra : texto.palabras()) {
            for (String unaForma : formas) {
                if (palabra.es(unaForma)) {
                    return true;
                }
            }
        }
        return palabrasSeguidas(texto.palabras(), NormalizadorDeTexto.normalizar(termino.termino()).palabras());
    }

    /**
     * La forma y su plural: {@code +s} si acaba en vocal, {@code +es} si no.
     * Nada mas (ver la cabecera: ni genero ni diminutivos en PALABRA).
     */
    static Set<String> conPlural(String forma) {
        if (forma.isEmpty()) {
            return Set.of(forma);
        }
        char ultima = forma.charAt(forma.length() - 1);
        String plural = "aeiou".indexOf(ultima) >= 0 ? forma + "s" : forma + "es";
        return Set.of(forma, plural);
    }

    /**
     * Las formas recortadas con las que tambien casa una SUBCADENA ofensiva:
     * el singular si el termino esta en plural, y la raiz sin genero del
     * singular (o del termino). Vacia si el termino es corto o no se declina.
     */
    static List<String> raices(String forma) {
        List<String> raices = new ArrayList<>(2);
        String singular = singularDe(forma);
        if (singular != null) {
            raices.add(singular);
        }
        String raiz = raizSinGenero(singular != null ? singular : forma);
        if (raiz != null) {
            raices.add(raiz);
        }
        return raices;
    }

    /**
     * El singular de un plural en «as» u «os» («gilipollas» → «gilipolla»,
     * «pendejos» → «pendejo»), o {@code null} si no acaba asi o si el singular
     * tendria menos de {@value #LARGO_MINIMO_PARA_RAIZ} letras. Los plurales en
     * «es» («maricones») no se recortan: quitar la «s» no da su singular y
     * quitar «es» rompe palabras como «deportes».
     */
    static String singularDe(String forma) {
        int largo = forma.length();
        if (largo < LARGO_MINIMO_PARA_RAIZ + 1 || forma.charAt(largo - 1) != 's') {
            return null;
        }
        char antes = forma.charAt(largo - 2);
        return antes == 'a' || antes == 'o' ? forma.substring(0, largo - 1) : null;
    }

    /**
     * La raiz comun a genero y numero de una SUBCADENA ofensiva larga que acaba
     * en «o» o «a» («pendejo» → «pendej»), o {@code null} si no se recorta.
     */
    static String raizSinGenero(String forma) {
        if (forma.length() < LARGO_MINIMO_PARA_RAIZ) {
            return null;
        }
        char ultima = forma.charAt(forma.length() - 1);
        return ultima == 'o' || ultima == 'a' ? forma.substring(0, forma.length() - 1) : null;
    }

    /** Las palabras del termino (dos o mas) aparecen seguidas y enteras en el texto. */
    private static boolean palabrasSeguidas(List<NormalizadorDeTexto.Palabra> texto,
                                            List<NormalizadorDeTexto.Palabra> termino) {
        if (termino.size() < 2 || termino.size() > texto.size()) {
            return false;
        }
        for (int inicio = 0; inicio + termino.size() <= texto.size(); inicio++) {
            boolean todas = true;
            for (int i = 0; i < termino.size() && todas; i++) {
                todas = texto.get(inicio + i).es(termino.get(i).completa());
            }
            if (todas) {
                return true;
            }
        }
        return false;
    }
}
