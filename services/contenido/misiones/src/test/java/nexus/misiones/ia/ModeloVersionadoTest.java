package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * El primer modelo entrenado con partidas reales (HU-SIM-008): el que se versiona en {@code ia/modelos/1.0.0/} y el
 * que la imagen del servicio copia a {@code /app/ia/}. Estas pruebas leen los archivos del repositorio, no copias
 * de prueba: si alguien cambia el {@code .onnx} sin su {@code modelo.json}, o la imagen deja de copiar la carpeta,
 * se rompen aqui y no en DEV.
 */
class ModeloVersionadoTest {

    private static final String SHA256_DE_LA_VERSION_1_0_0 =
            "ef0cdbc8f83bee5c80a47d4070b2cc2a50f40be1d1e6ae6255495a6dd99ff68f";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json() throws IOException {
        return JsonMapper.builder().build().readValue(
                Files.readString(ModeloVersionado.carpeta().resolve("modelo.json")), Map.class);
    }

    @Test
    @DisplayName("la carpeta versionada trae el modelo.onnx y su modelo.json")
    void estaVersionado() {
        assertThat(ModeloVersionado.carpeta()).isDirectory();
        assertThat(ModeloVersionado.onnx()).isRegularFile();
        assertThat(ModeloVersionado.carpeta().resolve("modelo.json")).isRegularFile();
    }

    @Test
    @DisplayName("es de produccion, version 1.0.0, con las caracteristicas de este servicio y el hash de su archivo")
    void metadatos() throws Exception {
        try (PuntuadorOnnx modelo = PuntuadorOnnx.cargar(ModeloVersionado.onnx())) {
            MetadatosDelModelo m = modelo.metadatos();

            assertThat(m.sintetico()).as("entrenado con partidas reales, no con eventos de prueba").isFalse();
            assertThat(modelo.version()).isEqualTo("1.0.0");
            assertThat(m.version()).isEqualTo(ModeloVersionado.VERSION);
            assertThat(m.versionDeCaracteristicas()).isEqualTo(Caracteristicas.VERSION);
            assertThat(m.dimension()).isEqualTo(54).isEqualTo(Caracteristicas.DIMENSION);
            assertThat(m.sha256()).isEqualTo(Hashes.sha256(Files.readAllBytes(ModeloVersionado.onnx())))
                    .isEqualTo(SHA256_DE_LA_VERSION_1_0_0);
        }
    }

    @Test
    @DisplayName("PuntuadorOnnx lo acepta: reproduce los tres ejemplos de PyTorch que trae su modelo.json")
    void reproduceAPyTorch() throws Exception {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ejemplos = (List<Map<String, Object>>) json().get("ejemplos");
        assertThat(ejemplos).hasSize(3);

        // cargar() ya exige los ejemplos; aqui se comprueban uno por uno para que el fallo diga cual.
        try (PuntuadorOnnx modelo = PuntuadorOnnx.cargar(ModeloVersionado.onnx())) {
            for (Map<String, Object> ejemplo : ejemplos) {
                @SuppressWarnings("unchecked")
                List<Number> entrada = (List<Number>) ejemplo.get("entrada");
                assertThat(entrada).hasSize(Caracteristicas.DIMENSION);
                float[] x = new float[entrada.size()];
                for (int i = 0; i < x.length; i++) {
                    x[i] = entrada.get(i).floatValue();
                }
                assertThat(modelo.puntuar(new float[][] {x})[0])
                        .isCloseTo(((Number) ejemplo.get("salida")).floatValue(),
                                org.assertj.core.data.Offset.offset(1e-4f));
            }
        }
    }

    @Test
    @DisplayName("dice de donde salio: 689 eventos de 33 ejecuciones, sin candidatas registradas (los del README)")
    void procedencia() throws Exception {
        Map<String, Object> json = json();

        assertThat(json.get("sintetico")).isEqualTo(false);
        assertThat(json.get("eventos")).isEqualTo(689);
        assertThat(json.get("ejecuciones")).isEqualTo(33);
        assertThat(json.get("candidatas")).isEqualTo(Map.of("derivadas", 689, "registradas", 0));
    }

    @Test
    @DisplayName("la imagen copia esa carpeta a /app/ia/ y la ruta por omision del servicio apunta alli")
    void laImagenLoLleva() throws IOException {
        String dockerfile = Files.readString(ModeloVersionado.modulo().resolve("Dockerfile"));

        assertThat(dockerfile).contains("COPY --from=construccion "
                + "/repo/services/contenido/misiones/ia/modelos/" + ModeloVersionado.VERSION + "/ /app/ia/");
        assertThat(Files.readString(ModeloVersionado.modulo().resolve("src/main/resources/application.properties")))
                .contains("${MISIONES_IA_MODELO_RUTA:/app/ia/modelo.onnx}");
    }

    @Test
    @DisplayName("el .onnx esta marcado como binario: Git no lo normaliza ni lo muestra como diff de texto")
    void esBinarioParaGit() throws IOException {
        Path atributos = ModeloVersionado.modulo().resolve(".gitattributes");

        assertThat(atributos).isRegularFile();
        assertThat(Files.readAllLines(atributos)).anyMatch(l -> l.trim().matches("\\*\\.onnx\\s+binary"));
    }
}
