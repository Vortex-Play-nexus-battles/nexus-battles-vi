package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La copia local de la Tabla 7 (6.1.2): que accion es de que prototipo y desde que nivel, la unica cosa que misiones
 * necesita saber de ella sin preguntarle a heroes (por ejemplo, para validar las estrategias predefinidas al
 * arrancar, cuando heroes puede no estar levantado).
 */
class Tabla7LocalTest {

    private static final Path CATALOGO = Path.of("..", "..", "..", "contracts", "esquemas", "catalogo-oficial.yaml");
    private static final Pattern ACCION = Pattern.compile(
            "^\\s*-\\s*\\{nombre:\\s*([^,]+),\\s*heroe:\\s*([^,]+),\\s*costo:\\s*[^}]+}\\s*$");

    @Test
    @DisplayName("los ocho prototipos y sus tres acciones, en el orden de desbloqueo de la tabla")
    void lasTresAccionesDeCadaPrototipo() {
        assertThat(Tabla7Local.accionesDe("Guerrero Tanque"))
                .containsExactly("Golpe con escudo", "Mano de piedra", "Defensa feroz");
        assertThat(Tabla7Local.accionesDe("Guerrero Armas"))
                .containsExactly("Embate sangriento", "Lanza de los dioses", "Golpe de tormenta");
        assertThat(Tabla7Local.accionesDe("Mago Fuego"))
                .containsExactly("Misiles de magma", "Vulcano", "Pare de fuego");
        assertThat(Tabla7Local.accionesDe("Mago Hielo"))
                .containsExactly("Lluvia de hielo", "Cono de hielo", "Bola de hielo");
        assertThat(Tabla7Local.accionesDe("Pícaro Veneno")).containsExactly("Flor de loto", "Agonía", "Piquete");
        assertThat(Tabla7Local.accionesDe("Pícaro Machete")).containsExactly("Cortada", "Machetazo", "Planazo");
        assertThat(Tabla7Local.accionesDe("Chamán"))
                .containsExactly("Toque de la Vida", "Vínculo Natural", "Canto del Bosque");
        assertThat(Tabla7Local.accionesDe("Médico"))
                .containsExactly("Curación Directa", "Neutralización de Efectos", "Reanimación");
        assertThat(Tabla7Local.PROTOTIPOS).hasSize(8);
    }

    @Test
    @DisplayName("un prototipo que la tabla no conoce no tiene acciones")
    void prototipoDesconocido() {
        assertThat(Tabla7Local.accionesDe("Bardo")).isEmpty();
        assertThat(Tabla7Local.accionesDe(null)).isEmpty();
    }

    @Test
    @DisplayName("RC-01: se desbloquean en los niveles 1, 4 y 8")
    void desbloqueoPorNivel() {
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 0)).isEmpty();
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 1)).containsExactly("Misiles de magma");
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 3)).containsExactly("Misiles de magma");
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 4)).containsExactly("Misiles de magma", "Vulcano");
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 7)).containsExactly("Misiles de magma", "Vulcano");
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 8))
                .containsExactly("Misiles de magma", "Vulcano", "Pare de fuego");
        assertThat(Tabla7Local.desbloqueadasEn("Mago Fuego", 20)).hasSize(3);
    }

    @Test
    @DisplayName("el tramo de un nivel es el del ultimo desbloqueo alcanzado: 1, 4 u 8 (0 si no hay nivel)")
    void tramoDeUnNivel() {
        assertThat(Tabla7Local.tramoDe(0)).isZero();
        assertThat(Tabla7Local.tramoDe(-3)).isZero();
        assertThat(Tabla7Local.tramoDe(1)).isEqualTo(1);
        assertThat(Tabla7Local.tramoDe(3)).isEqualTo(1);
        assertThat(Tabla7Local.tramoDe(4)).isEqualTo(4);
        assertThat(Tabla7Local.tramoDe(7)).isEqualTo(4);
        assertThat(Tabla7Local.tramoDe(8)).isEqualTo(8);
        assertThat(Tabla7Local.tramoDe(15)).isEqualTo(8);
    }

    @Test
    @DisplayName("el nombre canonico se encuentra sin importar tildes ni mayusculas, como lo hace heroes")
    void nombreCanonico() {
        assertThat(Tabla7Local.canonica("Pícaro Veneno", "agonia")).contains("Agonía");
        assertThat(Tabla7Local.canonica("Chamán", "  TOQUE DE LA VIDA ")).contains("Toque de la Vida");
        assertThat(Tabla7Local.canonica("Chamán", "Vulcano")).isEqualTo(Optional.empty());
        assertThat(Tabla7Local.canonica("Bardo", "Vulcano")).isEmpty();
        assertThat(Tabla7Local.canonica("Chamán", null)).isEmpty();
    }

    @Test
    @DisplayName("la copia local es la Tabla 7 del catalogo oficial del repositorio, accion por accion y en su orden")
    void esLaDelCatalogoOficial() throws IOException {
        assumeTrue(Files.exists(CATALOGO), "no se corre dentro del monorepo: falta catalogo-oficial.yaml");
        Map<String, List<String>> oficial = new LinkedHashMap<>();
        for (String linea : Files.readAllLines(CATALOGO, StandardCharsets.UTF_8)) {
            Matcher m = ACCION.matcher(linea);
            if (m.matches()) {
                oficial.computeIfAbsent(m.group(2).trim(), k -> new ArrayList<>()).add(m.group(1).trim());
            }
        }

        assertThat(oficial).hasSize(8);
        assertThat(Tabla7Local.PROTOTIPOS).containsExactlyElementsOf(oficial.keySet());
        oficial.forEach((prototipo, acciones) ->
                assertThat(Tabla7Local.accionesDe(prototipo)).as(prototipo).containsExactlyElementsOf(acciones));
    }
}
