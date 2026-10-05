package com.nexusbattles.plataforma.comentarios.imagenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Que bytes son una imagen que se puede guardar — contrato 1.4.0, B3.
 *
 * <p>Con archivos de verdad (generados con Pillow, en
 * {@code src/test/resources/imagenes}): JPEG normal y progresivo, PNG, y los
 * tres sabores de WebP (con perdida {@code VP8}, sin perdida {@code VP8L} y
 * extendido {@code VP8X}). Y con los falsos que importan: un HTML con
 * extension .png, un PNG truncado, poliglotas (imagen valida con otro
 * documento pegado detras) y cabeceras que prometen dimensiones enormes en
 * pocos bytes.
 */
class ExaminadorDeImagenesTest {

    private static final ExaminadorDeImagenes EXAMINADOR =
            new ExaminadorDeImagenes(EnumSet.allOf(TipoDeImagen.class));

    private static final byte[] HTML = "<html><body><script>alert(1)</script></body></html>"
            .getBytes(StandardCharsets.US_ASCII);

    static byte[] archivo(String nombre) {
        try (InputStream entrada = ExaminadorDeImagenesTest.class.getResourceAsStream("/imagenes/" + nombre)) {
            if (entrada == null) {
                throw new IllegalStateException("falta el archivo de prueba " + nombre);
            }
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] juntar(byte[] a, byte[] b) {
        byte[] junto = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, junto, a.length, b.length);
        return junto;
    }

    @Nested
    @DisplayName("imagenes de verdad")
    class DeVerdad {

        @ParameterizedTest(name = "{0} es {1} de {2}x{3}")
        @CsvSource({
                "real.png, PNG, 3, 2",
                "real.jpg, JPEG, 3, 2",
                "real-progresiva.jpg, JPEG, 3, 2",
                "real-con-perdida.webp, WEBP, 3, 2",
                "real-sin-perdida.webp, WEBP, 3, 2",
                "real-extendida.webp, WEBP, 3, 2",
                "limite.png, PNG, 4096, 1",
        })
        void seAceptan(String nombre, TipoDeImagen tipo, int ancho, int alto) {
            ExaminadorDeImagenes.ImagenExaminada examinada = EXAMINADOR.examinar(archivo(nombre));
            assertEquals(tipo, examinada.tipo());
            assertEquals(ancho, examinada.ancho());
            assertEquals(alto, examinada.alto());
        }

        @Test
        @DisplayName("el tipo se decide por la firma, no por la extension: un PNG llamado .jpg sigue siendo PNG")
        void firmaYNoExtension() {
            assertEquals(TipoDeImagen.PNG, TipoDeImagen.porFirma(archivo("real.png")).orElseThrow());
            assertEquals(TipoDeImagen.JPEG, TipoDeImagen.porFirma(archivo("real.jpg")).orElseThrow());
            assertEquals(TipoDeImagen.WEBP, TipoDeImagen.porFirma(archivo("real-sin-perdida.webp")).orElseThrow());
            assertTrue(TipoDeImagen.porFirma(HTML).isEmpty());
            assertTrue(TipoDeImagen.porFirma(null).isEmpty());
            assertTrue(TipoDeImagen.porFirma(new byte[] {(byte) 0xFF}).isEmpty());
        }

        @Test
        @DisplayName("un JPEG con ceros de relleno detras de su final tambien vale (algunas camaras los escriben)")
        void rellenoDeCeros() {
            byte[] conRelleno = juntar(archivo("real.jpg"), new byte[16]);
            assertEquals(TipoDeImagen.JPEG, EXAMINADOR.examinar(conRelleno).tipo());
        }
    }

    @Nested
    @DisplayName("archivos falsos: 415")
    class Falsos {

        @Test
        @DisplayName("un HTML con extension .png no es una imagen")
        void htmlConExtensionPng() {
            ImagenNoAdmitida error = assertThrows(ImagenNoAdmitida.class,
                    () -> EXAMINADOR.examinar(archivo("html-con-extension.png")));
            assertTrue(error.getMessage().contains("no su nombre"));
        }

        @ParameterizedTest(name = "un {0} cortado por la mitad no pasa")
        @ValueSource(strings = {"real.png", "real.jpg", "real-con-perdida.webp", "real-sin-perdida.webp",
                "real-extendida.webp"})
        void truncados(String nombre) {
            byte[] entero = archivo(nombre);
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(Arrays.copyOf(entero, entero.length / 2)));
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(Arrays.copyOf(entero, entero.length - 1)));
        }

        @ParameterizedTest(name = "un {0} con un HTML pegado detras (poliglota) no pasa")
        @ValueSource(strings = {"real.png", "real.jpg", "real-con-perdida.webp", "real-sin-perdida.webp"})
        void poliglotas(String nombre) {
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(juntar(archivo(nombre), HTML)));
        }

        @Test
        @DisplayName("la firma de PNG seguida de un HTML no es un PNG")
        void firmaDePngSinCuerpo() {
            byte[] firma = Arrays.copyOf(archivo("real.png"), 8);
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(juntar(firma, HTML)));
        }

        @Test
        @DisplayName("un PNG con un bloque corrupto (CRC que no cuadra) no pasa")
        void pngCorrupto() {
            byte[] corrupto = archivo("real.png");
            corrupto[20] ^= 0x01; // dentro de los datos de IHDR
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(corrupto));
        }

        @Test
        @DisplayName("un JPEG cuya cabecera no se puede leer no pasa aunque empiece y acabe bien")
        void jpegIlegible() {
            byte[] falso = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 1, 2,
                (byte) 0x99, (byte) 0x99, (byte) 0xFF, (byte) 0xD9};
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(falso));
        }

        @Test
        @DisplayName("un RIFF/WEBP con un bloque desconocido no es un WebP de imagen")
        void webpDesconocido() {
            byte[] webp = archivo("real-sin-perdida.webp");
            webp[12] = 'X';
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(webp));
        }

        @Test
        @DisplayName("un formato que la configuracion no admite se rechaza aunque sea valido")
        void formatoNoAdmitido() {
            ExaminadorDeImagenes soloPng = new ExaminadorDeImagenes(Set.of(TipoDeImagen.PNG));
            assertThrows(ImagenNoAdmitida.class, () -> soloPng.examinar(archivo("real.jpg")));
            assertEquals(TipoDeImagen.PNG, soloPng.examinar(archivo("real.png")).tipo());
        }
    }

    @Nested
    @DisplayName("limites")
    class Limites {

        @Test
        @DisplayName("sin bytes es 400 imagen-ausente")
        void vacio() {
            assertThrows(ArchivoAusente.class, () -> EXAMINADOR.examinar(new byte[0]));
            assertThrows(ArchivoAusente.class, () -> EXAMINADOR.examinar(null));
        }

        @Test
        @DisplayName("mas de 2 MB es 413, antes de mirar nada mas")
        void demasiadoPesada() {
            byte[] grande = Arrays.copyOf(archivo("real.png"), ExaminadorDeImagenes.TAMANO_MAXIMO + 1);
            assertThrows(ImagenDemasiadoGrande.class, () -> EXAMINADOR.examinar(grande));
        }

        @ParameterizedTest(name = "{0}: la cabecera promete mas de 4096 en un lado y es 413 sin decodificarla")
        @ValueSource(strings = {"ancha.png", "alta.jpg", "ancha.webp"})
        void dimensionesExcesivas(String nombre) {
            byte[] bytes = archivo(nombre);
            assertTrue(bytes.length < 2_000, "pocos bytes: el riesgo es la memoria al pintarla, no el peso");
            ImagenDemasiadoGrande error = assertThrows(ImagenDemasiadoGrande.class, () -> EXAMINADOR.examinar(bytes));
            assertTrue(error.getMessage().contains("4097"));
        }

        @Test
        @DisplayName("un VP8X que declara un lienzo de 16384 se rechaza por su cabecera")
        void lienzoWebpEnorme() {
            byte[] webp = archivo("real-extendida.webp");
            // lienzo 24 bits menos uno: 0x3FFF = 16383 -> 16384 de ancho
            webp[24] = (byte) 0xFF;
            webp[25] = (byte) 0x3F;
            webp[26] = 0;
            assertThrows(ImagenDemasiadoGrande.class, () -> EXAMINADOR.examinar(webp));
        }

        @Test
        @DisplayName("un VP8 de 0 pixeles no es una imagen")
        void sinPixeles() {
            byte[] webp = archivo("real-con-perdida.webp");
            webp[26] = 0;
            webp[27] = 0;
            assertThrows(ImagenNoAdmitida.class, () -> EXAMINADOR.examinar(webp));
        }
    }

    @Test
    @DisplayName("la configuracion: nombres conocidos, desconocidos ignorados y nunca ninguno")
    void configuracion() {
        assertEquals(EnumSet.of(TipoDeImagen.JPEG, TipoDeImagen.PNG),
                ServicioDeImagenes.formatosAdmitidos(java.util.List.of("JPG", " png ", "gif", "")));
        assertEquals(EnumSet.of(TipoDeImagen.JPEG), ServicioDeImagenes.formatosAdmitidos(java.util.List.of("jpeg")));
        assertThrows(IllegalStateException.class,
                () -> ServicioDeImagenes.formatosAdmitidos(java.util.List.of("gif", "bmp")));
        assertThrows(IllegalArgumentException.class, () -> new ExaminadorDeImagenes(Set.of()));
        assertEquals(TipoDeImagen.WEBP, TipoDeImagen.porTipoMime("image/webp"));
        assertThrows(IllegalArgumentException.class, () -> TipoDeImagen.porTipoMime("image/gif"));
    }
}
