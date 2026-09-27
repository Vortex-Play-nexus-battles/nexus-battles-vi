package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

/**
 * Como casa un termino con un texto (moderacion-lista-negra.yaml 2.0.x).
 * La regla de cada modo vive en {@link DetectorDeTerminos}.
 */
public enum ModoDeCoincidencia {

    /** El termino se busca dentro del texto compacto: {@code xXspidermanXx} casa con «spiderman». */
    SUBCADENA,

    /** Solo casa una palabra entera (o el texto entero): «computadora» no casa con «puta». */
    PALABRA;

    /**
     * Longitud normalizada desde la que un termino se busca por omision como
     * subcadena. Por debajo, un termino corto aparece por azar dentro de
     * palabras legitimas (el problema «Scunthorpe»), asi que va como palabra.
     */
    public static final int LONGITUD_MINIMA_SUBCADENA = 5;

    /** El modo que toca a una forma normalizada cuando nadie elige otro. */
    public static ModoDeCoincidencia porOmision(String normalizado) {
        return normalizado != null && normalizado.length() >= LONGITUD_MINIMA_SUBCADENA ? SUBCADENA : PALABRA;
    }
}
