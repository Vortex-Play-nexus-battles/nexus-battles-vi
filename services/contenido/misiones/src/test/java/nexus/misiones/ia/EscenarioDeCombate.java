package nexus.misiones.ia;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.dominio.HeroeEnMision;
import nexus.misiones.dominio.simulacion.AzarConSemilla;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.EstadisticasDeCombate;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.Formula;
import nexus.misiones.dominio.simulacion.PerfilDeCombate;
import nexus.misiones.dominio.simulacion.Rival;
import nexus.misiones.dominio.simulacion.Simulacion;
import nexus.misiones.dominio.simulacion.SimuladorDeMision;
import nexus.misiones.dominio.simulacion.TipoDeRival;

/**
 * Una mision completa con el simulador y el motor de combate (doble), para probar cualquier decisor, con la regla o con
 * un modelo, sobre la misma situacion y con la misma semilla. Lo usan {@code SimulacionConModeloTest} (modelo de
 * prueba) y {@code SimulacionConModeloVersionadoTest} (el modelo entrenado que viaja en la imagen).
 */
final class EscenarioDeCombate {

    static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    static final String MISION = "templo-olvidado";
    static final Formula ATAQUE = new Formula(10, 1, 6);
    static final Formula DANO = new Formula(2, 1, 4);

    static final HeroeEnMision HEROE = new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 8, 0, 12, 60, 11);
    static final PerfilDeCombate PERFIL = new PerfilDeCombate(
            new EstadisticasDeCombate(12, 60, 11, ATAQUE, DANO, null), List.of(), List.of());

    static final List<List<String>> ESTRATEGIA_DEL_HEROE = List.of(
            List.of("Golpe de tormenta"), List.of("Lanza de los dioses"), List.of("Embate sangriento"));
    static final List<List<String>> ESTRATEGIA_DEL_ENEMIGO = List.of(
            List.of("Golpe con escudo"), List.of("Mano de piedra"));
    static final Map<String, Integer> COSTOS = Map.of("Golpe de tormenta", 6, "Lanza de los dioses", 4,
            "Embate sangriento", 4, "Golpe con escudo", 2, "Mano de piedra", 4);

    private EscenarioDeCombate() {
    }

    static List<Rival> rivales() {
        return List.of(
                new Rival("Guardian", TipoDeRival.REGULAR, "Guerrero Tanque", 8, 70, 11, 10, ESTRATEGIA_DEL_ENEMIGO, null),
                new Rival("Sombra", TipoDeRival.REGULAR, "Guerrero Tanque", 8, 70, 11, 10, ESTRATEGIA_DEL_ENEMIGO, null),
                new Rival("Eterno", TipoDeRival.JEFE, "Guerrero Tanque", 8, 90, 11, 10, ESTRATEGIA_DEL_ENEMIGO, null));
    }

    static Simulacion simular(DecisorDeTurno decisor) {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 9;
        motor.danoDeLosEnemigos = 3;
        motor.costos.putAll(COSTOS);
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, motor, dado -> 1);
        return simulador.simular(EJECUCION, MISION, HEROE, PERFIL, ESTRATEGIA_DEL_HEROE, rivales(),
                new AzarConSemilla(42L), 42L);
    }

    /** Las acciones que las rotaciones del bando del actor permiten, mas el ataque basico de respaldo. */
    static Set<String> accionesPermitidas(EventoDeCombate e) {
        Set<String> permitidas = new HashSet<>(List.of(DecisionDeTurno.ATAQUE_BASICO));
        (e.actor().lado() == EventoDeCombate.Lado.HEROE ? ESTRATEGIA_DEL_HEROE : ESTRATEGIA_DEL_ENEMIGO)
                .forEach(permitidas::addAll);
        return permitidas;
    }
}
