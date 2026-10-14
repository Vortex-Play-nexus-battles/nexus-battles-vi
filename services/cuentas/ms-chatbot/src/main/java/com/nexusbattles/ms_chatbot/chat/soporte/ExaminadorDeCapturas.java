package com.nexusbattles.ms_chatbot.chat.soporte;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * ms-chatbot.yaml 1.14.0 (§7.4.6 «adjuntar capturas de pantalla para soporte
 * tecnico»): decide si unos bytes son una captura que se puede guardar y
 * servir, y le quita los metadatos.
 *
 * <h2>Que comprueba, en este orden</h2>
 * <ol>
 *   <li><b>Tamano</b>: de 1 byte a 2 MB (413).</li>
 *   <li><b>Firma de bytes</b>: PNG o JPEG por sus primeros bytes, nunca por
 *       el nombre ni por el {@code Content-Type} declarado (415).</li>
 *   <li><b>Estructura completa, sin decodificar</b>: un PNG tiene que empezar
 *       por {@code IHDR}, cuadrar cada bloque con su CRC, tener datos y
 *       acabar exactamente en {@code IEND}; un JPEG, recorrer sus segmentos
 *       hasta el comienzo de la imagen y acabar en {@code FF D9}. Asi cae el
 *       archivo cortado y el poliglota (una imagen con un HTML o un ZIP
 *       pegado detras) (415).</li>
 *   <li><b>Sin metadatos</b>: se reescribe dejando solo lo que hace falta
 *       para ver la imagen. Del PNG se quitan los bloques de texto
 *       ({@code tEXt}, {@code zTXt}, {@code iTXt}), {@code eXIf},
 *       {@code tIME} y cualquier otro auxiliar; del JPEG, los segmentos EXIF y
 *       XMP ({@code APP1}), IPTC ({@code APP13}), los comentarios y los demas
 *       {@code APPn} salvo JFIF, el perfil de color y Adobe. Una foto del
 *       movil puede llevar la ubicacion en su EXIF: no llega a la base.</li>
 *   <li><b>Dimensiones leidas solo de la cabecera</b>: como mucho 4096x4096
 *       (413), con un {@link ImageReader} al que se le piden ancho y alto.</li>
 * </ol>
 *
 * <p>No decodificar es deliberado, como en el examinador de imagenes de
 * comentarios: decodificar una imagen hostil es donde estan los fallos de las
 * bibliotecas, y una cabecera que miente sobre sus dimensiones pide cientos
 * de megas con unos pocos kilobytes. Aqui nunca se construye la imagen.
 *
 * <p>Lo que se pierde al quitar los metadatos: la orientacion de una foto
 * del movil (va en el EXIF). Una captura de pantalla no la usa.
 */
public final class ExaminadorDeCapturas {

    /** D-CHA-12: lo que pesa como mucho una captura. */
    public static final int TAMANO_MAXIMO = 2 * 1024 * 1024;

    /** D-CHA-12: lo que mide como mucho cada lado. */
    public static final int DIMENSION_MAXIMA = 4096;

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    // Los bloques del PNG que hacen falta para verla bien; lo demas se quita.
    private static final Set<String> BLOQUES_PNG_QUE_SE_QUEDAN = Set.of(
        "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs", "bKGD");

    /** Los dos formatos que se admiten. */
    public enum Formato {
        PNG("image/png", "png"),
        JPEG("image/jpeg", "jpg");

        private final String tipoMime;
        private final String extension;

        Formato(String tipoMime, String extension) {
            this.tipoMime = tipoMime;
            this.extension = extension;
        }

        public String tipoMime() {
            return tipoMime;
        }

        public String extension() {
            return extension;
        }

        public static Optional<Formato> porTipoMime(String tipo) {
            for (Formato formato : values()) {
                if (formato.tipoMime.equals(tipo)) {
                    return Optional.of(formato);
                }
            }
            return Optional.empty();
        }
    }

    /** Una captura que paso el examen, ya sin metadatos. */
    public record CapturaExaminada(Formato formato, int ancho, int alto, byte[] contenido) {
    }

    private record Dimensiones(int ancho, int alto) {
    }

    /**
     * @throws CapturaNoAdmitidaException     si no es un PNG o un JPEG bien formado
     * @throws CapturaDemasiadoGrandeException si pesa mas de 2 MB o mide mas de 4096 en algun lado
     */
    public CapturaExaminada examinar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new CapturaNoAdmitidaException("El archivo esta vacio.");
        }
        if (bytes.length > TAMANO_MAXIMO) {
            throw new CapturaDemasiadoGrandeException("La captura pesa mas de 2 MB.");
        }
        Formato formato = porFirma(bytes).orElseThrow(() -> new CapturaNoAdmitidaException(
            "La captura tiene que ser una imagen PNG o JPEG (se mira su contenido, no su nombre)."));
        byte[] limpia = formato == Formato.PNG ? pngSinMetadatos(bytes) : jpegSinMetadatos(bytes);
        // Una cabecera de cero pixeles ni llega aqui: el lector de ImageIO la
        // rechaza (415), asi que las medidas son siempre de 1 en adelante.
        Dimensiones medidas = dimensionesPorCabecera(limpia, formato == Formato.PNG ? "png" : "jpeg");
        if (medidas.ancho() > DIMENSION_MAXIMA || medidas.alto() > DIMENSION_MAXIMA) {
            throw new CapturaDemasiadoGrandeException("La captura mide mas de 4096 pixeles por lado.");
        }
        return new CapturaExaminada(formato, medidas.ancho(), medidas.alto(), limpia);
    }

    static Optional<Formato> porFirma(byte[] bytes) {
        if (bytes.length >= FIRMA_PNG.length && empiezaPor(bytes, FIRMA_PNG)) {
            return Optional.of(Formato.PNG);
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(Formato.JPEG);
        }
        return Optional.empty();
    }

    private static boolean empiezaPor(byte[] bytes, byte[] prefijo) {
        for (int i = 0; i < prefijo.length; i++) {
            if (bytes[i] != prefijo[i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------- PNG

    /**
     * Recorre los bloques sin descomprimir nada y escribe solo los que se
     * quedan, tal cual (con su CRC). El primero tiene que ser {@code IHDR},
     * tiene que haber datos y el archivo acaba exactamente en {@code IEND}.
     */
    static byte[] pngSinMetadatos(byte[] bytes) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(bytes.length);
        salida.write(FIRMA_PNG, 0, FIRMA_PNG.length);
        int posicion = FIRMA_PNG.length;
        boolean primero = true;
        boolean hayDatos = false;
        CRC32 crc = new CRC32();
        while (true) {
            if (posicion + 12 > bytes.length) {
                throw new CapturaNoAdmitidaException("El PNG esta cortado.");
            }
            long largo = enteroDe4(bytes, posicion);
            if (largo > bytes.length - posicion - 12L) {
                throw new CapturaNoAdmitidaException("El PNG esta cortado.");
            }
            String tipoDeBloque = new String(bytes, posicion + 4, 4, StandardCharsets.US_ASCII);
            if (primero && (!"IHDR".equals(tipoDeBloque) || largo != 13)) {
                throw new CapturaNoAdmitidaException("El PNG no empieza por su cabecera.");
            }
            crc.reset();
            crc.update(bytes, posicion + 4, 4 + (int) largo);
            if (crc.getValue() != enteroDe4(bytes, posicion + 8 + (int) largo)) {
                throw new CapturaNoAdmitidaException("El PNG tiene un bloque danado.");
            }
            int total = 12 + (int) largo;
            if (BLOQUES_PNG_QUE_SE_QUEDAN.contains(tipoDeBloque)) {
                salida.write(bytes, posicion, total);
            }
            hayDatos |= "IDAT".equals(tipoDeBloque);
            posicion += total;
            primero = false;
            if ("IEND".equals(tipoDeBloque)) {
                if (!hayDatos) {
                    throw new CapturaNoAdmitidaException("El PNG no tiene datos de imagen.");
                }
                if (posicion != bytes.length) {
                    throw new CapturaNoAdmitidaException("El PNG lleva algo pegado detras de su final.");
                }
                return salida.toByteArray();
            }
        }
    }

    private static long enteroDe4(byte[] bytes, int desde) {
        return ((long) (bytes[desde] & 0xFF) << 24) | ((bytes[desde + 1] & 0xFF) << 16)
            | ((bytes[desde + 2] & 0xFF) << 8) | (bytes[desde + 3] & 0xFF);
    }

    // ------------------------------------------------------------------ JPEG

    /**
     * Recorre los segmentos hasta el comienzo de la imagen ({@code SOS}),
     * quita los de metadatos y copia el resto tal cual. Despues del
     * {@code SOS} van los datos comprimidos hasta {@code FF D9}: se copian
     * enteros (se toleran ceros de relleno al final, que escriben algunas
     * camaras; cualquier otra cosa detras es un archivo con algo pegado).
     */
    static byte[] jpegSinMetadatos(byte[] bytes) {
        int fin = bytes.length;
        while (fin > 0 && bytes[fin - 1] == 0) {
            fin--;
        }
        if (fin < 4 || (bytes[fin - 2] & 0xFF) != 0xFF || (bytes[fin - 1] & 0xFF) != 0xD9) {
            throw new CapturaNoAdmitidaException("El JPEG no termina donde debe: esta cortado o lleva algo pegado.");
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream(fin);
        salida.write(0xFF);
        salida.write(0xD8);
        int posicion = 2;
        boolean hayCuadro = false;
        while (true) {
            if (posicion + 4 > fin || (bytes[posicion] & 0xFF) != 0xFF) {
                throw new CapturaNoAdmitidaException("El JPEG tiene un segmento danado.");
            }
            // Bytes de relleno (FF) antes del marcador. Siempre para antes de
            // `fin`: el ultimo byte es el D9 del final, comprobado arriba.
            while ((bytes[posicion] & 0xFF) == 0xFF) {
                posicion++;
            }
            if (posicion + 3 > fin) {
                throw new CapturaNoAdmitidaException("El JPEG esta cortado.");
            }
            int marcador = bytes[posicion] & 0xFF;
            posicion++;
            if (marcador == 0xD9 || marcador == 0x01 || (marcador >= 0xD0 && marcador <= 0xD7) || marcador == 0xD8) {
                throw new CapturaNoAdmitidaException("El JPEG no tiene datos de imagen.");
            }
            int largo = ((bytes[posicion] & 0xFF) << 8) | (bytes[posicion + 1] & 0xFF);
            if (largo < 2 || posicion + largo > fin) {
                throw new CapturaNoAdmitidaException("El JPEG esta cortado.");
            }
            if (marcador == 0xDA) {
                if (!hayCuadro) {
                    throw new CapturaNoAdmitidaException("El JPEG no describe su imagen antes de sus datos.");
                }
                salida.write(0xFF);
                salida.write(marcador);
                salida.write(bytes, posicion, fin - posicion);
                return salida.toByteArray();
            }
            hayCuadro |= esInicioDeCuadro(marcador);
            if (seQueda(marcador, bytes, posicion + 2, largo - 2)) {
                salida.write(0xFF);
                salida.write(marcador);
                salida.write(bytes, posicion, largo);
            }
            posicion += largo;
        }
    }

    // SOF0..SOF15 salvo DHT (C4), JPG (C8) y DAC (CC).
    private static boolean esInicioDeCuadro(int marcador) {
        return marcador >= 0xC0 && marcador <= 0xCF && marcador != 0xC4 && marcador != 0xC8 && marcador != 0xCC;
    }

    // Lo que hace falta para ver la imagen: tablas, cuadro, reinicio, JFIF,
    // el perfil de color y Adobe (transformacion de color). Fuera: EXIF y XMP
    // (APP1), IPTC (APP13), comentarios (FE) y los demas APPn y JPGn.
    private static boolean seQueda(int marcador, byte[] bytes, int datos, int largoDeDatos) {
        if (marcador == 0xE1 || marcador == 0xED || marcador == 0xFE) {
            return false;
        }
        if (marcador == 0xE2) {
            return largoDeDatos >= 12 && new String(bytes, datos, 12, StandardCharsets.US_ASCII).equals("ICC_PROFILE\0");
        }
        if (marcador == 0xE0 || marcador == 0xEE) {
            return true;
        }
        if (marcador >= 0xE0 && marcador <= 0xEF) {
            return false;
        }
        // JPGn (F0-FD): FE ya salio arriba y FF nunca es un marcador.
        return marcador < 0xF0;
    }

    // ------------------------------------------------------ medidas: ImageIO

    /**
     * Ancho y alto con el lector de ImageIO, sin decodificar: {@code getWidth}
     * y {@code getHeight} solo leen la cabecera. En memoria, sin temporales.
     */
    private static Dimensiones dimensionesPorCabecera(byte[] bytes, String formato) {
        // La JVM trae siempre los lectores de PNG y JPEG; sin ellos, esto falla
        // fuera del try (500): no es culpa de la captura.
        ImageReader lector = ImageIO.getImageReadersByFormatName(formato).next();
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            lector.setInput(entrada, true, true);
            return new Dimensiones(lector.getWidth(0), lector.getHeight(0));
        } catch (IOException | RuntimeException ilegible) {
            throw new CapturaNoAdmitidaException("No se pudo leer la cabecera de la imagen.");
        } finally {
            lector.dispose();
        }
    }
}
