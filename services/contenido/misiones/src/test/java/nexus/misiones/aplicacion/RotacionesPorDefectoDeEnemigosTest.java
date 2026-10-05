package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La estrategia de un enemigo que la mision no trae escrita (7.8.6): por ahora,
 * una rotacion por cada habilidad que su prototipo tiene desbloqueada en su
 * nivel, la mas avanzada primero. HU-SIM-004 la refinara.
 */
class RotacionesPorDefectoDeEnemigosTest {

    private Dobles.Heroes heroes;
    private RotacionesPorDefectoDeEnemigos estrategia;

    @BeforeEach
    void preparar() {
        heroes = new Dobles.Heroes();
        estrategia = new RotacionesPorDefectoDeEnemigos(heroes);
    }

    @Test
    @DisplayName("cada habilidad desbloqueada es una rotacion, de la mas avanzada a la primera")
    void unaRotacionPorHabilidad() {
        heroes.habilidadesValidas = List.of("Golpe con escudo", "Embate sangriento", "Furia", "Ataque básico");

        assertThat(estrategia.porDefecto("Guerrero Tanque", 8))
                .containsExactly(List.of("Furia"), List.of("Embate sangriento"), List.of("Golpe con escudo"));
    }

    @Test
    @DisplayName("sin habilidades especiales la estrategia es vacia: ataque basico siempre")
    void soloAtaqueBasico() {
        heroes.habilidadesValidas = List.of("Ataque básico");

        assertThat(estrategia.porDefecto("Guerrero Tanque", 1)).isEmpty();
    }

    @Test
    @DisplayName("nunca pasa de tres rotaciones, el maximo de una estrategia (7.8.5)")
    void tresComoMaximo() {
        heroes.habilidadesValidas = List.of("A", "B", "C", "D", "Ataque básico");

        assertThat(estrategia.porDefecto("Guerrero Tanque", 8)).hasSize(3)
                .containsExactly(List.of("D"), List.of("C"), List.of("B"));
    }

    @Test
    @DisplayName("se pregunta a heroes una vez por prototipo y nivel, no por cada enemigo")
    void unaPreguntaPorPrototipoYNivel() {
        heroes.habilidadesValidas = List.of("Golpe con escudo", "Ataque básico");

        estrategia.porDefecto("Guerrero Tanque", 1);
        estrategia.porDefecto("Guerrero Tanque", 1);
        estrategia.porDefecto("Guerrero Tanque", 4);
        estrategia.porDefecto("Mago Fuego", 1);

        assertThat(heroes.validaciones).hasSize(3);
    }

    @Test
    @DisplayName("se pide la estrategia vacia: lo que heroes contesta son las habilidades validas del nivel")
    void sePideSinRotaciones() {
        estrategia.porDefecto("Guerrero Tanque", 4);

        assertThat(heroes.validaciones).containsExactly("Guerrero Tanque@4 []");
    }

    @Test
    @DisplayName("si heroes no responde el fallo sube: la simulacion se reintenta")
    void heroesCaido() {
        heroes.fallarAlValidar = Dobles.caido("heroes");

        assertThatThrownBy(() -> estrategia.porDefecto("Guerrero Tanque", 1))
                .isInstanceOf(DependenciaDegradada.class);
    }
}
