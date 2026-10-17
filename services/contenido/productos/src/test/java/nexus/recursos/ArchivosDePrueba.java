package nexus.recursos;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/**
 * Archivos de verdad para las pruebas de recursos: PNG, JPEG y GIF los escribe
 * ImageIO; el GIF animado, el PNG animado (APNG) y los WebP se arman byte a
 * byte con la estructura de su formato, que es lo único que el examinador mira.
 */
final class ArchivosDePrueba {

    private ArchivosDePrueba() {
    }

    static byte[] png(int ancho, int alto) {
        return escribir(imagen(ancho, alto, BufferedImage.TYPE_INT_ARGB), "png");
    }

    static byte[] jpeg(int ancho, int alto) {
        return escribir(imagen(ancho, alto, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    static byte[] gifFijo(int ancho, int alto) {
        return escribir(imagen(ancho, alto, BufferedImage.TYPE_BYTE_INDEXED), "gif");
    }

    /**
     * Un GIF89a de dos fotogramas de 1x1: cabecera, pantalla lógica con tabla
     * de color global de 2 entradas, y dos descriptores de imagen con su dato
     * LZW mínimo.
     */
    static byte[] gifAnimado(int ancho, int alto) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
        salida.writeBytes(new byte[] {
                (byte) ancho, (byte) (ancho >> 8), (byte) alto, (byte) (alto >> 8),
                (byte) 0x80, 0, 0,
                0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        // Extension de aplicacion NETSCAPE2.0 (bucle) con sus subbloques.
        salida.writeBytes(new byte[] {0x21, (byte) 0xFF, 0x0B});
        salida.writeBytes("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));
        salida.writeBytes(new byte[] {0x03, 0x01, 0x00, 0x00, 0x00});
        for (int i = 0; i < 2; i++) {
            // Control grafico (retardo) y fotograma de 1x1.
            salida.writeBytes(new byte[] {0x21, (byte) 0xF9, 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00});
            salida.writeBytes(new byte[] {0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0});
            salida.writeBytes(new byte[] {0x02, 0x02, 0x44, 0x01, 0x00});
        }
        salida.write(0x3B);
        return salida.toByteArray();
    }

    /** El PNG con un bloque {@code acTL} justo después de {@code IHDR}: un APNG. */
    static byte[] pngAnimado(int ancho, int alto) {
        byte[] png = png(ancho, alto);
        int despuesDeIhdr = 8 + 12 + 13;
        byte[] actl = bloque("acTL", new byte[] {0, 0, 0, 2, 0, 0, 0, 0});
        byte[] resultado = new byte[png.length + actl.length];
        System.arraycopy(png, 0, resultado, 0, despuesDeIhdr);
        System.arraycopy(actl, 0, resultado, despuesDeIhdr, actl.length);
        System.arraycopy(png, despuesDeIhdr, resultado, despuesDeIhdr + actl.length, png.length - despuesDeIhdr);
        return resultado;
    }

    /**
     * Un WebP con cabecera extendida {@code VP8X}: lienzo de {@code ancho} x
     * {@code alto} y, si se pide, la marca de animación. Sin datos de imagen:
     * el examinador no decodifica.
     */
    static byte[] webpExtendido(int ancho, int alto, boolean animado) {
        byte[] vp8x = new byte[10];
        vp8x[0] = (byte) (animado ? 0x02 : 0x00);
        escribir24(vp8x, 4, ancho - 1);
        escribir24(vp8x, 7, alto - 1);
        return riff("VP8X", vp8x);
    }

    /** Un WebP sin pérdida ({@code VP8L}) de {@code ancho} x {@code alto}. */
    static byte[] webpSinPerdida(int ancho, int alto) {
        byte[] vp8l = new byte[10];
        vp8l[0] = 0x2F;
        long bits = (ancho - 1L) | ((alto - 1L) << 14);
        vp8l[1] = (byte) bits;
        vp8l[2] = (byte) (bits >> 8);
        vp8l[3] = (byte) (bits >> 16);
        vp8l[4] = (byte) (bits >> 24);
        return riff("VP8L", vp8l);
    }

    static byte[] con(byte[] archivo, byte[] cola) {
        byte[] resultado = Arrays.copyOf(archivo, archivo.length + cola.length);
        System.arraycopy(cola, 0, resultado, archivo.length, cola.length);
        return resultado;
    }

    static byte[] cortado(byte[] archivo, int quitar) {
        return Arrays.copyOf(archivo, archivo.length - quitar);
    }

    private static byte[] riff(String tipo, byte[] datos) {
        int largoBloque = 8 + datos.length + (datos.length % 2);
        int largoRiff = 4 + largoBloque;
        byte[] resultado = new byte[8 + largoRiff];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, resultado, 0, 4);
        escribir32(resultado, 4, largoRiff);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, resultado, 8, 4);
        System.arraycopy(tipo.getBytes(StandardCharsets.US_ASCII), 0, resultado, 12, 4);
        escribir32(resultado, 16, datos.length);
        System.arraycopy(datos, 0, resultado, 20, datos.length);
        return resultado;
    }

    private static byte[] bloque(String tipo, byte[] datos) {
        byte[] resultado = new byte[12 + datos.length];
        resultado[0] = (byte) (datos.length >>> 24);
        resultado[1] = (byte) (datos.length >>> 16);
        resultado[2] = (byte) (datos.length >>> 8);
        resultado[3] = (byte) datos.length;
        System.arraycopy(tipo.getBytes(StandardCharsets.US_ASCII), 0, resultado, 4, 4);
        System.arraycopy(datos, 0, resultado, 8, datos.length);
        CRC32 crc = new CRC32();
        crc.update(resultado, 4, 4 + datos.length);
        long valor = crc.getValue();
        resultado[8 + datos.length] = (byte) (valor >>> 24);
        resultado[9 + datos.length] = (byte) (valor >>> 16);
        resultado[10 + datos.length] = (byte) (valor >>> 8);
        resultado[11 + datos.length] = (byte) valor;
        return resultado;
    }

    private static void escribir24(byte[] b, int i, int valor) {
        b[i] = (byte) valor;
        b[i + 1] = (byte) (valor >> 8);
        b[i + 2] = (byte) (valor >> 16);
    }

    private static void escribir32(byte[] b, int i, int valor) {
        b[i] = (byte) valor;
        b[i + 1] = (byte) (valor >> 8);
        b[i + 2] = (byte) (valor >> 16);
        b[i + 3] = (byte) (valor >> 24);
    }

    private static BufferedImage imagen(int ancho, int alto, int tipo) {
        BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
        for (int x = 0; x < ancho; x++) {
            imagen.setRGB(x, x % alto, 0xFF2244BF);
        }
        return imagen;
    }

    private static byte[] escribir(BufferedImage imagen, String formato) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            if (!ImageIO.write(imagen, formato, salida)) {
                throw new IllegalStateException("ImageIO no escribe " + formato);
            }
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
