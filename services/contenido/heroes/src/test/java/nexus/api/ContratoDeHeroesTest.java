package nexus.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de contrato (documento del curso, 11.4.2: las pruebas de integracion
 * "aseguran que los contratos entre servicios se cumplen"). Cada respuesta del
 * servicio se valida contra el contrato publicado contracts/openapi/heroes.yaml,
 * que es lo que consumen la vitrina de e-commerce y los demas equipos
 * (regla de plataforma: contrato primero).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContratoDeHeroesTest {

    private static final String CONTRATO =
            Path.of("contracts/openapi/heroes.yaml").toUri().toString();
    private static final String CONTRATO_DESVIADO =
            Path.of("src/test/resources/contrato-desviado.yaml").toUri().toString();

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("la copia publicada en contracts/openapi de la raiz es identica a la del servicio")
    void copiaPublicadaSinDesviacion() throws Exception {
        // Regla de plataforma: los contratos entre equipos se publican en la
        // carpeta raiz. Esta prueba impide que las dos copias se separen.
        Path raiz = Path.of("../../../contracts/openapi/heroes.yaml");
        Assumptions.assumeTrue(Files.exists(raiz), "fuera del monorepo no hay copia publicada");

        assertThat(Files.readString(raiz))
                .isEqualTo(Files.readString(Path.of("contracts/openapi/heroes.yaml")));
    }

    @Test
    @DisplayName("el validador detecta desviaciones del contrato (control negativo)")
    void elValidadorDetectaDesviaciones() {
        // Un contrato que exige un campo inexistente DEBE hacer fallar la
        // validacion; si esto pasara en verde, las demas pruebas no probarian nada.
        Throwable desviacion = catchThrowable(() ->
                mvc.perform(get("/api/v1/heroes/{nombre}", "Chamán"))
                        .andExpect(openApi().isValid(CONTRATO_DESVIADO)));

        assertThat(desviacion)
                .as("el validador debe rechazar una respuesta sin el campo exigido")
                .isNotNull();
        assertThat(desviacion.getMessage()).contains("identificador");
    }

    @Test
    @DisplayName("el catalogo cumple el contrato publicado")
    void catalogoCumpleElContrato() throws Exception {
        mvc.perform(get("/api/v1/heroes"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("la ficha de un sanador cumple el contrato (sin ataque ni dano)")
    void fichaDeSanadorCumpleElContrato() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}", "Chamán"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("la ficha de un no sanador cumple el contrato (con ataque y dano)")
    void fichaDeNoSanadorCumpleElContrato() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}", "Guerrero Tanque"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("la vista por nivel cumple el contrato")
    void vistaPorNivelCumpleElContrato() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Mago Fuego", 4))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Médico", 8))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("la progresion sin prototipo cumple el contrato")
    void progresionCumpleElContrato() throws Exception {
        mvc.perform(get("/api/v1/progresion/niveles"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
        mvc.perform(post("/api/v1/progresion/experiencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nivel\":2,\"experiencia\":10,\"puntos\":500}"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
        mvc.perform(get("/api/v1/progresion/experiencia-por-enemigo/{dado}", 8))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("el error de nivel no valido cumple el contrato (RFC 9457)")
    void errorDeNivelCumpleElContrato() throws Exception {
        // La peticion viola a proposito el minimo del parametro (eso es lo que se
        // prueba), asi que aqui solo se valida la RESPUESTA contra el contrato.
        OpenApiInteractionValidator soloRespuesta = OpenApiInteractionValidator.createFor(CONTRATO)
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request", ValidationReport.Level.IGNORE)
                        .build())
                .build();

        mvc.perform(get("/api/v1/heroes/{nombre}/niveles/{nivel}", "Mago Fuego", 0))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(soloRespuesta));
    }

    @Test
    @DisplayName("el error de heroe no disponible cumple el contrato (RFC 9457)")
    void errorCumpleElContrato() throws Exception {
        mvc.perform(get("/api/v1/heroes/{nombre}", "Nigromante"))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(CONTRATO));
    }
}
