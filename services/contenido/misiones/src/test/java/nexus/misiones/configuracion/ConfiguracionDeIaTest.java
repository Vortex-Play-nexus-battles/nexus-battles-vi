package nexus.misiones.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.ia.DecisorConModelo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El modelo se enciende con {@code misiones.ia.modelo.habilitado} y {@code misiones.ia.modelo.ruta}. Apagado, sin
 * ruta, con un modelo que no esta, esta danado o no cuadra, o con un umbral absurdo, el decisor es la regla de
 * siempre: el servicio arranca y se comporta exactamente como antes. Nunca un error por culpa del modelo.
 */
class ConfiguracionDeIaTest {

    private static final DecisorDeTurno REGLA = turno -> new DecisionDeTurno("Ataque básico", 0, List.of());

    @TempDir
    Path carpeta;

    private Path modeloDePrueba() throws IOException {
        for (String nombre : List.of("modelo.onnx", "modelo.json")) {
            try (InputStream in = getClass().getResourceAsStream("/ia/" + nombre)) {
                Files.write(carpeta.resolve(nombre), in.readAllBytes());
            }
        }
        return carpeta.resolve("modelo.onnx");
    }

    @Test
    @DisplayName("apagado (el valor por omision): es la regla, la misma instancia, aunque haya un modelo valido")
    void apagado() throws IOException {
        DecisorDeTurno decisor = ConfiguracionDeIa.elegirDecisor(REGLA, false, modeloDePrueba().toString(), 0.6);

        assertThat(decisor).isSameAs(REGLA);
    }

    @Test
    @DisplayName("encendido pero sin ruta: la regla")
    void sinRuta() {
        assertThat(ConfiguracionDeIa.elegirDecisor(REGLA, true, "", 0.6)).isSameAs(REGLA);
        assertThat(ConfiguracionDeIa.elegirDecisor(REGLA, true, null, 0.6)).isSameAs(REGLA);
        assertThat(ConfiguracionDeIa.elegirDecisor(REGLA, true, "   ", 0.6)).isSameAs(REGLA);
    }

    @Test
    @DisplayName("encendido con un archivo que no existe: la regla, sin lanzar nada")
    void noExiste() {
        assertThat(ConfiguracionDeIa.elegirDecisor(REGLA, true, carpeta.resolve("no-esta.onnx").toString(), 0.6))
                .isSameAs(REGLA);
    }

    @Test
    @DisplayName("encendido con un modelo danado: la regla, sin lanzar nada")
    void danado() throws IOException {
        Path onnx = modeloDePrueba();
        Files.writeString(onnx, "esto no es un modelo");

        assertThat(ConfiguracionDeIa.elegirDecisor(REGLA, true, onnx.toString(), 0.6)).isSameAs(REGLA);
    }

    @Test
    @DisplayName("encendido con un modelo valido: el decisor con modelo, que envuelve a la regla")
    void encendido() throws IOException {
        DecisorDeTurno decisor = ConfiguracionDeIa.elegirDecisor(REGLA, true, modeloDePrueba().toString(), 0.6);

        assertThat(decisor).isInstanceOf(DecisorConModelo.class);
    }

    @Test
    @DisplayName("un umbral de confianza absurdo no tumba el arranque: la regla")
    void umbralAbsurdo() throws IOException {
        assertThat(ConfiguracionDeIa.elegirDecisor(REGLA, true, modeloDePrueba().toString(), 7.0)).isSameAs(REGLA);
    }
}
