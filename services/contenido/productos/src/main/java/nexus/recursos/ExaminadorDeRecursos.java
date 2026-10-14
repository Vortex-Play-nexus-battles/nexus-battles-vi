package nexus.recursos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import nexus.recursos.RecursoNoAdmitidoException.Motivo;

/**
 * Decide si unos bytes son un recurso gráfico que el catálogo puede guardar y
 * servir (HU-PRD-009, §7.2.3), y de qué clase.
 *
 * <h2>Qué comprueba, en este orden</h2>
 *
 * <ol>
 *   <li><b>Que haya archivo</b>.</li>
 *   <li><b>Peso</b>: el máximo de su clase ({@link LimitesDeRecursos}).</li>
 *   <li><b>Formato por la firma de bytes</b>, no por el nombre ni por el
 *       {@code Content-Type} ({@link FormatoDeRecurso#porFirma}), y que su
 *       clase lo admita.</li>
 *   <li><b>Estructura completa, sin decodificar</b>: el PNG acaba en
 *       {@code IEND} con todos sus CRC; el JPEG, en {@code FF D9}; el WebP mide
 *       lo que dice su cabecera RIFF; el GIF recorre sus bloques hasta el
 *       terminador {@code 3B}. Es lo que tumba un archivo cortado o un
 *       políglota (una imagen válida con un HTML pegado detrás).</li>
 *   <li><b>Dimensiones leídas de la cabecera</b>, sin construir la imagen, y
 *       el lado máximo de su clase.</li>
 *   <li><b>Animación</b>: una animación tiene que estarlo (GIF con más de un
 *       fotograma, PNG con {@code acTL}, WebP con la marca de animación de
 *       {@code VP8X}); una imagen o un icono no pueden estarlo.</li>
 * </ol>
 *
 * <p>No decodificar es deliberado, como en las imágenes de los comentarios:
 * decodificar una imagen hostil es donde están los fallos de las bibliotecas,
 * y una cabecera que miente sobre sus dimensiones pediría cientos de megas con
 * un archivo de pocos kilobytes. Solo se leen sus números.
 *
 * <p>No es un bean ni toca la red: se prueba con archivos de verdad y falsos.
 */
public final class ExaminadorDeRecursos {

    /** Lo que se sabe de un archivo que pasó todas las comprobaciones. */
    public record RecursoExaminado(FormatoDeRecurso formato, int ancho, int alto, boolean animado) {
    }

    private record Lectura(int ancho, int alto, boolean animado) {
    }

    private final LimitesDeRecursos limites;

    public ExaminadorDeRecursos(LimitesDeRecursos limites) {
        this.limites = Objects.requireNonNull(limites);
    }

    /**
     * @throws RecursoNoAdmitidoException con el requisito incumplido
     */
    public RecursoExaminado examinar(byte[] bytes, ClaseDeRecurso clase) {
        if (clase == null) {
            throw new RecursoNoAdmitidoException(Motivo.CLASE_INVALIDA,
                    "Indica si el archivo es una imagen, un icono o una animación.");
        }
        if (bytes == null || bytes.length == 0) {
            throw new RecursoNoAdmitidoException(Motivo.ARCHIVO_AUSENTE, "No llegó ningún archivo.");
        }
        LimitesDeRecursos.Limite limite = limites.de(clase);
        if (bytes.length > limite.bytesMaximos()) {
            throw new RecursoNoAdmitidoException(Motivo.DEMASIADO_GRANDE,
                    "El archivo pesa " + LimitesDeRecursos.enPalabras(bytes.length) + " y "
                            + clase.conArticulo() + " puede pesar como máximo " + limite.pesoEnPalabras() + ".");
        }
        FormatoDeRecurso formato = FormatoDeRecurso.porFirma(bytes)
                .orElseThrow(() -> new RecursoNoAdmitidoException(Motivo.FORMATO_NO_ADMITIDO,
                        "El archivo no es " + clase.formatosEnPalabras()
                                + ": se mira su contenido, no su nombre."));
        if (!clase.admite(formato)) {
            throw new RecursoNoAdmitidoException(Motivo.FORMATO_NO_ADMITIDO,
                    clase.nombre() + " admite " + clase.formatosEnPalabras() + "; este archivo es "
                            + formato.nombre() + ".");
        }

        Lectura lectura = switch (formato) {
            case PNG -> leerPng(bytes);
            case JPEG -> leerJpeg(bytes);
            case WEBP -> leerWebp(bytes);
            case GIF -> leerGif(bytes);
        };

        if (lectura.ancho() < 1 || lectura.alto() < 1) {
            throw danado("la cabecera describe una imagen sin píxeles");
        }
        if (lectura.ancho() > limite.ladoMaximo() || lectura.alto() > limite.ladoMaximo()) {
            throw new RecursoNoAdmitidoException(Motivo.DIMENSIONES_EXCEDIDAS,
                    "El archivo mide " + lectura.ancho() + " × " + lectura.alto() + " px y "
                            + clase.conArticulo() + " puede medir como máximo " + limite.ladoMaximo()
                            + " × " + limite.ladoMaximo() + " px.");
        }
        if (clase == ClaseDeRecurso.ANIMACION && !lectura.animado()) {
            throw new RecursoNoAdmitidoException(Motivo.SIN_ANIMACION,
                    "Este " + formato.nombre() + " es una imagen fija: una animación tiene que tener más de "
                            + "un fotograma. Súbelo como imagen o como icono.");
        }
        if (clase != ClaseDeRecurso.ANIMACION && lectura.animado()) {
            throw new RecursoNoAdmitidoException(Motivo.FORMATO_NO_ADMITIDO,
                    "Este archivo está animado: súbelo como animación.");
        }
        return new RecursoExaminado(formato, lectura.ancho(), lectura.alto(), lectura.animado());
    }

    // ------------------------------------------------------------------- PNG

    /**
     * Recorre los bloques sin descomprimir: el primero es {@code IHDR} (de ahí
     * salen ancho y alto), cada uno cabe en el archivo y cuadra con su CRC, hay
     * datos ({@code IDAT}) y el archivo acaba justo en {@code IEND}. Un bloque
     * {@code acTL} antes del primer {@code IDAT} lo hace un PNG animado.
     */
    private static Lectura leerPng(byte[] bytes) {
        int posicion = 8;
        boolean primero = true;
        boolean hayDatos = false;
        boolean animado = false;
        int ancho = 0;
        int alto = 0;
        CRC32 crc = new CRC32();
        while (true) {
            if (posicion + 12 > bytes.length) {
                throw danado("el PNG está cortado");
            }
            long largo = enteroGrandeFinal(bytes, posicion);
            if (largo > bytes.length - posicion - 12L) {
                throw danado("el PNG está cortado");
            }
            String bloque = new String(bytes, posicion + 4, 4, StandardCharsets.US_ASCII);
            if (primero) {
                if (!"IHDR".equals(bloque) || largo != 13) {
                    throw danado("el PNG no empieza por su cabecera");
                }
                ancho = (int) Math.min(Integer.MAX_VALUE, enteroGrandeFinal(bytes, posicion + 8));
                alto = (int) Math.min(Integer.MAX_VALUE, enteroGrandeFinal(bytes, posicion + 12));
            }
            crc.reset();
            crc.update(bytes, posicion + 4, 4 + (int) largo);
            if (crc.getValue() != enteroGrandeFinal(bytes, posicion + 8 + (int) largo)) {
                throw danado("el PNG tiene un bloque corrupto");
            }
            if ("acTL".equals(bloque) && !hayDatos) {
                animado = true;
            }
            hayDatos |= "IDAT".equals(bloque);
            posicion += 12 + (int) largo;
            primero = false;
            if ("IEND".equals(bloque)) {
                if (!hayDatos) {
                    throw danado("el PNG no tiene datos de imagen");
                }
                if (posicion != bytes.length) {
                    throw danado("el PNG lleva algo pegado detrás de su final");
                }
                return new Lectura(ancho, alto, animado);
            }
        }
    }

    // ------------------------------------------------------------------ JPEG

    /**
     * Termina en {@code FF D9} (se toleran ceros de relleno detrás, que algunas
     * cámaras escriben). Ancho y alto con el lector de ImageIO, que para
     * {@code getWidth(0)} solo lee el marcador {@code SOFn}.
     */
    private static Lectura leerJpeg(byte[] bytes) {
        int fin = bytes.length;
        while (fin > 0 && bytes[fin - 1] == 0) {
            fin--;
        }
        if (fin < 4 || (bytes[fin - 2] & 0xFF) != 0xFF || (bytes[fin - 1] & 0xFF) != 0xD9) {
            throw danado("el JPEG no termina donde debe: está cortado o lleva algo pegado detrás");
        }
        Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName("jpeg");
        if (!lectores.hasNext()) {
            throw new IllegalStateException("la JVM no trae lector de JPEG");
        }
        ImageReader lector = lectores.next();
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            lector.setInput(entrada, true, true);
            return new Lectura(lector.getWidth(0), lector.getHeight(0), false);
        } catch (IOException | RuntimeException ilegible) {
            throw danado("la cabecera del JPEG no se puede leer");
        } finally {
            lector.dispose();
        }
    }

    // ------------------------------------------------------------------ WebP

    /**
     * El tamaño de la cabecera RIFF tiene que ser el del archivo. Las
     * dimensiones salen del primer bloque: {@code VP8X} (lienzo de 24 bits
     * menos uno, y en su byte de marcas la de animación, {@code 0x02}),
     * {@code VP8L} (14 bits menos uno por lado) o {@code VP8 } (14 bits por
     * lado tras el código {@code 9D 01 2A}).
     */
    private static Lectura leerWebp(byte[] bytes) {
        if (bytes.length < 30) {
            throw danado("el WebP está cortado");
        }
        if (enteroPequenoFinal(bytes, 4) + 8 != bytes.length) {
            throw danado("el WebP no mide lo que dice su cabecera: está cortado o lleva algo detrás");
        }
        String bloque = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        long largo = enteroPequenoFinal(bytes, 16);
        if (largo > bytes.length - 20L) {
            throw danado("el WebP está cortado");
        }
        return switch (bloque) {
            case "VP8X" -> {
                if (largo < 10) {
                    throw danado("la cabecera extendida del WebP está incompleta");
                }
                boolean animado = (bytes[20] & 0x02) != 0;
                yield new Lectura(1 + entero24PequenoFinal(bytes, 24), 1 + entero24PequenoFinal(bytes, 27), animado);
            }
            case "VP8L" -> {
                if (largo < 5 || (bytes[20] & 0xFF) != 0x2F) {
                    throw danado("la cabecera del WebP sin pérdida no es válida");
                }
                long bits = enteroPequenoFinal(bytes, 21);
                yield new Lectura((int) (bits & 0x3FFF) + 1, (int) ((bits >>> 14) & 0x3FFF) + 1, false);
            }
            case "VP8 " -> {
                if (largo < 10
                        || (bytes[20] & 0x01) != 0
                        || (bytes[23] & 0xFF) != 0x9D || (bytes[24] & 0xFF) != 0x01
                        || (bytes[25] & 0xFF) != 0x2A) {
                    throw danado("la cabecera del WebP no es válida");
                }
                yield new Lectura(entero16PequenoFinal(bytes, 26) & 0x3FFF,
                        entero16PequenoFinal(bytes, 28) & 0x3FFF, false);
            }
            default -> throw danado("el WebP no empieza por un bloque de imagen conocido");
        };
    }

    // ------------------------------------------------------------------- GIF

    /**
     * Cabecera de 6 bytes, descriptor de pantalla lógica (ancho y alto en los
     * bytes 6-9, y si hay tabla de color global), y después bloques hasta el
     * terminador {@code 3B}: extensiones ({@code 21}) y fotogramas
     * ({@code 2C}), cada uno con sus subbloques. Se cuentan los fotogramas: más
     * de uno es una animación. Nada puede seguir al terminador.
     */
    private static Lectura leerGif(byte[] bytes) {
        if (bytes.length < 14) {
            throw danado("el GIF está cortado");
        }
        int ancho = entero16PequenoFinal(bytes, 6);
        int alto = entero16PequenoFinal(bytes, 8);
        int posicion = 13 + tablaDeColor(bytes[10]);
        int fotogramas = 0;
        while (true) {
            if (posicion >= bytes.length) {
                throw danado("el GIF está cortado");
            }
            int marca = bytes[posicion] & 0xFF;
            if (marca == 0x3B) {
                if (posicion + 1 != bytes.length) {
                    throw danado("el GIF lleva algo pegado detrás de su final");
                }
                if (fotogramas == 0) {
                    throw danado("el GIF no tiene ningún fotograma");
                }
                return new Lectura(ancho, alto, fotogramas > 1);
            }
            if (marca == 0x21) {
                posicion = saltarSubbloques(bytes, posicion + 2);
            } else if (marca == 0x2C) {
                if (posicion + 10 > bytes.length) {
                    throw danado("el GIF está cortado");
                }
                int despuesDelDescriptor = posicion + 10 + tablaDeColor(bytes[posicion + 9]);
                // El byte del tamaño mínimo de código LZW, y los datos en subbloques.
                posicion = saltarSubbloques(bytes, despuesDelDescriptor + 1);
                fotogramas++;
            } else {
                throw danado("el GIF tiene un bloque desconocido");
            }
        }
    }

    /** Bytes de la tabla de color que anuncia un byte de marcas (0 si no hay). */
    private static int tablaDeColor(byte marcas) {
        return (marcas & 0x80) == 0 ? 0 : 3 * (1 << ((marcas & 0x07) + 1));
    }

    /** Salta subbloques (un byte de largo y sus datos) hasta el de largo cero. */
    private static int saltarSubbloques(byte[] bytes, int desde) {
        int posicion = desde;
        while (true) {
            if (posicion >= bytes.length) {
                throw danado("el GIF está cortado");
            }
            int largo = bytes[posicion] & 0xFF;
            posicion += 1 + largo;
            if (largo == 0) {
                return posicion;
            }
        }
    }

    // ---------------------------------------------------------------- bytes

    private static RecursoNoAdmitidoException danado(String detalle) {
        return new RecursoNoAdmitidoException(Motivo.ARCHIVO_DANADO,
                "El archivo está dañado: " + detalle + ".");
    }

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
