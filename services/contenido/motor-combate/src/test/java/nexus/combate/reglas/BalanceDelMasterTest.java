package nexus.combate.reglas;

import nexus.combate.reglas.SimuladorDeMisiones.Juego;
import nexus.combate.reglas.SimuladorDeMisiones.Mision;
import nexus.combate.reglas.SimuladorDeMisiones.ReglaDelMaster;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El equilibrio del Master reforzado con el motor de combate real (HU-SIM-006, decision del PO del 5-oct).
 *
 * <p>La decision: un heroe del nivel recomendado de la mision debe ganarle al Master aproximadamente la mitad de las
 * veces, medido en un duelo 1 contra 1 con el heroe a vida completa. El docente dijo que el balance no se califica:
 * basta una regla simple, documentada y con prueba. La regla vive en
 * {@code services/contenido/misiones/src/main/resources/semilla/refuerzo-del-master.json}, que lee el servicio de
 * misiones ({@code ReglaDelMaster}) y lee este simulador como dato: aqui se prueba que, con esa regla, el duelo queda
 * cerca del 50 %.
 *
 * <p>Que se mide, y por que asi:
 * <ul>
 *   <li>Para cada mision de la semilla, un heroe de su nivel recomendado contra el Master de su mismo prototipo (el
 *       Master afin de la Tabla 20) en el nivel del heroe mas dos, con tope 8 (RG-107), con la vida y la defensa de la
 *       regla, el piso del criterio 1 contra los regulares de ESA mision ({@code RefuerzoDeMaster}) y su epica, que
 *       juega en cuanto puede.</li>
 *   <li>Cuentan los seis prototipos que atacan, como en {@link BalanceDeMisionesTest}; los sanadores no combaten
 *       solos. La cifra es el promedio de los seis: el Guerrero Tanque casi no hace dano (Tabla 21) y casi nunca gana,
 *       y el Mago de Fuego casi siempre; eso es del reparto del juego y no de la regla del Master, que no distingue
 *       prototipos.</li>
 *   <li>Semillas fijas: la prueba es determinista, no depende del azar de la corrida.</li>
 * </ul>
 */
class BalanceDelMasterTest {

    private static final List<String> ATACANTES = SimuladorDeCombates.PROTOTIPOS.subList(0, 6);

    /** Duelos por prototipo y mision: 6 x 60 = 360 por mision, con un error de unos 2,6 puntos. */
    private static final int DUELOS_POR_PROTOTIPO = 60;

    /** La banda «aproximadamente la mitad», con holgura de sobra para el error de la medicion. */
    private static final double TASA_MINIMA = 0.35;
    private static final double TASA_MAXIMA = 0.65;

    private static List<Mision> catalogo;
    private static ReglaDelMaster regla;
    private final SimuladorDeMisiones sim = new SimuladorDeMisiones();

    @BeforeAll
    static void leerSemillas() {
        catalogo = SimuladorDeMisiones.semillas("misiones-de-progresion.json", "misiones-del-documento.json");
        regla = SimuladorDeMisiones.reglaDelMaster();
    }

    @Test
    @DisplayName("la regla publicada trae una fraccion por cada nivel del heroe, de 1 a 8, mayor que cero y sin pasar de 1")
    void laReglaTrae8Fracciones() {
        assertFalse(regla.version().isBlank(), "la regla no dice su version");
        assertEquals(8, regla.fraccionPorNivelDelHeroe().size(), "debe traer una fraccion por nivel: "
                + regla.fraccionPorNivelDelHeroe());
        for (int nivel = 1; nivel <= 8; nivel++) {
            double fraccion = regla.fraccionPara(nivel);
            assertTrue(fraccion > 0 && fraccion <= 1.0, "el nivel " + nivel + " trae la fraccion " + fraccion
                    + ": un Master no pelea con mas que las estadisticas completas de su prototipo");
        }
    }

    @Test
    @DisplayName("duelosContraElMaster: un héroe del nivel recomendado de cada misión le gana al Máster aproximadamente la mitad de las veces")
    void duelosContraElMaster() {
        List<String> informe = new ArrayList<>();
        List<Executable> comprobaciones = new ArrayList<>();
        for (Mision m : catalogo) {
            int nivel = m.nivelRecomendado();
            // El Master afin de cada prototipo (Tabla 20) y los propios de la mision, que son los que mas aparecen
            // (el Templo trae a «Sombra del Olvido» con el 15 %; los afines, del 0,01 al 0,1 %).
            medir(m, nivel, "Máster afín", null, informe, comprobaciones);
            for (SimuladorDeMisiones.MasterDePrueba propio : m.masters()) {
                medir(m, nivel, "«" + propio.nombre() + "» (" + propio.prototipo() + ")", propio, informe,
                        comprobaciones);
            }
        }
        System.out.println("Duelos contra el Máster (regla " + regla.version() + "): ");
        informe.forEach(l -> System.out.println("  " + l));
        assertAll(comprobaciones);
    }

    /** Los seis prototipos contra un Master; {@code propio} nulo = el afin al prototipo del heroe. */
    private void medir(Mision m, int nivel, String quien, SimuladorDeMisiones.MasterDePrueba propio,
                       List<String> informe, List<Executable> comprobaciones) {
        StringBuilder porPrototipo = new StringBuilder();
        int ganados = 0;
        for (String prototipo : ATACANTES) {
            SimuladorDeMisiones.MasterDePrueba master = propio != null ? propio : SimuladorDeMisiones.masterAfin(prototipo);
            int g = 0;
            for (int i = 0; i < DUELOS_POR_PROTOTIPO; i++) {
                if (sim.heroeGanaAlMaster(m, prototipo, nivel, master, regla,
                        104729L * i + 31L * nivel + prototipo.hashCode())) {
                    g++;
                }
            }
            ganados += g;
            porPrototipo.append(String.format(Locale.ROOT, " %s %.0f %%;", prototipo, 100.0 * g / DUELOS_POR_PROTOTIPO));
        }
        double tasa = (double) ganados / (ATACANTES.size() * DUELOS_POR_PROTOTIPO);
        informe.add(String.format(Locale.ROOT, "%s (nivel %d), %s: %.1f %% [%s ]", m.id(), nivel, quien, 100 * tasa,
                porPrototipo));
        comprobaciones.add(() -> assertTrue(tasa >= TASA_MINIMA && tasa <= TASA_MAXIMA,
                m.id() + ", " + quien + ": el héroe de nivel " + nivel + " le gana el "
                        + String.format(Locale.ROOT, "%.1f", 100 * tasa) + " % de las veces; se espera entre "
                        + (int) (100 * TASA_MINIMA) + " y " + (int) (100 * TASA_MAXIMA) + " %"));
    }

    @Test
    @DisplayName("«Sombra del Olvido» pelea con su épica, «Velo de Sombras»: el motor ya la conoce y el balance debe medirla")
    void sombraDelOlvidoPeleaConSuEpica() {
        Mision templo = catalogo.stream().filter(m -> m.id().equals("templo-olvidado")).findFirst()
                .orElseThrow(() -> new AssertionError("la semilla no trae el Templo"));
        SimuladorDeMisiones.MasterDePrueba sombra = templo.masters().stream()
                .filter(m -> m.nombre().equals("Sombra del Olvido")).findFirst()
                .orElseThrow(() -> new AssertionError("el Templo no trae a Sombra del Olvido"));

        assertEquals("Velo de Sombras", sombra.epica(), "si el simulador no le da su épica, el balance mide un "
                + "Máster que no es el del juego");
    }

    @Test
    @DisplayName("hay misiones de nivel 1 a 8 para medir: ningún nivel queda sin duelo")
    void haySemillasParaCadaNivel() {
        for (int nivel = 1; nivel <= 8; nivel++) {
            final int n = nivel;
            assertTrue(catalogo.stream().anyMatch(m -> m.nivelRecomendado() == n), "ninguna mision de nivel " + n);
        }
    }

    /**
     * Solo para informar, con un piso minimo: el Templo completo, con un heroe de nivel 8, sin Master y con el
     * Master de la mision («Sombra del Olvido», Pícaro Veneno, con su epica «Velo de Sombras», que el motor ya
     * conoce). La unica fuente de epicas (RG-085) no puede quedar cerrada
     * en la practica: antes de la regla, con el Master aparecido, el exito del Templo caia al 1,3 %.
     */
    @Test
    @DisplayName("el Templo completo con héroe de nivel 8: con el Máster aparecido sigue habiendo una oportunidad real (informe)")
    void elTemploConYSinMaster() {
        Mision templo = catalogo.stream().filter(m -> m.id().equals("templo-olvidado")).findFirst()
                .orElseThrow(() -> new AssertionError("la semilla no trae el Templo"));
        SimuladorDeMisiones.MasterDePrueba sombra = templo.masters().getFirst();
        int corridas = 60;
        int sinMaster = 0;
        int conMaster = 0;
        List<String> informe = new ArrayList<>();
        for (String prototipo : ATACANTES) {
            int sin = 0;
            int con = 0;
            for (int i = 0; i < corridas; i++) {
                long semilla = 7919L * i + prototipo.hashCode();
                if (sim.jugar(templo, prototipo, 8, Juego.ROTACION, semilla).exito()) {
                    sin++;
                }
                if (sim.jugar(templo, prototipo, 8, Juego.ROTACION, semilla, sombra, regla).exito()) {
                    con++;
                }
            }
            sinMaster += sin;
            conMaster += con;
            informe.add(String.format(Locale.ROOT, "%s: sin Máster %.0f %%, con Máster %.0f %%", prototipo,
                    100.0 * sin / corridas, 100.0 * con / corridas));
        }
        double tasaSin = (double) sinMaster / (corridas * ATACANTES.size());
        double tasaCon = (double) conMaster / (corridas * ATACANTES.size());
        System.out.println(String.format(Locale.ROOT,
                "Templo, héroe de nivel 8: sin Máster %.1f %%, con Máster %.1f %% (regla %s). %s", 100 * tasaSin,
                100 * tasaCon, regla.version(), informe));
        assertTrue(tasaCon <= tasaSin, "un Máster de mas no puede facilitar el Templo");
    }
}
