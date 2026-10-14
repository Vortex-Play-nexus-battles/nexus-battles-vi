package com.nexusbattles.ms_chatbot.chat.soporte;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.bloque;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.conBloquesTrasLaCabecera;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.conSegmentosTrasElInicio;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.contiene;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.imagen;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.leer;
import static com.nexusbattles.ms_chatbot.chat.soporte.ImagenesDePrueba.segmento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ms-chatbot.yaml 1.14.0 (§7.4.6): que capturas se guardan, y sin metadatos.
// Con imagenes de verdad hechas aqui con ImageIO, y con las trampas de siempre.
class ExaminadorDeCapturasTest {

    private final ExaminadorDeCapturas examinador = new ExaminadorDeCapturas();

    // ------------------------------------------------------------------ PNG

    @Test
    void unPngDeVerdadPasaConSusMedidas() throws IOException {
        ExaminadorDeCapturas.CapturaExaminada examinada = examinador.examinar(imagen("png", 640, 360));

        assertThat(examinada.formato()).isEqualTo(ExaminadorDeCapturas.Formato.PNG);
        assertThat(examinada.formato().tipoMime()).isEqualTo("image/png");
        assertThat(examinada.ancho()).isEqualTo(640);
        assertThat(examinada.alto()).isEqualTo(360);
        assertThat(leer(examinada.contenido()).getWidth()).isEqualTo(640);
    }

    @Test
    void alPngSeLeQuitanElTextoElExifYLaFechaPeroSeVeIgual() throws IOException {
        byte[] png = conBloquesTrasLaCabecera(imagen("png", 32, 16),
            bloque("tEXt", "Author\0Simon en Bucaramanga".getBytes(StandardCharsets.ISO_8859_1)),
            bloque("eXIf", "MM\0*GPSLatitude 7.12".getBytes(StandardCharsets.ISO_8859_1)),
            bloque("tIME", new byte[] {0x07, (byte) 0xEA, 10, 14, 12, 0, 0}),
            bloque("pHYs", new byte[] {0, 0, 0x0B, 0x13, 0, 0, 0x0B, 0x13, 1}));

        ExaminadorDeCapturas.CapturaExaminada examinada = examinador.examinar(png);

        assertThat(contiene(examinada.contenido(), "Bucaramanga")).isFalse();
        assertThat(contiene(examinada.contenido(), "GPSLatitude")).isFalse();
        assertThat(contiene(examinada.contenido(), "tIME")).isFalse();
        // Lo que hace falta para verla se queda.
        assertThat(contiene(examinada.contenido(), "pHYs")).isTrue();
        assertThat(examinada.contenido().length).isLessThan(png.length);
        assertThat(leer(examinada.contenido()).getHeight()).isEqualTo(16);
    }

    @Test
    void unPngConAlgoPegadoDetrasOCortadoNoPasa() throws IOException {
        byte[] png = imagen("png", 8, 8);
        byte[] poliglota = Arrays.copyOf(png, png.length + 15);
        System.arraycopy("<script>x</script>".getBytes(StandardCharsets.US_ASCII), 0, poliglota, png.length, 15);

        assertThatThrownBy(() -> examinador.examinar(poliglota))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("pegado");
        assertThatThrownBy(() -> examinador.examinar(Arrays.copyOf(png, png.length - 20)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cortado");
    }

    @Test
    void unPngConUnBloqueDanadoOSinCabeceraOSinDatosNoPasa() throws IOException {
        byte[] png = imagen("png", 8, 8);
        byte[] danado = png.clone();
        danado[40] ^= 0x55;
        assertThatThrownBy(() -> examinador.examinar(danado)).isInstanceOf(CapturaNoAdmitidaException.class);

        byte[] firma = Arrays.copyOf(png, 8);
        byte[] sinCabecera = new byte[8 + 12 + 12];
        System.arraycopy(firma, 0, sinCabecera, 0, 8);
        System.arraycopy(bloque("IDAT", new byte[0]), 0, sinCabecera, 8, 12);
        System.arraycopy(bloque("IEND", new byte[0]), 0, sinCabecera, 20, 12);
        assertThatThrownBy(() -> examinador.examinar(sinCabecera))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cabecera");

        // IHDR y IEND, sin IDAT.
        ByteArrayOutputStream sinDatos = new ByteArrayOutputStream();
        sinDatos.write(png, 0, 33);
        sinDatos.writeBytes(bloque("IEND", new byte[0]));
        assertThatThrownBy(() -> examinador.examinar(sinDatos.toByteArray()))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("datos");
    }

    @Test
    void unPngDeMasDe4096PixelesNoPasaAunquePeseMuyPoco() throws IOException {
        byte[] ancho = imagen("png", 4097, 1);

        assertThat(ancho.length).isLessThan(ExaminadorDeCapturas.TAMANO_MAXIMO);
        assertThatThrownBy(() -> examinador.examinar(ancho)).isInstanceOf(CapturaDemasiadoGrandeException.class);
    }

    // ----------------------------------------------------------------- JPEG

    @Test
    void unJpegDeVerdadPasaYSeLeQuitanElExifElXmpYLosComentarios() throws IOException {
        byte[] jpeg = conSegmentosTrasElInicio(imagen("jpg", 120, 80),
            segmento(0xE1, "Exif\0\0MM GPSLatitude 7.1193".getBytes(StandardCharsets.ISO_8859_1)),
            segmento(0xE1, "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>Simon</x:xmpmeta>"
                .getBytes(StandardCharsets.ISO_8859_1)),
            segmento(0xED, "Photoshop 3.0\0IPTC Bucaramanga".getBytes(StandardCharsets.ISO_8859_1)),
            segmento(0xFE, "hecho por Simon".getBytes(StandardCharsets.ISO_8859_1)),
            segmento(0xE5, "proveedor".getBytes(StandardCharsets.ISO_8859_1)),
            segmento(0xE2, "FPXR\0datos".getBytes(StandardCharsets.ISO_8859_1)),
            segmento(0xE2, "ICC_PROFILE\0\1\1perfil".getBytes(StandardCharsets.ISO_8859_1)));

        ExaminadorDeCapturas.CapturaExaminada examinada = examinador.examinar(jpeg);

        assertThat(examinada.formato()).isEqualTo(ExaminadorDeCapturas.Formato.JPEG);
        assertThat(examinada.ancho()).isEqualTo(120);
        assertThat(examinada.alto()).isEqualTo(80);
        for (String rastro : new String[] {"GPSLatitude", "xmpmeta", "Bucaramanga", "hecho por", "proveedor", "FPXR"}) {
            assertThat(contiene(examinada.contenido(), rastro)).as(rastro).isFalse();
        }
        // El perfil de color y JFIF se quedan.
        assertThat(contiene(examinada.contenido(), "ICC_PROFILE")).isTrue();
        assertThat(contiene(examinada.contenido(), "JFIF")).isTrue();
        assertThat(leer(examinada.contenido()).getWidth()).isEqualTo(120);
    }

    @Test
    void unJpegConCerosDeRellenoPasaYConAlgoPegadoDetrasNo() throws IOException {
        byte[] jpeg = imagen("jpg", 16, 16);

        assertThat(examinador.examinar(Arrays.copyOf(jpeg, jpeg.length + 4)).ancho()).isEqualTo(16);
        byte[] poliglota = Arrays.copyOf(jpeg, jpeg.length + 4);
        System.arraycopy("PK\3\4".getBytes(StandardCharsets.ISO_8859_1), 0, poliglota, jpeg.length, 4);
        assertThatThrownBy(() -> examinador.examinar(poliglota))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("pegado");
    }

    @Test
    void unJpegSinImagenOConUnSegmentoMalNoPasa() throws IOException {
        // Inicio y fin, nada en medio.
        assertThatThrownBy(() -> examinador.examinar(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
            (byte) 0xD9})).isInstanceOf(CapturaNoAdmitidaException.class);
        // Un segmento cuyo largo se sale del archivo.
        byte[] jpeg = imagen("jpg", 8, 8);
        byte[] largo = conSegmentosTrasElInicio(jpeg, new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) 0x7F, (byte) 0xFF});
        assertThatThrownBy(() -> examinador.examinar(largo)).isInstanceOf(CapturaNoAdmitidaException.class);
        // Los datos de la imagen antes de describirla (sin SOF).
        byte[] sinCuadro = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 2, 1, 2, (byte) 0xFF,
            (byte) 0xD9};
        assertThatThrownBy(() -> examinador.examinar(sinCuadro))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("antes de sus datos");
        // Otro inicio en medio.
        byte[] dosInicios = conSegmentosTrasElInicio(jpeg, new byte[] {(byte) 0xFF, (byte) 0xD8});
        assertThatThrownBy(() -> examinador.examinar(dosInicios)).isInstanceOf(CapturaNoAdmitidaException.class);
    }

    // --------------------------------------------- los bordes de cada formato

    private static byte[] bytes(int... valores) {
        byte[] salida = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            salida[i] = (byte) valores[i];
        }
        return salida;
    }

    // Cambia ancho y alto del IHDR de un PNG y le recalcula el CRC.
    private static byte[] conMedidas(byte[] png, int ancho, int alto) {
        byte[] copia = png.clone();
        for (int i = 0; i < 4; i++) {
            copia[16 + i] = (byte) (ancho >>> (24 - 8 * i));
            copia[20 + i] = (byte) (alto >>> (24 - 8 * i));
        }
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(copia, 12, 17);
        long valor = crc.getValue();
        for (int i = 0; i < 4; i++) {
            copia[29 + i] = (byte) (valor >>> (24 - 8 * i));
        }
        return copia;
    }

    @Test
    void unPngAltoDeMasDe4096OSinPixelesNoPasa() {
        byte[] png = imagen("png", 10, 10);

        assertThatThrownBy(() -> examinador.examinar(conMedidas(png, 10, 4097)))
            .isInstanceOf(CapturaDemasiadoGrandeException.class).hasMessageContaining("4096");
        // Una cabecera de cero pixeles la rechaza el propio lector.
        assertThatThrownBy(() -> examinador.examinar(conMedidas(png, 0, 10)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cabecera");
    }

    @Test
    void unPngQueSeAcabaTrasLaCabeceraOConUnaCabeceraDeOtroLargoNoPasa() {
        byte[] png = imagen("png", 8, 8);

        assertThatThrownBy(() -> examinador.examinar(Arrays.copyOf(png, 33)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cortado");
        ByteArrayOutputStream cabeceraLarga = new ByteArrayOutputStream();
        cabeceraLarga.write(png, 0, 8);
        cabeceraLarga.writeBytes(bloque("IHDR", Arrays.copyOfRange(png, 16, 30)));
        cabeceraLarga.write(png, 33, png.length - 33);
        assertThatThrownBy(() -> examinador.examinar(cabeceraLarga.toByteArray()))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cabecera");
    }

    @Test
    void laFirmaTieneQueEstarEntera() {
        assertThat(ExaminadorDeCapturas.porFirma(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0B))).isEmpty();
        assertThat(ExaminadorDeCapturas.porFirma(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ExaminadorDeCapturas.porFirma(bytes(0x00, 0xD8, 0xFF))).isEmpty();
        assertThat(ExaminadorDeCapturas.porFirma(bytes(0xFF, 0xD9, 0xFF))).isEmpty();
        assertThat(ExaminadorDeCapturas.porFirma(bytes(0xFF, 0xD8, 0x00))).isEmpty();
        assertThat(ExaminadorDeCapturas.porFirma(bytes(0xFF, 0xD8, 0xFF))).contains(ExaminadorDeCapturas.Formato.JPEG);
    }

    @Test
    void unJpegQueNoAcabaEnSuMarcadorDeFinNoPasa() {
        byte[] jpeg = imagen("jpg", 8, 8);
        byte[] sinFin = jpeg.clone();
        sinFin[sinFin.length - 2] = 0x00;

        assertThatThrownBy(() -> examinador.examinar(sinFin))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("no termina");
        assertThatThrownBy(() -> examinador.examinar(bytes(0xFF, 0xD8, 0xFF)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("no termina");
        // Solo ceros: ni siquiera queda un final que mirar.
        assertThatThrownBy(() -> ExaminadorDeCapturas.jpegSinMetadatos(new byte[3]))
            .isInstanceOf(CapturaNoAdmitidaException.class);
    }

    @Test
    void unJpegConMarcadoresSinLargoAntesDeLaImagenNoPasa() {
        // Fin (D9), TEM (01) y reinicio (D3) antes de los datos: no hay imagen.
        for (int marcador : new int[] {0xD9, 0x01, 0xD3}) {
            assertThatThrownBy(() -> examinador.examinar(bytes(0xFF, 0xD8, 0xFF, marcador, 0, 0, 0xFF, 0xD9)))
                .as("marcador %02X", marcador)
                .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("datos de imagen");
        }
    }

    @Test
    void unJpegConSegmentosMalFormadosNoPasa() {
        // Algo que no es un marcador entre segmentos.
        assertThatThrownBy(() -> examinador.examinar(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00,
            0x11, 0x22, 0x33, 0x44, 0xFF, 0xD9)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("danado");
        // Relleno hasta el final, sin sitio para un segmento.
        assertThatThrownBy(() -> examinador.examinar(bytes(0xFF, 0xD8, 0xFF, 0xFF, 0xFF, 0xD9)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cortado");
        // Un largo que no cuenta ni sus propios dos bytes.
        assertThatThrownBy(() -> examinador.examinar(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x01, 0x00, 0xFF, 0xD9)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("cortado");
    }

    @Test
    void niLaTablaAritmeticaNiUnMarcadorReservadoCuentanComoCuadro() {
        // DAC (CC), JPG (C8) y uno reservado (02) se copian, pero no describen la
        // imagen: los datos que siguen no tienen cuadro.
        byte[] sinCuadro = bytes(0xFF, 0xD8,
            0xFF, 0xCC, 0x00, 0x04, 0x00, 0x00,
            0xFF, 0xC8, 0x00, 0x04, 0x00, 0x00,
            0xFF, 0x02, 0x00, 0x04, 0x00, 0x00,
            0xFF, 0xDA, 0x00, 0x02, 0x01, 0x02,
            0xFF, 0xD9);

        assertThatThrownBy(() -> examinador.examinar(sinCuadro))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("antes de sus datos");
    }

    @Test
    void deUnJpegSeQuedanAdobeYElPerfilDeColorYSeVanLosJpgnYUnApp2Corto() {
        byte[] jpeg = conSegmentosTrasElInicio(imagen("jpg", 24, 12),
            segmento(0xEE, bytes('A', 'd', 'o', 'b', 'e', 0, 100, 0, 0, 0, 0, 1)),
            segmento(0xE2, "ICC".getBytes(StandardCharsets.US_ASCII)),
            segmento(0xF0, "extension uno".getBytes(StandardCharsets.US_ASCII)),
            segmento(0xFD, "extension trece".getBytes(StandardCharsets.US_ASCII)));

        ExaminadorDeCapturas.CapturaExaminada examinada = examinador.examinar(jpeg);

        assertThat(contiene(examinada.contenido(), "Adobe")).isTrue();
        assertThat(contiene(examinada.contenido(), "extension")).isFalse();
        assertThat(contiene(examinada.contenido(), "ICC")).isFalse();
        assertThat(examinada.ancho()).isEqualTo(24);
        assertThat(examinada.alto()).isEqualTo(12);
    }

    // ------------------------------------------------------------ lo demas

    @Test
    void loQueNoEsPngNiJpegNoPasaPorMuchoQueSeLlame() throws IOException {
        assertThatThrownBy(() -> examinador.examinar(imagen("gif", 4, 4)))
            .isInstanceOf(CapturaNoAdmitidaException.class).hasMessageContaining("PNG o JPEG");
        assertThatThrownBy(() -> examinador.examinar("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(CapturaNoAdmitidaException.class);
        assertThatThrownBy(() -> examinador.examinar(new byte[] {(byte) 0x89, 'P'}))
            .isInstanceOf(CapturaNoAdmitidaException.class);
    }

    @Test
    void vacioODeMasDe2MbNoPasa() {
        assertThatThrownBy(() -> examinador.examinar(null)).isInstanceOf(CapturaNoAdmitidaException.class);
        assertThatThrownBy(() -> examinador.examinar(new byte[0])).isInstanceOf(CapturaNoAdmitidaException.class);
        assertThatThrownBy(() -> examinador.examinar(new byte[ExaminadorDeCapturas.TAMANO_MAXIMO + 1]))
            .isInstanceOf(CapturaDemasiadoGrandeException.class);
    }

    @Test
    void elFormatoSeBuscaPorSuTipo() {
        assertThat(ExaminadorDeCapturas.Formato.porTipoMime("image/jpeg")).contains(ExaminadorDeCapturas.Formato.JPEG);
        assertThat(ExaminadorDeCapturas.Formato.JPEG.extension()).isEqualTo("jpg");
        assertThat(ExaminadorDeCapturas.Formato.porTipoMime("image/gif")).isEmpty();
    }
}
