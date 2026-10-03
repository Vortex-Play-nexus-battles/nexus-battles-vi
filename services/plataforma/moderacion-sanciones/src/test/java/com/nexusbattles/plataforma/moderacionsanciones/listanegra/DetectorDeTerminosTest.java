package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La coincidencia de la lista negra con los dos modos del contrato 2.0.x.
 *
 * <p>La lista de pruebas es la de la regresion del caso que fallo delante del
 * profesor («spiderman» aceptado como apodo) mas los apodos legitimos que no
 * pueden caer por culpa del arreglo. Los terminos se construyen aqui, en la
 * prueba: ningun termino vive escrito en el codigo de produccion.
 */
@DisplayName("DetectorDeTerminos · SUBCADENA y PALABRA")
class DetectorDeTerminosTest {

    private static TerminoActivo termino(String termino, CategoriaDeTermino categoria) {
        String normalizado = NormalizadorDeTexto.normalizar(termino).compacta();
        return new TerminoActivo(termino, normalizado, categoria, ModoDeCoincidencia.porOmision(normalizado));
    }

    private static TerminoActivo termino(String termino, CategoriaDeTermino categoria, ModoDeCoincidencia modo) {
        return new TerminoActivo(termino, NormalizadorDeTexto.normalizar(termino).compacta(), categoria, modo);
    }

    private static final List<TerminoActivo> LISTA = List.of(
            termino("spiderman", CategoriaDeTermino.MARCA),
            termino("puta", CategoriaDeTermino.OFENSIVO),
            termino("culo", CategoriaDeTermino.OFENSIVO),
            termino("messi", CategoriaDeTermino.CELEBRIDAD, ModoDeCoincidencia.PALABRA),
            termino("stalin", CategoriaDeTermino.DIRIGENTE, ModoDeCoincidencia.PALABRA),
            termino("coca cola", CategoriaDeTermino.MARCA, ModoDeCoincidencia.PALABRA));

    private static List<TerminoActivo> coincidencias(String texto) {
        return DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar(texto), LISTA);
    }

    @Test
    @DisplayName("el modo por omision: SUBCADENA desde 5 caracteres normalizados, PALABRA por debajo")
    void modoPorOmision() {
        assertThat(ModoDeCoincidencia.porOmision("spiderman")).isEqualTo(ModoDeCoincidencia.SUBCADENA);
        assertThat(ModoDeCoincidencia.porOmision("messi")).isEqualTo(ModoDeCoincidencia.SUBCADENA);
        assertThat(ModoDeCoincidencia.porOmision("puta")).isEqualTo(ModoDeCoincidencia.PALABRA);
        assertThat(ModoDeCoincidencia.porOmision("")).isEqualTo(ModoDeCoincidencia.PALABRA);
    }

    @Nested
    @DisplayName("regresion obligatoria: el caso del profesor")
    class CasoSpiderman {

        @ParameterizedTest(name = "rechaza «{0}»")
        @ValueSource(strings = {
                "spiderman", "Spiderman", "SPIDERMAN", "spider-man", "spider man", "sp1derman", "$piderman",
                "spíderman", "spider_man", "s.p.i.d.e.r.m.a.n", "xXspidermanXx", "ElSpiderman2000",
                "ｓｐｉｄｅｒｍａｎ", "spi​derman", "SP1D3RM4N"
        })
        void rechaza(String apodo) {
            assertThat(coincidencias(apodo)).extracting(TerminoActivo::termino).containsExactly("spiderman");
        }

        @ParameterizedTest(name = "acepta «{0}»")
        @ValueSource(strings = {"Valkiria", "ElGuerrero", "Mariposa", "Clasico99", "Spidey", "Arana", "Hombre"})
        void aceptaApodosLegitimos(String apodo) {
            assertThat(coincidencias(apodo)).isEmpty();
        }
    }

    @Nested
    @DisplayName("PALABRA: sin el problema Scunthorpe")
    class Palabra {

        @ParameterizedTest(name = "acepta «{0}»")
        @ValueSource(strings = {
                "computadora", "Mi computadora nueva", "disputa", "reputacion", "vehículo", "cálculo",
                "película", "el mes siguiente", "esta línea", "está lindo", "cocacolas"
        })
        void palabrasNormalesQueContienenUnTerminoCorto(String texto) {
            assertThat(coincidencias(texto)).isEmpty();
        }

        @ParameterizedTest(name = "rechaza «{0}»")
        @ValueSource(strings = {"puta", "PUTA", "eres una puta", "put4", "pvta!", "Puta69", "p.u.t.a", "p u t a"})
        void laPalabraEntera(String texto) {
            // «pvta» no: la v no es leetspeak del contrato. Se deja como
            // control negativo dentro del mismo grupo para que se vea el borde.
            if (texto.startsWith("pvta")) {
                assertThat(coincidencias(texto)).isEmpty();
            } else {
                assertThat(coincidencias(texto)).extracting(TerminoActivo::termino).contains("puta");
            }
        }

        @Test
        @DisplayName("cifras en los bordes: Messi10 y m3ss1 son messi; xXmessiXx no (limite de PALABRA)")
        void cifrasEnLosBordes() {
            assertThat(coincidencias("Messi10")).extracting(TerminoActivo::termino).containsExactly("messi");
            assertThat(coincidencias("m3ss1")).extracting(TerminoActivo::termino).containsExactly("messi");
            assertThat(coincidencias("Stalin1945")).extracting(TerminoActivo::termino).containsExactly("stalin");
            assertThat(coincidencias("xXmessiXx")).isEmpty();
        }

        @Test
        @DisplayName("un termino de varias palabras casa con esas palabras seguidas o escritas juntas, no con otras")
        void variasPalabras() {
            assertThat(coincidencias("me tomo una coca-cola fria")).extracting(TerminoActivo::termino)
                    .containsExactly("coca cola");
            assertThat(coincidencias("COCA COLA")).extracting(TerminoActivo::termino).containsExactly("coca cola");
            assertThat(coincidencias("bebo cocacola")).extracting(TerminoActivo::termino).containsExactly("coca cola");
            assertThat(coincidencias("la coca y la cola")).isEmpty();
        }
    }

    @Nested
    @DisplayName("plural y genero de los insultos (auditoria de DEV del 30-sep)")
    class PluralYGenero {

        private final List<TerminoActivo> insultos = List.of(
                termino("puta", CategoriaDeTermino.OFENSIVO),
                termino("perra", CategoriaDeTermino.OFENSIVO, ModoDeCoincidencia.PALABRA),
                termino("maricón", CategoriaDeTermino.OFENSIVO, ModoDeCoincidencia.PALABRA),
                termino("malparido", CategoriaDeTermino.OFENSIVO),
                termino("pendejo", CategoriaDeTermino.OFENSIVO),
                termino("mierda", CategoriaDeTermino.OFENSIVO),
                termino("shakira", CategoriaDeTermino.CELEBRIDAD),
                termino("messi", CategoriaDeTermino.CELEBRIDAD, ModoDeCoincidencia.PALABRA));

        private List<String> en(String texto) {
            return DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar(texto), insultos).stream()
                    .map(TerminoActivo::termino).toList();
        }

        @ParameterizedTest(name = "rechaza «{0}»")
        @ValueSource(strings = {"eres una puta", "son unas putas", "PUTAS", "p u t a s", "putas69",
                "malparida", "malparidos", "unos malparidas", "pendeja", "pendejos", "PENDEJAS",
                "maricones", "mierdas", "perras"})
        void pasabanEnElChatYYaNo(String texto) {
            assertThat(en(texto)).isNotEmpty();
        }

        @ParameterizedTest(name = "acepta «{0}»")
        @ValueSource(strings = {"disputas", "mi perro", "mi perrita", "perrito", "reputaciones",
                "computadoras", "pendiente", "malpar", "messis", "shakir"})
        void loCorrienteSigueEntrando(String texto) {
            assertThat(en(texto)).isEmpty();
        }

        @Test
        @DisplayName("una marca o una persona no se declinan: el plural de messi y la raiz de shakira no casan")
        void lasPersonasNoSeDeclinan() {
            assertThat(DetectorDeTerminos.conPlural("puta")).containsExactlyInAnyOrder("puta", "putas");
            assertThat(DetectorDeTerminos.conPlural("maricon")).containsExactlyInAnyOrder("maricon", "maricones");
            assertThat(DetectorDeTerminos.raizSinGenero("malparido")).isEqualTo("malparid");
            assertThat(DetectorDeTerminos.raizSinGenero("mierda")).isEqualTo("mierd");
            assertThat(DetectorDeTerminos.raizSinGenero("perra")).as("menos de seis letras: no se recorta").isNull();
            assertThat(DetectorDeTerminos.raizSinGenero("cabron")).as("no acaba en o ni en a").isNull();
            assertThat(en("Messi")).containsExactly("messi");
            assertThat(en("shakira")).containsExactly("shakira");
        }
    }

    @Test
    @DisplayName("varias coincidencias salen todas, en el orden de la lista y sin repetir")
    void varias() {
        assertThat(coincidencias("spiderman culo puta puta")).extracting(TerminoActivo::termino)
                .containsExactly("spiderman", "puta", "culo");
    }

    @Test
    @DisplayName("texto vacio o sin terminos: ninguna coincidencia")
    void nada() {
        assertThat(coincidencias("")).isEmpty();
        assertThat(DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar("spiderman"), List.of())).isEmpty();
    }

    @Test
    @DisplayName("un termino guardado con forma vacia (fila heredada inutilizable) no casa con todo")
    void formaVacia() {
        TerminoActivo vacio = new TerminoActivo("!!!", "", CategoriaDeTermino.OTRO, ModoDeCoincidencia.SUBCADENA);
        assertThat(DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar("hola"), List.of(vacio))).isEmpty();
    }
}
