package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Matriz de acciones — auditoria del 4-oct (P0, «cuando ataco me hago dano»).
 *
 * <p>Cada prototipo en nivel 8 (todas sus acciones aprendidas) y con las ocho
 * epicas, en un 2 contra 2, prueba CADA accion contra CADA objetivo posible: si
 * mismo, su companero, los dos rivales y ninguno. Para cada casilla:
 * <ul>
 *   <li>una accion que va a un RIVAL contra uno mismo o un companero se
 *       rechaza con {@code OBJETIVO_INVALIDO} (§6.1.3), y no llega a tirar;</li>
 *   <li>una accion que se juega no le quita vida a quien la lanza salvo un
 *       {@code REFLEJO} declarado, y no le quita vida a su companero;</li>
 *   <li>un ataque que se juega tiene por objetivo a un rival.</li>
 * </ul>
 */
class MatrizDeAccionesTest {

    private static final List<String> EPICAS = List.of("Golpe de defensa", "Segundo impulso", "Luz cegadora",
            "Frío concentrado", "Toma y lleva", "Intimidación sangrienta", "Té changua", "Reanimador 3000");

    private final CatalogoDePrueba catalogo = new CatalogoDePrueba();
    private final MotorDeAcciones motor = new MotorDeAcciones(catalogo, IndiceNormal.porOmision());

    static Stream<String> prototipos() {
        return SimuladorDeCombates.PROTOTIPOS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("prototipos")
    @DisplayName("cada acción contra cada objetivo: ni auto-daño, ni fuego amigo, ni un ataque a uno mismo")
    void matriz(String prototipo) {
        List<Contendiente> mesa = sentados(prototipo);
        Contendiente yo = mesa.get(0);
        FichaDeCombate ficha = catalogo.ficha(yo.prototipo(), yo.nivel());
        List<String> objetivos = Arrays.asList("yo", "aliado", "rival1", "rival2", null);

        int jugadas = 0;
        int rechazosPorObjetivo = 0;
        for (EstadoDeAccion accion : motor.accionesDe(yo, ficha)) {
            if (!accion.disponible()) {
                continue;
            }
            Plan plan = motor.reglamento().planPara(accion.codigo(), yo, ficha);
            for (String objetivo : objetivos) {
                boolean aUnoDeLosSuyos = "yo".equals(objetivo) || "aliado".equals(objetivo);
                for (int semilla = 1; semilla <= 3; semilla++) {
                    String casilla = prototipo + " · " + accion.codigo() + " → " + objetivo + " (semilla " + semilla + ")";
                    ResultadoDeAccion r;
                    try {
                        r = motor.resolver(new SolicitudDeAccion(accion.codigo(), "yo", objetivo, true, mesa),
                                new Random(semilla));
                    } catch (AccionNoPermitida rechazo) {
                        if (plan.objetivo() == Plan.Objetivo.RIVAL && aUnoDeLosSuyos) {
                            assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo(), casilla);
                            rechazosPorObjetivo++;
                        }
                        continue;
                    }
                    if (plan.objetivo() == Plan.Objetivo.RIVAL && aUnoDeLosSuyos) {
                        fail("Se jugó un golpe contra los suyos: " + casilla);
                    }
                    jugadas++;
                    comprobar(r, casilla);
                }
            }
        }
        assertTrue(jugadas > 0, "alguna jugada válida para " + prototipo);
        if (ficha.estadisticas().ataca()) {
            assertTrue(rechazosPorObjetivo > 0, "los ataques contra sí mismo o el compañero se rechazan");
        }
    }

    private static void comprobar(ResultadoDeAccion r, String casilla) {
        boolean reflejo = false;
        for (Evento e : r.eventos()) {
            if (e.tipo() == TipoDeEvento.DANO) {
                assertTrue(e.combatiente().startsWith("rival"), "daño a uno de los suyos en " + casilla + ": " + e);
            }
            if (e.tipo() == TipoDeEvento.REFLEJO && e.combatiente().equals("yo")) {
                reflejo = true;
            }
        }
        boolean perdioVida = r.afectados().stream().anyMatch(a -> a.id().equals("yo") && a.diferencia() < 0);
        assertTrue(!perdioVida || reflejo, "quien actúa perdió vida sin un reflejo declarado en " + casilla);
        assertTrue(r.afectados().stream().noneMatch(a -> a.id().equals("aliado") && a.diferencia() < 0),
                "el compañero perdió vida en " + casilla);
        if (r.tipo() == TipoDeAccion.ATAQUE) {
            assertTrue(r.objetivo() != null && r.objetivo().startsWith("rival"),
                    "un ataque va a un rival en " + casilla + ", fue a " + r.objetivo());
        }
    }

    /**
     * Los cuatro a la mesa, con las estadisticas ya resueltas por el motor: el
     * companero a media vida (para que sanarlo cambie algo) y poder de sobra.
     */
    private List<Contendiente> sentados(String prototipo) {
        List<Contendiente> crudos = List.of(
                new Contendiente("yo", 1, prototipo, 8, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, Map.of(),
                        List.of(), List.of(), EPICAS, null),
                new Contendiente("aliado", 1, "Guerrero Armas", 8, null, 176, Integer.MAX_VALUE, 0, Map.of(),
                        List.of(), List.of(), List.of(), null),
                new Contendiente("rival1", 2, "Guerrero Tanque", 8, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                        Map.of(), List.of(), List.of(), List.of(), null),
                new Contendiente("rival2", 2, "Mago Fuego", 8, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                        Map.of(), List.of(), List.of(), List.of(), null));
        // Empezar el turno de alguien que no es «yo» resuelve a todos sin tocar su poder.
        return new ArrayList<>(motor.iniciarTurno(new SolicitudDeTurno("rival2", true, crudos), new Random(0))
                .combatientes());
    }
}
