package com.nexusbattles.ms_chatbot.chat.soporte;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * ms-chatbot.yaml 1.14.0 (§7.4.6): imagenes de verdad, hechas con ImageIO,
 * para las pruebas de las capturas de soporte, y las piezas con las que se les
 * meten metadatos (bloques PNG, segmentos JPEG) o se les pega otro archivo.
 */
public final class ImagenesDePrueba {

    private ImagenesDePrueba() {
        // Utilidad de pruebas.
    }

    /** Una imagen de un color con un punto azul, en "png" o "jpg". */
    public static byte[] imagen(String formato, int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        imagen.setRGB(0, 0, 0x1E3F88);
        return escribir(imagen, formato);
    }

    /** Un PNG de ruido, que casi no se comprime: para probar los limites de tamano. */
    public static byte[] pngDeRuido(int ancho, int alto, long semilla) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Random azar = new Random(semilla);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                imagen.setRGB(x, y, azar.nextInt(0x1000000));
            }
        }
        return escribir(imagen, "png");
    }

    /** Un bloque PNG con su CRC. */
    public static byte[] bloque(String tipo, byte[] datos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(new byte[] {(byte) (datos.length >>> 24), (byte) (datos.length >>> 16),
            (byte) (datos.length >>> 8), (byte) datos.length});
        byte[] tipoYDatos = new byte[4 + datos.length];
        System.arraycopy(tipo.getBytes(StandardCharsets.US_ASCII), 0, tipoYDatos, 0, 4);
        System.arraycopy(datos, 0, tipoYDatos, 4, datos.length);
        salida.writeBytes(tipoYDatos);
        CRC32 crc = new CRC32();
        crc.update(tipoYDatos);
        long valor = crc.getValue();
        salida.writeBytes(new byte[] {(byte) (valor >>> 24), (byte) (valor >>> 16), (byte) (valor >>> 8),
            (byte) valor});
        return salida.toByteArray();
    }

    /** Mete bloques justo despues de IHDR (8 de firma + 25 del IHDR). */
    public static byte[] conBloquesTrasLaCabecera(byte[] png, byte[]... bloques) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(png, 0, 33);
        for (byte[] bloque : bloques) {
            salida.writeBytes(bloque);
        }
        salida.write(png, 33, png.length - 33);
        return salida.toByteArray();
    }

    /** Un segmento JPEG: marcador y largo (que cuenta sus dos bytes). */
    public static byte[] segmento(int marcador, byte[] datos) {
        int largo = datos.length + 2;
        byte[] salida = new byte[4 + datos.length];
        salida[0] = (byte) 0xFF;
        salida[1] = (byte) marcador;
        salida[2] = (byte) (largo >>> 8);
        salida[3] = (byte) largo;
        System.arraycopy(datos, 0, salida, 4, datos.length);
        return salida;
    }

    /** Mete segmentos justo despues de SOI. */
    public static byte[] conSegmentosTrasElInicio(byte[] jpeg, byte[]... segmentos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpeg, 0, 2);
        for (byte[] segmento : segmentos) {
            salida.writeBytes(segmento);
        }
        salida.write(jpeg, 2, jpeg.length - 2);
        return salida.toByteArray();
    }

    public static boolean contiene(byte[] bytes, String texto) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(texto);
    }

    public static BufferedImage leer(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] escribir(BufferedImage imagen, String formato) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(imagen, formato, salida)) {
                throw new IllegalArgumentException("ImageIO no escribe " + formato);
            }
        } catch (IOException imposible) {
            throw new UncheckedIOException(imposible);
        }
        return salida.toByteArray();
    }
}
