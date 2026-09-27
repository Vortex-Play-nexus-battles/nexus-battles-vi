package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La funcion de normalizacion de la lista negra (moderacion-lista-negra.yaml
 * 2.0.x): texto y termino pasan por ella antes de comparar, asi que cada paso
 * se prueba por separado y despues todos juntos.
 */
@DisplayName("NormalizadorDeTexto · la misma forma para el texto y para el termino")
class NormalizadorDeTextoTest {

    private static String compacta(String texto) {
        return NormalizadorDeTexto.normalizar(texto).compacta();
    }

    @Nested
    @DisplayName("forma compacta")
    class FormaCompacta {

        @ParameterizedTest(name = "«{0}» -> spiderman")
        @ValueSource(strings = {
                "spiderman", "Spiderman", "SPIDERMAN", "spider-man", "spider man", "sp1derman",
                "$piderman", "spíderman", "spider_man", "s.p.i.d.e.r.m.a.n", "Spider-Man", "SP1D3RM4N",
                "5p1d3rm4n", "sp1derm@n", "ｓｐｉｄｅｒｍａｎ", "spi​derman", "spi­der⁠man",
                "spiiiiderman", "spi-i-i-derman", "SPÍDÊRMÀN", "\tspider\nman "
        })
        void todasLasVariantesDanLaMismaForma(String variante) {
            assertThat(compacta(variante)).isEqualTo("spiderman");
        }

        @Test
        @DisplayName("NFKC: ligaduras, anchos completos y letras compuestas quedan en ASCII")
        void nfkc() {
            assertThat(compacta("ﬁesta")).isEqualTo("fiesta");
            assertThat(compacta("ＢＡＴＭＡＮ")).isEqualTo("batman");
            assertThat(compacta("Ⓑatman")).isEqualTo("batman");
        }

        @Test
        @DisplayName("minusculas con Locale.ROOT: la I con punto turca no deja una marca suelta")
        void minusculasSinLocale() {
            assertThat(compacta("İNDIO")).isEqualTo("indio");
        }

        @Test
        @DisplayName("solo el leetspeak del contrato: 0 1 3 4 5 7 @ $; el resto de cifras se queda")
        void leetspeakControlado() {
            assertThat(compacta("0134578@$9")).isEqualTo("oieast8as9");
        }

        @Test
        @DisplayName("una racha de tres o mas letras iguales queda en una; una doble se respeta")
        void rachas() {
            assertThat(compacta("holaaaa")).isEqualTo("hola");
            assertThat(compacta("zorro")).isEqualTo("zorro");
            assertThat(compacta("perrrro")).isEqualTo("pero");
            assertThat(compacta("999")).as("las cifras que no son leetspeak no son letras").isEqualTo("999");
        }

        @Test
        @DisplayName("sin separadores de ningun tipo, ni emojis, ni controles")
        void separadores() {
            assertThat(compacta("el · mejor — jugador ★ 🙂")).isEqualTo("elmejorjugador");
            assertThat(compacta("a\u0000b\u0007c")).isEqualTo("abc");
        }

        @Test
        @DisplayName("vacio, nulo o solo simbolos da cadena vacia")
        void vacio() {
            assertThat(compacta("")).isEmpty();
            assertThat(compacta("   ")).isEmpty();
            assertThat(compacta("!!! ...")).isEmpty();
            assertThat(NormalizadorDeTexto.normalizar(null).compacta()).isEmpty();
        }
    }

    @Nested
    @DisplayName("palabras")
    class Palabras {

        @Test
        @DisplayName("se cortan por los separadores y cada una pasa por el mismo proceso")
        void cortes() {
            List<NormalizadorDeTexto.Palabra> palabras = NormalizadorDeTexto.normalizar("Te 0dio, PUT4!").palabras();
            assertThat(palabras).extracting(NormalizadorDeTexto.Palabra::completa)
                    .containsExactly("te", "odio", "puta");
        }

        @Test
        @DisplayName("las cifras del principio o del final cuentan tambien sin ellas (Messi10 es la palabra messi)")
        void cifrasEnLosBordes() {
            NormalizadorDeTexto.Palabra palabra = NormalizadorDeTexto.normalizar("Messi10").palabras().get(0);
            assertThat(palabra.completa()).isEqualTo("messiio");
            assertThat(palabra.sinCifrasEnLosBordes()).isEqualTo("messi");
            assertThat(palabra.es("messi")).isTrue();
            assertThat(palabra.es("messiio")).isTrue();
            assertThat(palabra.es("mess")).isFalse();
        }

        @Test
        @DisplayName("una cifra entre letras es leetspeak, no un corte: m3ss1 es messi completo")
        void cifrasEnMedio() {
            NormalizadorDeTexto.Palabra palabra = NormalizadorDeTexto.normalizar("m3ss1").palabras().get(0);
            assertThat(palabra.completa()).isEqualTo("messi");
            assertThat(palabra.es("messi")).isTrue();
        }

        @Test
        @DisplayName("una palabra que solo son cifras no deja variante vacia")
        void soloCifras() {
            NormalizadorDeTexto.Palabra palabra = NormalizadorDeTexto.normalizar("2026").palabras().get(0);
            assertThat(palabra.completa()).isEqualTo("2o26");
            assertThat(palabra.sinCifrasEnLosBordes()).isNull();
        }

        @Test
        @DisplayName("un texto sin letras ni cifras no tiene palabras")
        void sinPalabras() {
            assertThat(NormalizadorDeTexto.normalizar("¡¿!?").palabras()).isEmpty();
        }
    }

    @Test
    @DisplayName("limite conocido y documentado: los homoglifos cirilicos no se traducen")
    void homoglifosCirilicosNoCubiertos() {
        // «ѕріdеrmаn» con ѕ, і, е, а cirilicas: NFKC no las lleva al alfabeto
        // latino. Si algun dia se cubren, esta prueba es la que cambia.
        assertThat(compacta("ѕріdеrmаn")).isNotEqualTo("spiderman");
    }
}
