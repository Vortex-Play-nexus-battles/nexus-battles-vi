package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.util.List;
import java.util.Optional;
import nexus.misiones.catalogo.CatalogoDeEstrategiasDesdeSemilla;
import nexus.misiones.dominio.CatalogoDeEstrategiasDeEnemigos;
import nexus.misiones.dominio.EstrategiaPredefinida;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que juega un enemigo que la mision no trae escrito (HU-SIM-004): su estrategia predefinida si existe y heroes
 * la acepta, y si no, la heuristica de siempre. Nunca un error al jugador.
 */
class EstrategiasPredefinidasTest {

    private Dobles.Heroes heroes;
    private RotacionesPorDefectoDeEnemigos heuristica;
    private EstrategiasPredefinidas estrategias;

    @BeforeEach
    void preparar() {
        heroes = new Dobles.Heroes();
        heuristica = new RotacionesPorDefectoDeEnemigos(heroes);
        estrategias = new EstrategiasPredefinidas(CatalogoDeEstrategiasDesdeSemilla.cargar(), heroes, heuristica);
    }

    @Test
    @DisplayName("un prototipo con estrategia predefinida la juega, con su id para saber cual fue")
    void juegaLaPredefinida() {
        EstrategiaDeEnemigos.Elegida elegida = estrategias.elegir("Mago Fuego", 5);

        assertThat(elegida.origen()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
        assertThat(elegida.id()).isEqualTo("mago-fuego-n4");
        assertThat(elegida.rotaciones()).containsExactly(List.of("Vulcano"), List.of("Misiles de magma"));
    }

    @Test
    @DisplayName("la estrategia sale del tramo del nivel del enemigo")
    void porTramo() {
        assertThat(estrategias.elegir("Guerrero Tanque", 1).id()).isEqualTo("guerrero-tanque-n1");
        assertThat(estrategias.elegir("Guerrero Tanque", 3).id()).isEqualTo("guerrero-tanque-n1");
        assertThat(estrategias.elegir("Guerrero Tanque", 4).id()).isEqualTo("guerrero-tanque-n4");
        assertThat(estrategias.elegir("Guerrero Tanque", 7).id()).isEqualTo("guerrero-tanque-n4");
        assertThat(estrategias.elegir("Guerrero Tanque", 8).id()).isEqualTo("guerrero-tanque-n8");
        assertThat(estrategias.elegir("Guerrero Tanque", 8).rotaciones()).hasSize(3);
    }

    @Test
    @DisplayName("la regla de heroes la valida una sola vez por estrategia, con el nivel en que se desbloquea, no por enemigo")
    void unaValidacionPorEstrategia() {
        estrategias.elegir("Mago Fuego", 4);
        estrategias.elegir("Mago Fuego", 4);
        estrategias.elegir("Mago Fuego", 7);
        estrategias.elegir("Mago Fuego", 8);

        assertThat(heroes.validaciones).containsExactly(
                "Mago Fuego@4 [[Vulcano], [Misiles de magma]]",
                "Mago Fuego@8 [[Vulcano], [Misiles de magma], [Pare de fuego]]");
    }

    @Test
    @DisplayName("si heroes devuelve los nombres exactos, son esos los que se juegan")
    void usaLosNombresDeHeroes() {
        CatalogoDeEstrategiasDeEnemigos catalogo = (prototipo, nivel) -> Optional.of(
                new EstrategiaPredefinida("de-prueba", prototipo, 1, List.of(List.of("misiles de magma"))));
        heroes.rotacionesCanonicas = List.of(List.of("Misiles de magma"));
        estrategias = new EstrategiasPredefinidas(catalogo, heroes, heuristica);

        assertThat(estrategias.elegir("Mago Fuego", 1).rotaciones()).containsExactly(List.of("Misiles de magma"));
    }

    @Test
    @DisplayName("si heroes rechaza la estrategia predefinida, el enemigo juega la heuristica y no pasa nada mas")
    void heroesLaRechaza() {
        heroes.motivoDeRechazo = "La rotación 1 usa una habilidad que Mago Fuego no posee en nivel 4: Vulcano.";
        heroes.rechazarSoloLasEscritas = true;
        heroes.habilidadesValidas = List.of("Misiles de magma", "Vulcano", "Ataque básico");

        EstrategiaDeEnemigos.Elegida elegida = estrategias.elegir("Mago Fuego", 4);

        assertThat(elegida.origen()).isEqualTo(OrigenDeEstrategia.HEURISTICA);
        assertThat(elegida.id()).isNull();
        assertThat(elegida.rotaciones()).containsExactly(List.of("Vulcano"), List.of("Misiles de magma"));
    }

    @Test
    @DisplayName("lo que heroes rechazo se recuerda: no se le vuelve a preguntar por cada enemigo")
    void elRechazoSeRecuerda() {
        heroes.motivoDeRechazo = "no";
        heroes.rechazarSoloLasEscritas = true;
        heroes.habilidadesValidas = List.of("Misiles de magma", "Ataque básico");

        estrategias.elegir("Mago Fuego", 4);
        estrategias.elegir("Mago Fuego", 4);
        estrategias.elegir("Mago Fuego", 6);

        // La predefinida se pregunto una sola vez; lo demas son las preguntas de la heuristica (por prototipo y nivel).
        assertThat(heroes.validaciones).filteredOn(v -> !v.endsWith(" []")).hasSize(1);
    }

    @Test
    @DisplayName("un prototipo sin estrategia predefinida juega la heuristica")
    void sinPredefinida() {
        CatalogoDeEstrategiasDeEnemigos vacio = (prototipo, nivel) -> Optional.empty();
        estrategias = new EstrategiasPredefinidas(vacio, heroes, heuristica);
        heroes.habilidadesValidas = List.of("Golpe con escudo", "Ataque básico");

        EstrategiaDeEnemigos.Elegida elegida = estrategias.elegir("Guerrero Tanque", 1);

        assertThat(elegida.origen()).isEqualTo(OrigenDeEstrategia.HEURISTICA);
        assertThat(elegida.rotaciones()).containsExactly(List.of("Golpe con escudo"));
    }

    @Test
    @DisplayName("si heroes no responde el fallo sube (la simulacion se reintenta) y no se recuerda como rechazo")
    void heroesCaido() {
        heroes.fallarAlValidar = Dobles.caido("heroes");

        assertThatThrownBy(() -> estrategias.elegir("Mago Fuego", 4)).isInstanceOf(DependenciaDegradada.class);

        heroes.fallarAlValidar = null;
        assertThat(estrategias.elegir("Mago Fuego", 4).origen()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
    }

    @Test
    @DisplayName("sigue siendo una EstrategiaDeEnemigos de siempre: porDefecto devuelve las rotaciones de la elegida")
    void porDefecto() {
        assertThat(estrategias.porDefecto("Mago Fuego", 4))
                .containsExactly(List.of("Vulcano"), List.of("Misiles de magma"));
    }

    @Test
    @DisplayName("la heuristica sola declara su origen: HEURISTICA y sin id")
    void laHeuristicaSeIdentifica() {
        heroes.habilidadesValidas = List.of("Golpe con escudo", "Ataque básico");

        EstrategiaDeEnemigos.Elegida elegida = heuristica.elegir("Guerrero Tanque", 1);

        assertThat(elegida.origen()).isEqualTo(OrigenDeEstrategia.HEURISTICA);
        assertThat(elegida.id()).isNull();
        assertThat(elegida.rotaciones()).containsExactly(List.of("Golpe con escudo"));
    }
}
