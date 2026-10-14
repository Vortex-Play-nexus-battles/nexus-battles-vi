package nexus.recursos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import nexus.recursos.RecursoNoAdmitidoException.Motivo;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Examinador de recursos gráficos (HU-PRD-009)")
class ExaminadorDeRecursosTest {

    private final ExaminadorDeRecursos examinador = new ExaminadorDeRecursos(LimitesDeRecursos.porOmision());

    private static void rechaza(ThrowingCallable examen, Motivo motivo, String mensaje) {
        assertThatThrownBy(examen)
                .isInstanceOf(RecursoNoAdmitidoException.class)
                .hasMessageContaining(mensaje)
                .extracting(e -> ((RecursoNoAdmitidoException) e).motivo())
                .isEqualTo(motivo);
    }

    @Nested
    @DisplayName("admite")
    class Admite {

        @Test
        @DisplayName("una imagen PNG, JPEG o WebP, con su formato y dimensiones")
        void imagenes() {
            assertThat(examinador.examinar(ArchivosDePrueba.png(320, 200), ClaseDeRecurso.IMAGEN))
                    .isEqualTo(new ExaminadorDeRecursos.RecursoExaminado(FormatoDeRecurso.PNG, 320, 200, false));
            assertThat(examinador.examinar(ArchivosDePrueba.jpeg(64, 48), ClaseDeRecurso.IMAGEN))
                    .isEqualTo(new ExaminadorDeRecursos.RecursoExaminado(FormatoDeRecurso.JPEG, 64, 48, false));
            assertThat(examinador.examinar(ArchivosDePrueba.webpSinPerdida(300, 150), ClaseDeRecurso.IMAGEN))
                    .isEqualTo(new ExaminadorDeRecursos.RecursoExaminado(FormatoDeRecurso.WEBP, 300, 150, false));
            assertThat(examinador.examinar(ArchivosDePrueba.webpExtendido(800, 600, false), ClaseDeRecurso.IMAGEN))
                    .isEqualTo(new ExaminadorDeRecursos.RecursoExaminado(FormatoDeRecurso.WEBP, 800, 600, false));
        }

        @Test
        @DisplayName("un icono PNG o WebP de hasta 512 px")
        void iconos() {
            assertThat(examinador.examinar(ArchivosDePrueba.png(512, 512), ClaseDeRecurso.ICONO).formato())
                    .isEqualTo(FormatoDeRecurso.PNG);
            assertThat(examinador.examinar(ArchivosDePrueba.webpSinPerdida(64, 64), ClaseDeRecurso.ICONO).ancho())
                    .isEqualTo(64);
        }

        @Test
        @DisplayName("una animación GIF de varios fotogramas, un PNG animado o un WebP animado")
        void animaciones() {
            assertThat(examinador.examinar(ArchivosDePrueba.gifAnimado(1, 1), ClaseDeRecurso.ANIMACION))
                    .isEqualTo(new ExaminadorDeRecursos.RecursoExaminado(FormatoDeRecurso.GIF, 1, 1, true));
            assertThat(examinador.examinar(ArchivosDePrueba.pngAnimado(40, 40), ClaseDeRecurso.ANIMACION).animado())
                    .isTrue();
            assertThat(examinador.examinar(ArchivosDePrueba.webpExtendido(120, 90, true), ClaseDeRecurso.ANIMACION))
                    .isEqualTo(new ExaminadorDeRecursos.RecursoExaminado(FormatoDeRecurso.WEBP, 120, 90, true));
        }
    }

    @Nested
    @DisplayName("rechaza diciendo el requisito incumplido")
    class Rechaza {

        @Test
        @DisplayName("sin archivo o sin clase")
        void sinArchivoNiClase() {
            rechaza(() -> examinador.examinar(new byte[0], ClaseDeRecurso.IMAGEN),
                    Motivo.ARCHIVO_AUSENTE, "No llegó ningún archivo");
            rechaza(() -> examinador.examinar(null, ClaseDeRecurso.IMAGEN),
                    Motivo.ARCHIVO_AUSENTE, "No llegó ningún archivo");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.png(8, 8), null),
                    Motivo.CLASE_INVALIDA, "una imagen, un icono o una animación");
        }

        @Test
        @DisplayName("un archivo más pesado que el máximo de su clase, con los dos pesos")
        void demasiadoPesado() {
            ExaminadorDeRecursos estrecho = new ExaminadorDeRecursos(new LimitesDeRecursos(
                    new LimitesDeRecursos.Limite(100, 4096),
                    new LimitesDeRecursos.Limite(100, 512),
                    new LimitesDeRecursos.Limite(100, 1024)));
            byte[] png = ArchivosDePrueba.png(64, 64);
            rechaza(() -> estrecho.examinar(png, ClaseDeRecurso.IMAGEN), Motivo.DEMASIADO_GRANDE,
                    "y una imagen puede pesar como máximo 100 bytes");
        }

        @Test
        @DisplayName("lo que no es una imagen, aunque se llame .png")
        void noEsImagen() {
            byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
            rechaza(() -> examinador.examinar(html, ClaseDeRecurso.IMAGEN), Motivo.FORMATO_NO_ADMITIDO,
                    "El archivo no es PNG, JPEG o WebP: se mira su contenido, no su nombre.");
            byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8);
            rechaza(() -> examinador.examinar(svg, ClaseDeRecurso.ICONO), Motivo.FORMATO_NO_ADMITIDO,
                    "El archivo no es PNG o WebP");
        }

        @Test
        @DisplayName("un formato que su clase no admite")
        void formatoDeOtraClase() {
            rechaza(() -> examinador.examinar(ArchivosDePrueba.jpeg(32, 32), ClaseDeRecurso.ICONO),
                    Motivo.FORMATO_NO_ADMITIDO, "Icono admite PNG o WebP; este archivo es JPEG.");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.gifAnimado(1, 1), ClaseDeRecurso.IMAGEN),
                    Motivo.FORMATO_NO_ADMITIDO, "Imagen admite PNG, JPEG o WebP; este archivo es GIF.");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.jpeg(32, 32), ClaseDeRecurso.ANIMACION),
                    Motivo.FORMATO_NO_ADMITIDO, "GIF, PNG animado (APNG) o WebP animado");
        }

        @Test
        @DisplayName("un archivo cortado o con algo pegado detrás")
        void danado() {
            byte[] png = ArchivosDePrueba.png(16, 16);
            rechaza(() -> examinador.examinar(ArchivosDePrueba.cortado(png, 5), ClaseDeRecurso.IMAGEN),
                    Motivo.ARCHIVO_DANADO, "El archivo está dañado");
            rechaza(() -> examinador.examinar(
                            ArchivosDePrueba.con(png, "<html></html>".getBytes(StandardCharsets.UTF_8)),
                            ClaseDeRecurso.IMAGEN),
                    Motivo.ARCHIVO_DANADO, "lleva algo pegado detrás");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.cortado(ArchivosDePrueba.jpeg(16, 16), 2),
                            ClaseDeRecurso.IMAGEN),
                    Motivo.ARCHIVO_DANADO, "el JPEG no termina donde debe");
            rechaza(() -> examinador.examinar(
                            ArchivosDePrueba.con(ArchivosDePrueba.webpSinPerdida(8, 8), new byte[] {1, 2}),
                            ClaseDeRecurso.IMAGEN),
                    Motivo.ARCHIVO_DANADO, "no mide lo que dice su cabecera");
            rechaza(() -> examinador.examinar(
                            ArchivosDePrueba.con(ArchivosDePrueba.gifAnimado(1, 1), new byte[] {9}),
                            ClaseDeRecurso.ANIMACION),
                    Motivo.ARCHIVO_DANADO, "el GIF lleva algo pegado detrás");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.cortado(ArchivosDePrueba.gifAnimado(1, 1), 3),
                            ClaseDeRecurso.ANIMACION),
                    Motivo.ARCHIVO_DANADO, "el GIF está cortado");
        }

        @Test
        @DisplayName("un PNG con un bloque corrupto")
        void pngCorrupto() {
            byte[] png = ArchivosDePrueba.png(16, 16);
            png[png.length - 20] ^= 0x5A;
            rechaza(() -> examinador.examinar(png, ClaseDeRecurso.IMAGEN), Motivo.ARCHIVO_DANADO, "corrupto");
        }

        @Test
        @DisplayName("un icono más grande de 512 px, con sus medidas")
        void dimensiones() {
            rechaza(() -> examinador.examinar(ArchivosDePrueba.png(600, 300), ClaseDeRecurso.ICONO),
                    Motivo.DIMENSIONES_EXCEDIDAS, "El archivo mide 600 × 300 px y un icono puede medir como máximo 512 × 512 px.");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.webpExtendido(5000, 10, false), ClaseDeRecurso.IMAGEN),
                    Motivo.DIMENSIONES_EXCEDIDAS, "5000 × 10 px");
        }

        @Test
        @DisplayName("una animación que es una imagen fija, y una imagen que está animada")
        void animacion() {
            rechaza(() -> examinador.examinar(ArchivosDePrueba.gifFijo(10, 10), ClaseDeRecurso.ANIMACION),
                    Motivo.SIN_ANIMACION, "Este GIF es una imagen fija");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.png(10, 10), ClaseDeRecurso.ANIMACION),
                    Motivo.SIN_ANIMACION, "Súbelo como imagen o como icono.");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.pngAnimado(10, 10), ClaseDeRecurso.IMAGEN),
                    Motivo.FORMATO_NO_ADMITIDO, "está animado: súbelo como animación");
            rechaza(() -> examinador.examinar(ArchivosDePrueba.webpExtendido(10, 10, true), ClaseDeRecurso.ICONO),
                    Motivo.FORMATO_NO_ADMITIDO, "está animado");
        }
    }

    @Test
    @DisplayName("los motivos llevan su código de estado y las clases dicen sus formatos en palabras")
    void textos() {
        assertThat(Motivo.ARCHIVO_AUSENTE.estado()).isEqualTo(400);
        assertThat(Motivo.DEMASIADO_GRANDE.estado()).isEqualTo(413);
        assertThat(Motivo.FORMATO_NO_ADMITIDO.estado()).isEqualTo(415);
        assertThat(ClaseDeRecurso.IMAGEN.formatosEnPalabras()).isEqualTo("PNG, JPEG o WebP");
        assertThat(ClaseDeRecurso.ICONO.formatosEnPalabras()).isEqualTo("PNG o WebP");
        assertThat(LimitesDeRecursos.enPalabras(2 * 1024 * 1024)).isEqualTo("2 MB");
        assertThat(LimitesDeRecursos.enPalabras(1536 * 1024)).isEqualTo("1,5 MB");
        assertThat(LimitesDeRecursos.enPalabras(256 * 1024)).isEqualTo("256 KB");
        assertThat(LimitesDeRecursos.enPalabras(1)).isEqualTo("1 byte");
        assertThat(LimitesDeRecursos.porOmision().mayorPeso()).isEqualTo(4 * 1024 * 1024);
        assertThat(FormatoDeRecurso.porTipoMime("image/gif")).isEqualTo(FormatoDeRecurso.GIF);
        assertThatThrownBy(() -> FormatoDeRecurso.porTipoMime("image/svg+xml"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesDeRecursos.Limite(0, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
