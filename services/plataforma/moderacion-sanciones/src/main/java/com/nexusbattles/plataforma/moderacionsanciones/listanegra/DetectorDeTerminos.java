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
 */
public final class DetectorDeTerminos {

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
        if (termino.modo() == ModoDeCoincidencia.SUBCADENA) {
            return texto.compacta().contains(forma);
        }
        if (texto.compacta().equals(forma)) {
            return true;
        }
        for (NormalizadorDeTexto.Palabra palabra : texto.palabras()) {
            if (palabra.es(forma)) {
                return true;
            }
        }
        return palabrasSeguidas(texto.palabras(), NormalizadorDeTexto.normalizar(termino.termino()).palabras());
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
