package nexus.misiones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.MasterDeMision;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.ParametrosDeRecompensa;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-SIM-005, criterio 3: «dado cualquier modo de juego distinto de misiones,
 * cuando se procesa, entonces los enemigos Master no aparecen».
 *
 * <p>La otra mitad de esa frase vive fuera de este servicio y se prueba alla:
 * ni motor-combate ni salas-partidas conocen la nocion de Master, y las
 * partidas en linea arman a la maquina con un heroe del catalogo sin epicas
 * ({@code CombateSinMasterTest} en salas-partidas). Aqui se fija lo que
 * garantiza el criterio desde el lado de las misiones: los Master solo
 * existen en el plan de combate de una mision, y solo cuando la tirada los
 * saca ({@code TiradaDeMasters}, seccion 7.8.4). Si dejaran de salir de esa
 * tirada, o salieran sin ella, estas pruebas fallan.
 *
 * <p>Se prueba por comportamiento: se simula la mision entera y se mira lo que
 * quedo en el resultado y en los eventos de combate, no el texto del codigo.
 */
@DisplayName("HU-SIM-005 C3 · el Master solo aparece en una mision, y solo si la tirada lo saca")
class MasterSoloEnMisionesTest {

    private static final Instant INICIO = Instant.parse("2026-10-05T10:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";

    private final AtomicReference<Instant> ahora = new AtomicReference<>(INICIO);
    private final AtomicLong semilla = new AtomicLong(7);
    private final Clock reloj = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    };

    private Dobles.Ejecuciones ejecuciones;
    private Dobles.Eventos eventos;
    private ParametrosDeMisiones parametros;
    private Dobles.Heroes heroes;
    private Dobles.Inventario inventario;
    private Dobles.Productos productos;
    private Dobles.Motor motor;

    @BeforeEach
    void preparar() {
        ejecuciones = new Dobles.Ejecuciones();
        eventos = new Dobles.Eventos();
        inventario = new Dobles.Inventario().conHeroe("h-1", JUGADOR, "p-armas", true);
        productos = new Dobles.Productos();
        productos.prototipos.put("p-armas", "Guerrero Armas");
        heroes = new Dobles.Heroes();
        // Enemigos de poca vida: el heroe los derrota a todos, Master incluido, y asi el
        // resultado cuenta cada Master que el plan trajo.
        heroes.vidaDeLosEnemigos = 5;
        motor = new Dobles.Motor();
        parametros = new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), 20, true, true, null,
                null, new ParametrosDeRecompensa(Map.of(), Map.of(), false));
    }

    /**
     * Matricula y simula la mision con la simulacion de verdad, con las semillas
     * y el catalogo dados.
     */
    private Ejecucion simular(String misionId, List<nexus.misiones.dominio.Mision> misiones,
                              List<nexus.misiones.dominio.EpicaDeTabla20> tabla20, long laSemilla) {
        // Dobles nuevos en cada simulacion: el heroe de la anterior quedaria reservado.
        preparar();
        Dobles.Catalogo catalogo = new Dobles.Catalogo(misiones, tabla20);
        semilla.set(laSemilla);
        ahora.set(INICIO);
        Ejecucion matriculada = new MatricularHeroe(catalogo, ejecuciones, new Dobles.Estrategias(), inventario,
                productos, heroes, parametros, reloj, semilla::get)
                .matricular(JUGADOR, new SolicitudDeMatricula(misionId, "h-1", List.of(), null, null))
                .ejecucion();
        ahora.set(INICIO.plus(Duration.ofHours(200)));
        SimularEjecucion simulacion = new SimularEjecucion(catalogo, ejecuciones, eventos, heroes, motor,
                new PerfilDeCombateDelHeroe(inventario, productos, heroes),
                new RotacionesPorDefectoDeEnemigos(heroes), parametros, reloj);
        return simulacion.simular(matriculada).orElseThrow();
    }

    private boolean huboMasterEnLosEventos(Ejecucion ejecucion) {
        return eventos.de(ejecucion.id()).stream()
                .anyMatch(e -> e.actor().lado() == EventoDeCombate.Lado.MASTER);
    }

    @Test
    @DisplayName("criterio3_sinMasterEnLaMisionNiAfinAlHeroeNoApareceNingunoAunqueLaMisionSeaLarga")
    void criterio3_sinMasterEnLaMisionNiAfinAlHeroeNoApareceNingunoAunqueLaMisionSeaLarga() {
        // Una exploracion de tres dias tira tres veces; sin candidatos no hay nada que sacar.
        for (long s = 1; s <= 25; s++) {
            Ejecucion terminada = simular("exp-larga", List.of(Misiones.exploracion("exp-larga", 72)), List.of(), s);

            assertThat(terminada.resultado().masters()).as("semilla %d", s).isEmpty();
            assertThat(huboMasterEnLosEventos(terminada)).as("semilla %d", s).isFalse();
        }
    }

    @Test
    @DisplayName("criterio3_unMasterConProbabilidadCeroNuncaApareceEnNingunaTirada")
    void criterio3_unMasterConProbabilidadCeroNuncaApareceEnNingunaTirada() {
        MasterDeMision imposible = new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 0.0,
                Misiones.VELO_DE_SOMBRAS);

        for (long s = 1; s <= 25; s++) {
            Ejecucion terminada = simular("exp-larga",
                    List.of(Misiones.exploracion("exp-larga", 72, imposible)), List.of(), s);

            assertThat(terminada.resultado().masters()).as("semilla %d", s).isEmpty();
            assertThat(huboMasterEnLosEventos(terminada)).as("semilla %d", s).isFalse();
        }
    }

    @Test
    @DisplayName("criterio3_controlConProbabilidadUnoElPlanDeLaMisionSiTraeAlMasterYPelea")
    void criterio3_controlConProbabilidadUnoElPlanDeLaMisionSiTraeAlMasterYPelea() {
        // Contraprueba de las dos de arriba: si la simulacion nunca pudiera traer un Master
        // (por ejemplo, si el plan lo descartara), los dos «no aparece» pasarian por nada.
        MasterDeMision seguro = new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 1.0,
                Misiones.VELO_DE_SOMBRAS);

        Ejecucion terminada = simular("historia-corta",
                List.of(Misiones.historia("historia-corta", List.of(seguro))), List.of(), 3);

        assertThat(terminada.resultado().masters()).hasSize(1);
        assertThat(terminada.resultado().masters().getFirst().nombre()).isEqualTo("Sombra del Olvido");
        assertThat(huboMasterEnLosEventos(terminada)).isTrue();
    }
}
