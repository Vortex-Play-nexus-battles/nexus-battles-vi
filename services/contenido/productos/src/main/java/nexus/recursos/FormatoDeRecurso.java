package nexus.recursos;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los formatos de archivo que el catálogo acepta como recurso gráfico
 * (HU-PRD-009, §7.2.3) y cómo se reconoce cada uno por sus primeros bytes.
 *
 * <p>Se reconocen por la <b>firma de bytes</b>, nunca por la extensión del
 * nombre ni por el {@code Content-Type} que declara el navegador: los dos los
 * escribe quien sube el archivo. Es el mismo criterio que las imágenes de los
 * comentarios (comentarios.yaml 1.4.0); el código no se comparte porque cada
 * servicio es dueño de su validación (regla de microservicios).
 *
 * <p>SVG no está: un SVG es un documento con scripts posibles, no una imagen
 * de píxeles, y servirlo desde el mismo origen que la aplicación abriría la
 * puerta a ejecutar código en el navegador de quien lo mire.
 */
public enum FormatoDeRecurso {

    /** 89 50 4E 47 0D 0A 1A 0A. Con bloque {@code acTL} es un PNG animado (APNG). */
    PNG("image/png", "PNG"),

    /** FF D8 FF. */
    JPEG("image/jpeg", "JPEG"),

    /** "RIFF" + tamaño + "WEBP". Con la marca de animación de {@code VP8X}, animado. */
    WEBP("image/webp", "WebP"),

    /** "GIF87a" o "GIF89a". */
    GIF("image/gif", "GIF");

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String tipoMime;
    private final String nombre;

    FormatoDeRecurso(String tipoMime, String nombre) {
        this.tipoMime = tipoMime;
        this.nombre = nombre;
    }

    /** El {@code Content-Type} con el que se sirve. */
    public String tipoMime() {
        return tipoMime;
    }

    /** Como se le dice a quien administra el catálogo («PNG», «WebP»…). */
    public String nombre() {
        return nombre;
    }

    /** El formato que dicen los primeros bytes, si es uno de los cuatro. */
    public static Optional<FormatoDeRecurso> porFirma(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (bytes.length >= FIRMA_PNG.length
                && Arrays.equals(bytes, 0, FIRMA_PNG.length, FIRMA_PNG, 0, FIRMA_PNG.length)) {
            return Optional.of(PNG);
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of(WEBP);
        }
        if (bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
            return Optional.of(GIF);
        }
        return Optional.empty();
    }

    /** El formato de un tipo MIME guardado. */
    public static FormatoDeRecurso porTipoMime(String tipoMime) {
        for (FormatoDeRecurso formato : values()) {
            if (formato.tipoMime.equals(tipoMime)) {
                return formato;
            }
        }
        throw new IllegalArgumentException("formato de recurso desconocido: " + tipoMime);
    }
}
