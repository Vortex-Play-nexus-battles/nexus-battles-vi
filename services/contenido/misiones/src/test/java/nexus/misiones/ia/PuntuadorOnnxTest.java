package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * El modelo se carga de un {@code modelo.onnx} con su {@code modelo.json} al lado, y se niega a usarse si algo no
 * cuadra: mejor sin modelo (decide la regla) que con un modelo equivocado. El de prueba es sintetico
 * ({@code src/test/resources/ia}, ver ia/README.md): sirve para probar la carga y la inferencia, nada mas.
 */
class PuntuadorOnnxTest {

    @TempDir
    Path carpeta;

    private Path modeloDePrueba() throws IOException {
        copiar("modelo.onnx");
        copiar("modelo.json");
        return carpeta.resolve("modelo.onnx");
    }

    private void copiar(String nombre) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ia/" + nombre)) {
            assertThat(in).as("recurso de prueba /ia/" + nombre).isNotNull();
            Files.write(carpeta.resolve(nombre), in.readAllBytes());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json() throws IOException {
        return JsonMapper.builder().build().readValue(Files.readString(carpeta.resolve("modelo.json")), Map.class);
    }

    private void escribirJson(Map<String, Object> contenido) throws IOException {
        Files.writeString(carpeta.resolve("modelo.json"), JsonMapper.builder().build().writeValueAsString(contenido),
                StandardCharsets.UTF_8);
    }

    private static float[] fila(float valor) {
        float[] f = new float[Caracteristicas.DIMENSION];
        java.util.Arrays.fill(f, valor);
        return f;
    }

    // ---------------------------------------------------------------- carga e inferencia

    @Test
    @DisplayName("carga el modelo de prueba y dice su version, de donde viene y con que caracteristicas se hizo")
    void cargaElModelo() throws Exception {
        try (PuntuadorOnnx modelo = PuntuadorOnnx.cargar(modeloDePrueba())) {
            assertThat(modelo.version()).isEqualTo("sintetico-v1");
            assertThat(modelo.metadatos().sintetico()).isTrue();
            assertThat(modelo.metadatos().versionDeCaracteristicas()).isEqualTo(Caracteristicas.VERSION);
            assertThat(modelo.metadatos().dimension()).isEqualTo(Caracteristicas.DIMENSION);
        }
    }

    @Test
    @DisplayName("da las mismas salidas que PyTorch para las entradas de ejemplo que trae el modelo.json")
    void mismasSalidasQueEnPython() throws Exception {
        Path onnx = modeloDePrueba();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ejemplos = (List<Map<String, Object>>) json().get("ejemplos");
        assertThat(ejemplos).hasSize(3);
        try (PuntuadorOnnx modelo = PuntuadorOnnx.cargar(onnx)) {
            for (Map<String, Object> ejemplo : ejemplos) {
                @SuppressWarnings("unchecked")
                List<Number> entrada = (List<Number>) ejemplo.get("entrada");
                float[] x = new float[entrada.size()];
                for (int i = 0; i < x.length; i++) {
                    x[i] = entrada.get(i).floatValue();
                }
                float[] salida = modelo.puntuar(new float[][] {x});
                assertThat(salida).hasSize(1);
                assertThat(salida[0]).isCloseTo(((Number) ejemplo.get("salida")).floatValue(),
                        org.assertj.core.data.Offset.offset(1e-4f));
            }
        }
    }

    @Test
    @DisplayName("puntua un lote de varias candidatas a la vez, una salida por candidata y en orden")
    void lote() throws Exception {
        try (PuntuadorOnnx modelo = PuntuadorOnnx.cargar(modeloDePrueba())) {
            float[] solas = {modelo.puntuar(new float[][] {fila(0.2f)})[0], modelo.puntuar(new float[][] {fila(0.8f)})[0]};

            float[] juntas = modelo.puntuar(new float[][] {fila(0.2f), fila(0.8f), fila(0.2f)});

            assertThat(juntas).hasSize(3);
            assertThat(juntas[0]).isCloseTo(solas[0], org.assertj.core.data.Offset.offset(1e-5f));
            assertThat(juntas[1]).isCloseTo(solas[1], org.assertj.core.data.Offset.offset(1e-5f));
            assertThat(juntas[2]).isEqualTo(juntas[0]);
        }
    }

    @Test
    @DisplayName("una entrada de dimension equivocada o vacia se rechaza antes de llegar a ONNX Runtime")
    void entradaMala() throws Exception {
        try (PuntuadorOnnx modelo = PuntuadorOnnx.cargar(modeloDePrueba())) {
            assertThatThrownBy(() -> modelo.puntuar(new float[][] {new float[3]}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> modelo.puntuar(new float[0][])).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("cerrar el modelo dos veces no falla; despues de cerrado ya no puntua")
    void cerrar() throws Exception {
        PuntuadorOnnx modelo = PuntuadorOnnx.cargar(modeloDePrueba());
        modelo.close();
        modelo.close();

        assertThatThrownBy(() -> modelo.puntuar(new float[][] {fila(0.5f)})).isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- un modelo que no se puede usar

    @Test
    @DisplayName("un archivo que no existe: no se puede usar")
    void noExiste() {
        assertThatThrownBy(() -> PuntuadorOnnx.cargar(carpeta.resolve("no-esta.onnx")))
                .isInstanceOf(ModeloNoUtilizable.class).hasMessageContaining("no-esta.onnx");
    }

    @Test
    @DisplayName("sin el modelo.json al lado no se sabe de que version es: no se usa")
    void sinMetadatos() throws Exception {
        Path onnx = modeloDePrueba();
        Files.delete(carpeta.resolve("modelo.json"));

        assertThatThrownBy(() -> PuntuadorOnnx.cargar(onnx)).isInstanceOf(ModeloNoUtilizable.class)
                .hasMessageContaining("modelo.json");
    }

    @Test
    @DisplayName("un modelo hecho con otra version de las caracteristicas no se usa")
    void otraVersionDeCaracteristicas() throws Exception {
        Path onnx = modeloDePrueba();
        Map<String, Object> contenido = new java.util.HashMap<>(json());
        contenido.put("caracteristicas", Map.of("version", Caracteristicas.VERSION + 1, "dimension",
                Caracteristicas.DIMENSION));
        escribirJson(contenido);

        assertThatThrownBy(() -> PuntuadorOnnx.cargar(onnx)).isInstanceOf(ModeloNoUtilizable.class)
                .hasMessageContaining("caracter");
    }

    @Test
    @DisplayName("un modelo hecho con otra dimension no se usa")
    void otraDimension() throws Exception {
        Path onnx = modeloDePrueba();
        Map<String, Object> contenido = new java.util.HashMap<>(json());
        contenido.put("caracteristicas", Map.of("version", Caracteristicas.VERSION, "dimension", 55));
        escribirJson(contenido);

        assertThatThrownBy(() -> PuntuadorOnnx.cargar(onnx)).isInstanceOf(ModeloNoUtilizable.class)
                .hasMessageContaining("55");
    }

    @Test
    @DisplayName("un .onnx que no es el que describe su modelo.json (otro hash) no se usa")
    void hashQueNoCuadra() throws Exception {
        Path onnx = modeloDePrueba();
        byte[] bytes = Files.readAllBytes(onnx);
        bytes[bytes.length / 2] ^= 0x5A;
        Files.write(onnx, bytes);

        assertThatThrownBy(() -> PuntuadorOnnx.cargar(onnx)).isInstanceOf(ModeloNoUtilizable.class)
                .hasMessageContaining("sha256");
    }

    @Test
    @DisplayName("un archivo corrupto con su hash al dia tampoco: ONNX Runtime lo rechaza y se dice sin lanzar otra cosa")
    void archivoCorrupto() throws Exception {
        Path onnx = modeloDePrueba();
        byte[] basura = "esto no es un modelo onnx".getBytes(StandardCharsets.UTF_8);
        Files.write(onnx, basura);
        Map<String, Object> contenido = new java.util.HashMap<>(json());
        contenido.put("onnx", Map.of("sha256", Hashes.sha256(basura)));
        escribirJson(contenido);

        assertThatThrownBy(() -> PuntuadorOnnx.cargar(onnx)).isInstanceOf(ModeloNoUtilizable.class);
    }

    @Test
    @DisplayName("si las salidas de ejemplo del modelo.json no salen al correrlo aqui, no se usa")
    void ejemploQueNoCoincide() throws Exception {
        Path onnx = modeloDePrueba();
        Map<String, Object> contenido = new java.util.HashMap<>(json());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ejemplos = new java.util.ArrayList<>((List<Map<String, Object>>) contenido.get("ejemplos"));
        Map<String, Object> primero = new java.util.HashMap<>(ejemplos.get(0));
        primero.put("salida", ((Number) primero.get("salida")).doubleValue() + 1.0);
        ejemplos.set(0, primero);
        contenido.put("ejemplos", ejemplos);
        escribirJson(contenido);

        assertThatThrownBy(() -> PuntuadorOnnx.cargar(onnx)).isInstanceOf(ModeloNoUtilizable.class)
                .hasMessageContaining("ejemplo");
    }
}
