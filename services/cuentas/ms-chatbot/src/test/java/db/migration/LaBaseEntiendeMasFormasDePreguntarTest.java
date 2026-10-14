package db.migration;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V18 lee del JSON sus temas nuevos, sus variantes y sus casos: un contenido
 * incompleto no se aplica a medias, y las variantes se suman sin repetirse ni
 * quitar ninguna.
 */
@DisplayName("V18 · el contenido de la base que entiende mas formas de preguntar")
class LaBaseEntiendeMasFormasDePreguntarTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode delRepositorio() throws Exception {
        try (InputStream entrada = LaBaseEntiendeMasFormasDePreguntarTest.class
                .getResourceAsStream(V18__la_base_entiende_mas_formas_de_preguntar.CONTENIDO)) {
            return JSON.readTree(entrada);
        }
    }

    @Test
    void elContenidoDelRepositorioEsValido() throws Exception {
        JsonNode contenido = delRepositorio();

        List<V18__la_base_entiende_mas_formas_de_preguntar.Tema> temas =
            V18__la_base_entiende_mas_formas_de_preguntar.temasNuevos(contenido);
        assertThat(temas).extracting(V18__la_base_entiende_mas_formas_de_preguntar.Tema::clave)
            .containsExactly("terminos-del-servicio", "como-funciona-la-subasta");
        List<String> categorias = Arrays.stream(Categoria.values()).map(Enum::name).toList();
        List<String> tipos = Arrays.stream(TipoRespuesta.values()).map(Enum::name).toList();
        assertThat(temas).allSatisfy(tema -> {
            assertThat(categorias).contains(tema.categoria());
            assertThat(tipos).contains(tema.tipoRespuesta());
            assertThat(tema.palabrasEs()).isNotBlank();
            assertThat(tema.palabrasEn()).isNotBlank();
            assertThat(tema.respuestaEn()).isNotBlank();
        });

        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.variantesNuevas(contenido))
            .extracting(V18__la_base_entiende_mas_formas_de_preguntar.Variantes::clave)
            .containsExactly("modo-batalla", "problema-tecnico");

        List<String> preguntas = new ArrayList<>();
        contenido.path("casos").forEach(caso -> {
            assertThat(caso.path("temaClaveEsperada").asString()).isNotBlank();
            preguntas.add(caso.path("pregunta").asString().toLowerCase());
        });
        assertThat(preguntas).hasSize(13).doesNotHaveDuplicates();
    }

    @Test
    void unTemaIncompletoNoSeAplica() {
        assertThatThrownBy(() -> V18__la_base_entiende_mas_formas_de_preguntar.temasNuevos(JSON.readTree("""
            {"temasNuevos": [{"clave": "k", "categoria": "FAQ_GENERAL", "tipoRespuesta": "DIRECTA",
              "respuestaEs": "r"}]}
            """))).isInstanceOf(IllegalStateException.class).hasMessageContaining("titulo");
        assertThatThrownBy(() -> V18__la_base_entiende_mas_formas_de_preguntar.temasNuevos(JSON.readTree("""
            {"temasNuevos": [{"clave": " ", "categoria": "FAQ_GENERAL", "tipoRespuesta": "DIRECTA",
              "titulo": "t", "respuestaEs": "r"}]}
            """))).isInstanceOf(IllegalStateException.class).hasMessageContaining("clave");
        assertThatThrownBy(() -> V18__la_base_entiende_mas_formas_de_preguntar.temasNuevos(JSON.readTree("""
            {"temasNuevos": [{"clave": "k", "categoria": "FAQ_GENERAL", "tipoRespuesta": "DIRECTA",
              "titulo": "t", "respuestaEs": null}]}
            """))).isInstanceOf(IllegalStateException.class).hasMessageContaining("respuestaEs");
        assertThatThrownBy(() -> V18__la_base_entiende_mas_formas_de_preguntar.variantesNuevas(JSON.readTree("""
            {"variantesNuevas": [{"es": ["una"]}]}
            """))).isInstanceOf(IllegalStateException.class).hasMessageContaining("clave");
    }

    @Test
    void unTemaSinInglesNiVariantesNiPrioridadSeLeeConSusValoresPorOmision() {
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.temasNuevos(JSON.readTree("""
            {"temasNuevos": [{"clave": "k", "categoria": "FAQ_GENERAL", "tipoRespuesta": "DIRECTA",
              "titulo": "t", "respuestaEs": "r", "variantesEs": [" uno ", "", "dos"]}]}
            """))).singleElement().satisfies(tema -> {
                assertThat(tema.palabrasEs()).isEqualTo("uno, dos");
                assertThat(tema.palabrasEn()).isNull();
                assertThat(tema.respuestaEn()).isNull();
                assertThat(tema.prioridad()).isZero();
            });
    }

    @Test
    void sinNadaQueHacerLasListasVienenVacias() {
        JsonNode vacio = JSON.readTree("{}");

        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.temasNuevos(vacio)).isEmpty();
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.variantesNuevas(vacio)).isEmpty();
    }

    @Test
    void unirDejaSoloLasVariantesConTexto() {
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.unir(Arrays.asList(" a ", null, " ", "b")))
            .isEqualTo("a, b");
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.unir(List.of(" "))).isNull();
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.unir(List.of())).isNull();
    }

    @Test
    void sumarConservaLasQueHabiaYAnadeSoloLasQueFaltan() {
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.sumar("modos de juego, Formas De Jugar",
            Arrays.asList("formas de jugar", "ways to play", null, " ", "Modos de juego")))
            .isEqualTo("modos de juego, Formas De Jugar, ways to play");
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.sumar(null, List.of("una", "una")))
            .isEqualTo("una");
        assertThat(V18__la_base_entiende_mas_formas_de_preguntar.sumar(" , ", List.of())).isNull();
    }
}
