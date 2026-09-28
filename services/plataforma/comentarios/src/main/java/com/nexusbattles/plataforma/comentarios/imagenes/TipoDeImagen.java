package com.nexusbattles.plataforma.comentarios.imagenes;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Los tres formatos que el contrato 1.4.0 admite para las imagenes de un
 * comentario, y como se reconoce cada uno por sus primeros bytes.
 *
 * <p>Se reconocen por la <b>firma de bytes</b>, nunca por la extension del
 * nombre ni por el {@code Content-Type} que declara el navegador: los dos los
 * escribe quien sube el archivo, y un HTML llamado {@code foto.png} y enviado
 * como {@code image/png} sigue siendo un HTML.
 */
public enum TipoDeImagen {

    /** FF D8 FF: inicio de imagen JPEG seguido del primer marcador. */
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),

    /** 89 50 4E 47 0D 0A 1A 0A: la firma de 8 bytes de PNG. */
    PNG("image/png", Set.of("png")),

    /** "RIFF" + tamano + "WEBP": contenedor RIFF con forma WEBP. */
    WEBP("image/webp", Set.of("webp"));

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String tipoMime;
    private final Set<String> nombres;

    TipoDeImagen(String tipoMime, Set<String> nombres) {
        this.tipoMime = tipoMime;
        this.nombres = nombres;
    }

    /** El {@code Content-Type} con el que se sirve y el valor de la columna {@code tipo}. */
    public String tipoMime() {
        return tipoMime;
    }

    /** El formato que dicen los primeros bytes, si es uno de los tres. */
    public static Optional<TipoDeImagen> porFirma(byte[] bytes) {
        if (bytes == null) {
            return Optional.empty();
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (bytes.length >= FIRMA_PNG.length
                && Arrays.equals(bytes, 0, FIRMA_PNG.length, FIRMA_PNG, 0, FIRMA_PNG.length)) {
            return Optional.of(PNG);
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /** El formato de un tipo MIME guardado, para leerlo de la base. */
    public static TipoDeImagen porTipoMime(String tipoMime) {
        for (TipoDeImagen tipo : values()) {
            if (tipo.tipoMime.equals(tipoMime)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("tipo de imagen desconocido: " + tipoMime);
    }

    /**
     * El formato de un nombre de la configuracion ({@code jpg}, {@code png},
     * {@code webp}...), para {@code COMENTARIOS_FORMATOS_IMAGEN}.
     */
    public static Optional<TipoDeImagen> porNombre(String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        String normalizado = nombre.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(tipo -> tipo.nombres.contains(normalizado)).findFirst();
    }
}
