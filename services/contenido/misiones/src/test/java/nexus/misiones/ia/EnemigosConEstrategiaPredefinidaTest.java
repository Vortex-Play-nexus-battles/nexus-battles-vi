package nexus.misiones.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.catalogo.CatalogoDeEstrategiasDesdeSemilla;
import nexus.misiones.dominio.EstrategiaPredefinida;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.OrigenDeEstrategia;
import nexus.misiones.dominio.simulacion.PerfilDeCombate;
import nexus.misiones.dominio.simulacion.Rival;
import nexus.misiones.dominio.simulacion.Simulacion;
import nexus.misiones.dominio.simulacion.SimuladorDeMision;
import nexus.misiones.dominio.simulacion.TipoDeRival;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Criterio 1 de HU-SIM-004: «dado un enemigo en una mision, cuando llega su turno, la IA selecciona su accion de
 * acuerdo con la estrategia definida». Se juega la estrategia predefinida de verdad (la de la semilla) contra el
 * simulador, el motor de combate (doble) y la regla de rotaciones de heroes (doble fiel: primera rotacion viable por
 * poder y recarga, ataque basico de respaldo), y se lee lo que quedo en los eventos.
 *
 * <p>Los costos son los de la Tabla 7. El motor de pruebas recupera 2 de poder por turno propio, gasta el costo de la
 * accion y el enemigo entra con 10 de poder; la recarga es de un turno (no se puede repetir en el turno siguiente).
 */
class EnemigosConEstrategiaPredefinidaTest {

    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    private static final Formula ATAQUE = new Formula(10, 1, 6);
    private static final Formula DANO = new Formula(1, 1, 4);
    private static final HeroeEnMision HEROE = new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 8, 0, 12, 1000, 11);
    private static final PerfilDeCombate PERFIL = new PerfilDeCombate(
            new EstadisticasDeCombate(12, 1000, 11, ATAQUE, DANO, null), List.of(), List.of());

    /** Tabla 7, costos en poder. */
    private static final Map<String, Integer> COSTOS = Map.of(
            "Vulcano", 6, "Misiles de magma", 2, "Pare de fuego", 4,
            "Golpe con escudo", 2, "Mano de piedra", 4, "Defensa feroz", 6);

    private static Rival enemigoCon(String prototipo, int nivel) {
        EstrategiaPredefinida estrategia = CatalogoDeEstrategiasDesdeSemilla.cargar().para(prototipo, nivel)
                .orElseThrow();
        return new Rival("Enemigo", TipoDeRival.REGULAR, prototipo, nivel, 1000, 5, 10, estrategia.rotaciones(), null,
                ATAQUE, DANO, null, OrigenDeEstrategia.PREDEFINIDA, estrategia.id());
    }

    private static Simulacion simular(Rival rival) {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 1;
        motor.danoDeLosEnemigos = 1;
        motor.costos.putAll(COSTOS);
        return new SimuladorDeMision(new ReglaDeRotaciones(COSTOS), motor, dado -> 1).simular(EJECUCION,
                "mision-de-prueba", HEROE, PERFIL, List.of(), List.of(rival), new AzarConSemilla(42L), 42L);
    }

    private static List<EventoDeCombate> turnosDelEnemigo(Simulacion simulacion) {
        return simulacion.eventos().stream()
                .filter(e -> e.actor().lado() != EventoDeCombate.Lado.HEROE && e.jugada() != null).toList();
    }

    @Test
    @DisplayName("un mago de fuego de nivel 8 juega Vulcano, Misiles de magma y Pare de fuego en el orden de su estrategia, segun su poder y su recarga")
    void elMagoDeFuegoJuegaSuRotacion() {
        List<String> jugadas = turnosDelEnemigo(simular(enemigoCon("Mago Fuego", 8))).stream()
                .limit(8).map(e -> e.jugada().ejecutada()).toList();

        // Poder 10 y +2 por turno. T1 Vulcano (6). T2 Vulcano en recarga: Misiles. T3 Vulcano otra vez (6 de 6).
        // T4 Misiles. T5 sin poder para nada y Misiles en recarga: ataque basico de respaldo. T6 Misiles.
        // T7 Pare de fuego (4 de 4). T8 Misiles.
        assertThat(jugadas).containsExactly("Vulcano", "Misiles de magma", "Vulcano", "Misiles de magma",
                "Ataque básico", "Misiles de magma", "Pare de fuego", "Misiles de magma");
    }

    @Test
    @DisplayName("un tanque ataca con Golpe con escudo y se defiende cuando el golpe se recarga: Defensa feroz antes que Mano de piedra")
    void elTanqueAtacaYSeDefiende() {
        List<String> jugadas = turnosDelEnemigo(simular(enemigoCon("Guerrero Tanque", 8))).stream()
                .limit(8).map(e -> e.jugada().ejecutada()).toList();

        assertThat(jugadas).containsExactly("Golpe con escudo", "Defensa feroz", "Golpe con escudo", "Defensa feroz",
                "Golpe con escudo", "Ataque básico", "Golpe con escudo", "Mano de piedra");
    }

    @Test
    @DisplayName("nunca juega una accion sin el poder que cuesta ni repite una accion especial en su turno de recarga")
    void respetaPoderYRecarga() {
        for (String prototipo : List.of("Mago Fuego", "Guerrero Tanque")) {
            List<EventoDeCombate> turnos = turnosDelEnemigo(simular(enemigoCon(prototipo, 8)));
            assertThat(turnos).hasSizeGreaterThan(50);
            Map<String, Integer> ultimoUso = new HashMap<>();
            List<String> violaciones = new ArrayList<>();
            for (EventoDeCombate e : turnos) {
                String ejecutada = e.jugada().ejecutada();
                Integer costo = COSTOS.get(ejecutada);
                if (costo == null) {
                    continue;
                }
                if (e.antes().actor().poder() < costo) {
                    violaciones.add(prototipo + " T" + e.turno() + ": " + ejecutada + " cuesta " + costo + " y tenia "
                            + e.antes().actor().poder());
                }
                Integer anterior = ultimoUso.put(ejecutada, e.turno());
                if (anterior != null && e.turno() - anterior < 2) {
                    violaciones.add(prototipo + " T" + e.turno() + ": " + ejecutada + " se repite en recarga");
                }
            }
            assertThat(violaciones).isEmpty();
        }
    }

    @Test
    @DisplayName("lo que se juega siempre sale de las rotaciones de la estrategia o es el ataque basico de respaldo")
    void soloJuegaSuEstrategia() {
        for (String prototipo : List.of("Mago Fuego", "Guerrero Tanque", "Pícaro Veneno", "Guerrero Armas")) {
            Rival rival = enemigoCon(prototipo, 8);
            List<String> permitidas = new ArrayList<>(rival.rotaciones().stream().flatMap(List::stream).toList());
            permitidas.add("Ataque básico");

            assertThat(turnosDelEnemigo(simular(rival)))
                    .allSatisfy(e -> assertThat(e.jugada().ejecutada()).isIn(permitidas));
        }
    }

    @Test
    @DisplayName("cada jugada del enemigo deja dicho que estrategia uso y cual: PREDEFINIDA y su id; el heroe no tiene")
    void dejaLaTraza() {
        Simulacion simulacion = simular(enemigoCon("Mago Fuego", 8));

        assertThat(turnosDelEnemigo(simulacion)).isNotEmpty().allSatisfy(e -> {
            assertThat(e.jugada().estrategia()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
            assertThat(e.jugada().estrategiaId()).isEqualTo("mago-fuego-n8");
        });
        assertThat(simulacion.eventos().stream().filter(e -> e.actor().lado() == EventoDeCombate.Lado.HEROE
                && e.jugada() != null)).isNotEmpty().allSatisfy(e -> {
                    assertThat(e.jugada().estrategia()).isNull();
                    assertThat(e.jugada().estrategiaId()).isNull();
                });
    }

    @Test
    @DisplayName("un rival con rotaciones y sin origen declarado es de la mision; sin rotaciones no tiene estrategia")
    void origenPorOmision() {
        Rival conRotaciones = new Rival("A", TipoDeRival.REGULAR, "Guerrero Tanque", 1, 10, 11, 10,
                List.of(List.of("Golpe con escudo")), null);
        Rival sinRotaciones = new Rival("B", TipoDeRival.REGULAR, "Guerrero Tanque", 1, 10, 11, 10, List.of(), null);

        assertThat(conRotaciones.origenDeEstrategia()).isEqualTo(OrigenDeEstrategia.MISION);
        assertThat(conRotaciones.estrategiaId()).isNull();
        assertThat(sinRotaciones.origenDeEstrategia()).isNull();
        assertThat(sinRotaciones.estrategiaId()).isNull();
    }

    @Test
    @DisplayName("un rival sin rotaciones no puede declarar origen ni id: no hay estrategia que trazar")
    void sinRotacionesNoHayOrigen() {
        Rival rival = new Rival("C", TipoDeRival.REGULAR, "Guerrero Tanque", 1, 10, 11, 10, List.of(), null, null, null,
                null, OrigenDeEstrategia.HEURISTICA, "x");

        assertThat(rival.origenDeEstrategia()).isNull();
        assertThat(rival.estrategiaId()).isNull();
    }
}
