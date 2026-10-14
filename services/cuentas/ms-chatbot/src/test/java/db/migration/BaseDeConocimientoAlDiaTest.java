package db.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V16 lee sus correcciones de un JSON: una mal escrita no puede aplicarse a
 * medias. Cada una dice por qué se busca (clave o título, solo uno), lo que
 * tiene que decir el tema hoy y lo que dirá en los dos idiomas.
 */
@DisplayName("V16 · el contenido de las correcciones")
class BaseDeConocimientoAlDiaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static List<V16__base_de_conocimiento_al_dia.Correccion> de(String json) {
        return V16__base_de_conocimiento_al_dia.correcciones(JSON.readTree(json));
    }

    @Test
    void elContenidoDelRepositorioEsValidoYCadaTituloApareceUnaVez() throws Exception {
        JsonNode contenido;
        try (InputStream entrada = getClass().getResourceAsStream(V16__base_de_conocimiento_al_dia.CONTENIDO)) {
            contenido = JSON.readTree(entrada);
        }
        List<V16__base_de_conocimiento_al_dia.Correccion> correcciones =
            V16__base_de_conocimiento_al_dia.correcciones(contenido);
        assertThat(correcciones).hasSize(19);
        assertThat(correcciones.get(0).clave()).isEqualTo("apelar-sancion");
        assertThat(correcciones.get(0).variantesEs()).startsWith("apelar, apelacion,");
        assertThat(correcciones.stream().skip(1).map(V16__base_de_conocimiento_al_dia.Correccion::titulo))
            .doesNotContainNull().doesNotHaveDuplicates();
        // Lo que se escribe nunca es lo que habia: si no, la correccion sobra.
        assertThat(correcciones).allSatisfy(c -> assertThat(c.respuestaEs()).isNotEqualTo(c.antesEs()));
    }

    @Test
    void sinVariantesLasDejaComoEstan() {
        assertThat(de("""
            {"porClave": [{"clave": "k", "antesEs": "a", "respuestaEs": "b", "respuestaEn": "c"}]}
            """)).singleElement().satisfies(c -> {
                assertThat(c.variantesEs()).isNull();
                assertThat(c.variantesEn()).isNull();
            });
        assertThat(de("""
            {"porClave": [{"clave": "k", "antesEs": "a", "respuestaEs": "b", "respuestaEn": "c",
              "variantesEs": [" uno ", "", "dos"], "variantesEn": []}]}
            """)).singleElement().satisfies(c -> {
                assertThat(c.variantesEs()).isEqualTo("uno, dos");
                assertThat(c.variantesEn()).isNull();
            });
    }

    @Test
    void unaCorreccionIncompletaNoSeAplica() {
        assertThatThrownBy(() -> de("""
            {"porTitulo": [{"titulo": "t", "respuestaEs": "b", "respuestaEn": "c"}]}
            """)).isInstanceOf(IllegalStateException.class).hasMessageContaining("incompleta");
        assertThatThrownBy(() -> de("""
            {"porTitulo": [{"titulo": "t", "antesEs": "a", "respuestaEs": "b", "respuestaEn": null}]}
            """)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> de("""
            {"porTitulo": [{"titulo": "t", "antesEs": "a", "respuestaEn": "c"}]}
            """)).isInstanceOf(IllegalStateException.class);
        // Sin por donde buscarla, tampoco: ni titulo ni clave.
        assertThatThrownBy(() -> de("""
            {"porTitulo": [{"antesEs": "a", "respuestaEs": "b", "respuestaEn": "c"}]}
            """)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> de("""
            {"porClave": [{"antesEs": "a", "respuestaEs": "b", "respuestaEn": "c"}]}
            """)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sinCorreccionesNoHayNadaQueHacer() {
        assertThat(de("{}")).isEmpty();
    }
}
