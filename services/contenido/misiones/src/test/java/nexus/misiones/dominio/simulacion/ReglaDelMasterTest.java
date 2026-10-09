package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La regla de las estadisticas del Master (HU-SIM-006, decision del PO del 5-oct): de la vida y la defensa completas
 * de su prototipo en su nivel, el Master conserva la fraccion que fija el nivel del heroe. Es un dato versionado
 * ({@code semilla/refuerzo-del-master.json}) que tambien lee el simulador de motor-combate, donde se mide que con
 * esas fracciones un heroe del nivel recomendado le gana al Master cerca de la mitad de las veces.
 */
class ReglaDelMasterTest {

    private static InputStream json(String texto) {
        return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String regla(String version, String fracciones) {
        return "{\"version\":\"" + version + "\",\"notas\":[\"x\"],\"fraccionPorNivelDelHeroe\":{" + fracciones + "}}";
    }

    private static final String OCHO_MITADES = "\"1\":0.5,\"2\":0.5,\"3\":0.5,\"4\":0.5,\"5\":0.5,\"6\":0.5,\"7\":0.5,\"8\":0.5";

    // ---------------------------------------------------------------- la regla publicada

    @Test
    @DisplayName("la regla publicada trae version, notas con su justificacion y una fraccion por cada nivel del heroe, de 1 a 8")
    void laReglaPublicadaEsCompleta() {
        ReglaDelMaster publicada = ReglaDelMaster.publicada();

        assertThat(publicada.version()).isNotBlank();
        assertThat(publicada.fraccionPorNivelDelHeroe()).hasSize(8);
        for (int nivel = 1; nivel <= 8; nivel++) {
            double fraccion = publicada.fraccionPara(nivel);
            assertThat(fraccion).as("fraccion del nivel " + nivel).isGreaterThan(0).isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("la regla publicada baja de verdad al Master: en ningun nivel le deja las estadisticas completas")
    void laReglaPublicadaBajaAlMaster() {
        // Antes de la regla el Master peleaba con el 100 %: un heroe del nivel recomendado casi nunca le ganaba.
        ReglaDelMaster publicada = ReglaDelMaster.publicada();
        for (int nivel = 1; nivel <= 8; nivel++) {
            assertThat(publicada.fraccionPara(nivel)).as("nivel " + nivel).isLessThan(1.0);
        }
    }

    @Test
    @DisplayName("el archivo de la regla del repositorio se lee sin error y es el que publica el servicio")
    void elArchivoDelRepositorioSeLee() throws Exception {
        try (InputStream archivo = getClass().getClassLoader().getResourceAsStream(ReglaDelMaster.RECURSO)) {
            assertThat(archivo).as(ReglaDelMaster.RECURSO).isNotNull();
            assertThat(ReglaDelMaster.leer(archivo, ReglaDelMaster.RECURSO)).isEqualTo(ReglaDelMaster.publicada());
        }
    }

    // ---------------------------------------------------------------- lo que hace con las estadisticas

    @Test
    @DisplayName("la vida y la defensa del Master son la fraccion de las completas de su prototipo, redondeada")
    void aplicaLaFraccion() {
        ReglaDelMaster mitad = ReglaDelMaster.leer(json(regla("t", OCHO_MITADES)), "prueba");

        assertThat(mitad.vida(288, 6)).isEqualTo(144);
        assertThat(mitad.defensa(85, 6)).isEqualTo(43);
        assertThat(mitad.vida(1, 6)).as("nunca menos de un punto de vida").isEqualTo(1);
    }

    @Test
    @DisplayName("la fraccion depende del nivel del heroe, no del nivel del Master")
    void dependeDelNivelDelHeroe() {
        ReglaDelMaster regla = ReglaDelMaster.leer(json(regla("t",
                "\"1\":0.2,\"2\":0.2,\"3\":0.3,\"4\":0.5,\"5\":0.6,\"6\":0.7,\"7\":0.8,\"8\":0.9")), "prueba");

        assertThat(regla.fraccionPara(1)).isEqualTo(0.2);
        assertThat(regla.fraccionPara(8)).isEqualTo(0.9);
        assertThat(regla.vida(100, 4)).isEqualTo(50);
        assertThat(regla.vida(100, 7)).isEqualTo(80);
    }

    @Test
    @DisplayName("COMPLETA es la regla de antes: las estadisticas completas en todos los niveles")
    void completaEsLaReglaDeAntes() {
        for (int nivel = 1; nivel <= 8; nivel++) {
            assertThat(ReglaDelMaster.COMPLETA.vida(288, nivel)).isEqualTo(288);
            assertThat(ReglaDelMaster.COMPLETA.defensa(85, nivel)).isEqualTo(85);
        }
    }

    // ---------------------------------------------------------------- un archivo malo no se publica

    @Test
    @DisplayName("un archivo sin version ni notas, con un nivel de menos, con una fraccion fuera de (0, 1] o con un campo que no existe se rechaza con el motivo")
    void rechazaLoMalo() {
        assertThatThrownBy(() -> ReglaDelMaster.leer(json(regla("", OCHO_MITADES)), "prueba"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json("{\"version\":\"t\",\"fraccionPorNivelDelHeroe\":{"
                + OCHO_MITADES + "}}"), "prueba")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notas");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json(regla("t", "\"1\":0.5,\"2\":0.5")), "prueba"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nivel 3");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json(regla("t", OCHO_MITADES.replace("\"4\":0.5", "\"4\":0"))),
                "prueba")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nivel 4");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json(regla("t", OCHO_MITADES.replace("\"4\":0.5", "\"4\":1.2"))),
                "prueba")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nivel 4");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json(regla("t", OCHO_MITADES + ",\"9\":0.5")), "prueba"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nivel 9");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json("{\"version\":\"t\",\"fraccionPorNivelDelHeroe\":{"
                + OCHO_MITADES + "},\"factorMagico\":2}"), "prueba"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("factorMagico");
        assertThatThrownBy(() -> ReglaDelMaster.leer(json("esto no es json"), "prueba"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("prueba");
    }

    @Test
    @DisplayName("la fraccion de un nivel que no existe es un error, no un valor por omision")
    void nivelFueraDeRango() {
        assertThatThrownBy(() -> ReglaDelMaster.COMPLETA.fraccionPara(9)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9");
        assertThat(Map.copyOf(ReglaDelMaster.COMPLETA.fraccionPorNivelDelHeroe())).hasSize(8);
    }
}
