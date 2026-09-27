package com.nexusbattles.plataforma.comentarios.imagenes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Decide si unos bytes son una imagen que se puede guardar y servir — contrato
 * 1.4.0 ({@code POST /comentarios/imagenes}), 7.1 del documento del curso.
 *
 * <h2>Que comprueba, en este orden</h2>
 *
 * <ol>
 *   <li><b>Tamano</b>: de 1 byte a 2 MB (413 si se pasa).</li>
 *   <li><b>Firma de bytes</b>: JPEG, PNG o WebP por sus primeros bytes
 *       ({@link TipoDeImagen#porFirma}), nunca por la extension ni por el
 *       {@code Content-Type} declarado (415 si no es ninguno).</li>
 *   <li><b>Estructura completa, sin decodificar</b>: que el archivo termine
 *       donde el formato dice que termina. Un PNG tiene que acabar en su
 *       bloque {@code IEND} con todos los CRC en orden; un JPEG, en su marcador
 *       {@code FF D9}; un WebP tiene que medir exactamente lo que dice su
 *       cabecera RIFF. Es lo que tumba el PNG truncado y el poliglota (una
 *       imagen valida con un HTML o un ZIP pegado detras), que pasarian una
 *       comprobacion que solo mirase la cabecera (415).</li>
 *   <li><b>Dimensiones leidas SOLO de la cabecera</b>: como mucho 4096x4096
 *       (413 si se pasa). Para JPEG y PNG con un {@link ImageReader} de
 *       ImageIO al que se le piden ancho y alto, que lee la cabecera y no
 *       decodifica ni un pixel; para WebP, que ImageIO no trae, se leen a mano
 *       los bloques {@code VP8}, {@code VP8L} y {@code VP8X}.</li>
 * </ol>
 *
 * <p>No decodificar es deliberado: decodificar una imagen hostil es
 * exactamente donde estan los fallos de las bibliotecas de imagen, y una
 * cabecera que miente sobre sus dimensiones es la forma de pedir cientos de
 * megas de memoria con un archivo de pocos kilobytes. Aqui nunca se construye
 * la imagen: solo se leen sus numeros.
 *
 * <p>No es un bean ni toca la red: se prueba con archivos de verdad y falsos
 * sin levantar nada.
 */
public final class ExaminadorDeImagenes {

    /** Contrato 1.4.0: «tamano maximo 2 MB». */
    public static final int TAMANO_MAXIMO = 2 * 1024 * 1024;

    /** Contrato 1.4.0: «como maximo 4096x4096». */
    public static final int DIMENSION_MAXIMA = 4096;

    /** Lo que se sabe de una imagen que paso todas las comprobaciones. */
    public record ImagenExaminada(TipoDeImagen tipo, int ancho, int alto) {
    }

    private record Dimensiones(int ancho, int alto) {
    }

    private final Set<TipoDeImagen> admitidos;

    /**
     * @param admitidos los formatos que la configuracion deja pasar, de entre
     *                  los tres del contrato ({@code COMENTARIOS_FORMATOS_IMAGEN})
     */
    public ExaminadorDeImagenes(Set<TipoDeImagen> admitidos) {
        Objects.requireNonNull(admitidos, "los formatos admitidos son obligatorios");
        if (admitidos.isEmpty()) {
            throw new IllegalArgumentException("hay que admitir al menos un formato de imagen");
        }
        this.admitidos = EnumSet.copyOf(admitidos);
    }

    /**
     * @throws ArchivoAusente         si no hay bytes
     * @throws ImagenDemasiadoGrande  si pesa mas de 2 MB o mide mas de 4096 en algun lado
     * @throws ImagenNoAdmitida       si no es un JPEG, PNG o WebP bien formado y admitido
     */
    public ImagenExaminada examinar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ArchivoAusente();
        }
        if (bytes.length > TAMANO_MAXIMO) {
            throw new ImagenDemasiadoGrande(
                    "la imagen pesa " + bytes.length + " bytes y el maximo es 2 MB");
        }
        TipoDeImagen tipo = TipoDeImagen.porFirma(bytes)
                .orElseThrow(() -> new ImagenNoAdmitida(
                        "el archivo no es una imagen JPEG, PNG ni WebP (se mira su contenido, no su nombre)"));
        if (!admitidos.contains(tipo)) {
            throw new ImagenNoAdmitida("las imagenes " + tipo.tipoMime() + " no estan admitidas");
        }

        Dimensiones dimensiones = switch (tipo) {
            case JPEG -> {
                exigirFinDeJpeg(bytes);
                yield dimensionesPorCabecera(bytes, "jpeg");
            }
            case PNG -> {
                exigirEstructuraPng(bytes);
                yield dimensionesPorCabecera(bytes, "png");
            }
            case WEBP -> dimensionesDeWebp(bytes);
        };

        if (dimensiones.ancho() < 1 || dimensiones.alto() < 1) {
            throw new ImagenNoAdmitida("la cabecera describe una imagen sin pixeles");
        }
        if (dimensiones.ancho() > DIMENSION_MAXIMA || dimensiones.alto() > DIMENSION_MAXIMA) {
            throw new ImagenDemasiadoGrande("la imagen mide " + dimensiones.ancho() + "x" + dimensiones.alto()
                    + " y el maximo es " + DIMENSION_MAXIMA + "x" + DIMENSION_MAXIMA);
        }
        return new ImagenExaminada(tipo, dimensiones.ancho(), dimensiones.alto());
    }

    // ------------------------------------------------------------------ JPEG

    /**
     * Un JPEG termina en el marcador EOI ({@code FF D9}). Se toleran ceros de
     * relleno detras, que algunas camaras escriben; cualquier otra cosa es un
     * archivo cortado o con algo pegado.
     */
    private static void exigirFinDeJpeg(byte[] bytes) {
        int fin = bytes.length;
        while (fin > 0 && bytes[fin - 1] == 0) {
            fin--;
        }
        if (fin < 4 || (bytes[fin - 2] & 0xFF) != 0xFF || (bytes[fin - 1] & 0xFF) != 0xD9) {
            throw new ImagenNoAdmitida("el JPEG no termina donde debe: esta cortado o lleva algo pegado detras");
        }
    }

    // ------------------------------------------------------------------- PNG

    /**
     * Recorre los bloques del PNG sin descomprimir nada: el primero tiene que
     * ser {@code IHDR}, cada uno tiene que caber en el archivo y cuadrar con
     * su CRC, tiene que haber datos ({@code IDAT}) y el archivo tiene que
     * acabar exactamente en {@code IEND}.
     */
    private static void exigirEstructuraPng(byte[] bytes) {
        int posicion = 8;
        boolean primero = true;
        boolean hayDatos = false;
        CRC32 crc = new CRC32();
        while (true) {
            if (posicion + 12 > bytes.length) {
                throw new ImagenNoAdmitida("el PNG esta cortado");
            }
            long largo = enteroGrandeFinal(bytes, posicion);
            if (largo > bytes.length - posicion - 12L) {
                throw new ImagenNoAdmitida("el PNG esta cortado");
            }
            String tipoDeBloque = new String(bytes, posicion + 4, 4, StandardCharsets.US_ASCII);
            if (primero && (!"IHDR".equals(tipoDeBloque) || largo != 13)) {
                throw new ImagenNoAdmitida("el PNG no empieza por su cabecera IHDR");
            }
            crc.reset();
            crc.update(bytes, posicion + 4, 4 + (int) largo);
            if (crc.getValue() != enteroGrandeFinal(bytes, posicion + 8 + (int) largo)) {
                throw new ImagenNoAdmitida("el PNG tiene un bloque corrupto");
            }
            hayDatos |= "IDAT".equals(tipoDeBloque);
            posicion += 12 + (int) largo;
            primero = false;
            if ("IEND".equals(tipoDeBloque)) {
                if (!hayDatos) {
                    throw new ImagenNoAdmitida("el PNG no tiene datos de imagen");
                }
                if (posicion != bytes.length) {
                    throw new ImagenNoAdmitida("el PNG lleva algo pegado detras de su final");
                }
                return;
            }
        }
    }

    // --------------------------------------------------- JPEG y PNG: ImageIO

    /**
     * Ancho y alto con el lector de ImageIO, sin decodificar la imagen:
     * {@code getWidth(0)} y {@code getHeight(0)} solo leen la cabecera
     * ({@code SOFn} en JPEG, {@code IHDR} en PNG). Con memoria y no con disco:
     * {@link MemoryCacheImageInputStream} no escribe temporales.
     */
    private static Dimensiones dimensionesPorCabecera(byte[] bytes, String formato) {
        Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName(formato);
        if (!lectores.hasNext()) {
            throw new IllegalStateException("la JVM no trae lector de " + formato);
        }
        ImageReader lector = lectores.next();
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            lector.setInput(entrada, true, true);
            return new Dimensiones(lector.getWidth(0), lector.getHeight(0));
        } catch (IOException | RuntimeException ilegible) {
            // Las bibliotecas de imagen lanzan de todo ante una cabecera
            // hostil (IIOException, indices fuera de rango...). Para quien sube
            // el archivo es lo mismo: no es una imagen valida.
            throw new ImagenNoAdmitida("la cabecera de la imagen no se puede leer");
        } finally {
            lector.dispose();
        }
    }

    // ------------------------------------------------------------------ WebP

    /**
     * Las dimensiones de un WebP a partir de su primer bloque:
     *
     * <ul>
     *   <li>{@code VP8X} (extendido): lienzo de 24 bits menos uno, en los
     *       bytes 24-26 (ancho) y 27-29 (alto).</li>
     *   <li>{@code VP8L} (sin perdida): tras la firma {@code 0x2F}, 14 bits de
     *       ancho menos uno y 14 de alto menos uno.</li>
     *   <li>{@code VP8 } (con perdida): tras la cabecera de fotograma clave y el
     *       codigo {@code 9D 01 2A}, 14 bits de ancho y 14 de alto.</li>
     * </ul>
     *
     * <p>Antes se exige que el tamano que declara la cabecera RIFF sea el del
     * archivo: ni cortado ni con nada pegado detras.
     */
    private static Dimensiones dimensionesDeWebp(byte[] bytes) {
        if (bytes.length < 30) {
            throw new ImagenNoAdmitida("el WebP esta cortado");
        }
        if (enteroPequenoFinal(bytes, 4) + 8 != bytes.length) {
            throw new ImagenNoAdmitida("el WebP no mide lo que dice su cabecera: esta cortado o lleva algo detras");
        }
        String bloque = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        long largo = enteroPequenoFinal(bytes, 16);
        if (largo > bytes.length - 20L) {
            throw new ImagenNoAdmitida("el WebP esta cortado");
        }
        return switch (bloque) {
            case "VP8X" -> {
                if (largo < 10) {
                    throw new ImagenNoAdmitida("la cabecera VP8X esta incompleta");
                }
                yield new Dimensiones(1 + entero24PequenoFinal(bytes, 24), 1 + entero24PequenoFinal(bytes, 27));
            }
            case "VP8L" -> {
                if (largo < 5 || (bytes[20] & 0xFF) != 0x2F) {
                    throw new ImagenNoAdmitida("la cabecera VP8L no es valida");
                }
                long bits = enteroPequenoFinal(bytes, 21);
                yield new Dimensiones((int) (bits & 0x3FFF) + 1, (int) ((bits >>> 14) & 0x3FFF) + 1);
            }
            case "VP8 " -> {
                if (largo < 10
                        || (bytes[20] & 0x01) != 0
                        || (bytes[23] & 0xFF) != 0x9D || (bytes[24] & 0xFF) != 0x01
                        || (bytes[25] & 0xFF) != 0x2A) {
                    throw new ImagenNoAdmitida("la cabecera VP8 no es valida");
                }
                yield new Dimensiones(entero16PequenoFinal(bytes, 26) & 0x3FFF,
                        entero16PequenoFinal(bytes, 28) & 0x3FFF);
            }
            default -> throw new ImagenNoAdmitida("el WebP no empieza por un bloque de imagen conocido");
        };
    }

    // ---------------------------------------------------------------- bytes

    private static long enteroGrandeFinal(byte[] b, int i) {
        return ((long) (b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    private static long enteroPequenoFinal(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16) | ((long) (b[i + 3] & 0xFF) << 24);
    }

    private static int entero24PequenoFinal(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16);
    }

    private static int entero16PequenoFinal(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
    }
}
