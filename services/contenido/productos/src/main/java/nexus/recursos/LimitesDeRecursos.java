package nexus.recursos;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cuánto puede pesar y medir cada clase de recurso (HU-PRD-009: «un recurso
 * que no cumple el formato o el tamaño admitidos… se rechaza indicando el
 * requisito incumplido»).
 *
 * <p>El documento del curso no fija las cifras: son decisión técnica y se
 * leen de la configuración ({@code catalogo.recursos.*}, con variable de
 * entorno, regla 10). Los valores por omisión están en
 * {@code application.properties}: 2 MB y 4096 px para una imagen —lo mismo
 * que las imágenes de los comentarios—, 256 KB y 512 px para un icono y 4 MB y
 * 1024 px para una animación.
 */
public final class LimitesDeRecursos {

    /** Peso y lado máximos de una clase. */
    public record Limite(int bytesMaximos, int ladoMaximo) {

        public Limite {
            if (bytesMaximos < 1 || ladoMaximo < 1) {
                throw new IllegalArgumentException("los limites de un recurso tienen que ser positivos");
            }
        }

        /** «2 MB», «256 KB», para los mensajes. */
        public String pesoEnPalabras() {
            return enPalabras(bytesMaximos);
        }
    }

    private final Map<ClaseDeRecurso, Limite> limites;

    public LimitesDeRecursos(Limite imagen, Limite icono, Limite animacion) {
        Map<ClaseDeRecurso, Limite> mapa = new EnumMap<>(ClaseDeRecurso.class);
        mapa.put(ClaseDeRecurso.IMAGEN, Objects.requireNonNull(imagen));
        mapa.put(ClaseDeRecurso.ICONO, Objects.requireNonNull(icono));
        mapa.put(ClaseDeRecurso.ANIMACION, Objects.requireNonNull(animacion));
        this.limites = Map.copyOf(mapa);
    }

    /** Los de {@code application.properties}, para las pruebas que no levantan Spring. */
    public static LimitesDeRecursos porOmision() {
        return new LimitesDeRecursos(
                new Limite(2 * 1024 * 1024, 4096),
                new Limite(256 * 1024, 512),
                new Limite(4 * 1024 * 1024, 1024));
    }

    public Limite de(ClaseDeRecurso clase) {
        return limites.get(Objects.requireNonNull(clase));
    }

    /** El mayor de los pesos: lo que el multipart tiene que dejar pasar. */
    public int mayorPeso() {
        return limites.values().stream().mapToInt(Limite::bytesMaximos).max().orElse(0);
    }

    /** Un peso en palabras: «2 MB», «256 KB», «1,5 MB», «900 bytes». */
    public static String enPalabras(long bytes) {
        if (bytes >= 1024L * 1024L) {
            double megas = bytes / (1024d * 1024d);
            return (megas == Math.rint(megas)
                    ? String.valueOf((long) megas)
                    : String.format(java.util.Locale.ROOT, "%.1f", megas).replace('.', ',')) + " MB";
        }
        if (bytes >= 1024L) {
            double kilos = bytes / 1024d;
            return (kilos == Math.rint(kilos)
                    ? String.valueOf((long) kilos)
                    : String.format(java.util.Locale.ROOT, "%.1f", kilos).replace('.', ',')) + " KB";
        }
        return bytes + (bytes == 1 ? " byte" : " bytes");
    }
}
