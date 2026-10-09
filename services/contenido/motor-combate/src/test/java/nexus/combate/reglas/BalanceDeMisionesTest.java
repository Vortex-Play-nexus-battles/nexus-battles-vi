package nexus.combate.reglas;

import nexus.combate.reglas.SimuladorDeMisiones.Campana;
import nexus.combate.reglas.SimuladorDeMisiones.Juego;
import nexus.combate.reglas.SimuladorDeMisiones.Mision;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El equilibrio de las misiones publicadas, con el motor de combate real —
 * auditoria del 4-oct, cambio autorizado n.º 2 (D-42).
 *
 * <p>Lee las semillas del servicio de misiones ({@code misiones-de-progresion.json}
 * y {@code misiones-del-documento.json}) y juega cada mision entera, como la
 * simulacion de ese servicio ({@link SimuladorDeMisiones}). Si alguien cambia
 * una semilla, la integracion continua de motor-combate corre (ci.yml lo
 * engancha) y esto dice si la mision sigue jugable.
 *
 * <p>Lo que se exige:
 * <ul>
 *   <li>ninguna mision recomienda un nivel fuera de 1..8 (§6.1.1);</li>
 *   <li>un heroe de nivel 1 tiene una mision que gana mas veces de las que
 *       pierde;</li>
 *   <li>en su nivel recomendado ninguna mision se gana ni se pierde siempre;</li>
 *   <li>un nivel por debajo cuesta mas que en el recomendado;</li>
 *   <li>un jugador nuevo llega al nivel 8 jugando la mision que le toca, sin
 *       quedarse sin mision.</li>
 * </ul>
 *
 * <p>El heroe juega como un jugador con rotaciones (su ataque mas fuerte al
 * alcance). Los enemigos juegan las estrategias predefinidas de HU-SIM-004
 * ({@code estrategias-de-enemigos.json}), que son las que juega el servicio de
 * misiones: medir el equilibrio con otra politica seria medirlo sobre otro
 * juego. Los seis prototipos que atacan entran en las cuentas; los
 * sanadores no, porque «a un sanador le esta vedado infligir dano» (§6.1.1) y
 * solo no puede ganar ningun combate. El Guerrero Tanque si entra: casi todos
 * sus golpes caen en «sin efecto» (su fila de la Tabla 21), y aun asi progresa
 * con la experiencia de los enemigos que derrota.
 */
class BalanceDeMisionesTest {

    private static final List<String> ATACANTES = SimuladorDeCombates.PROTOTIPOS.subList(0, 6);
    private static final int EJECUCIONES_POR_PROTOTIPO = 12;

    private static List<Mision> catalogo;
    private final SimuladorDeMisiones sim = new SimuladorDeMisiones();

    @BeforeAll
    static void leerSemillas() {
        catalogo = SimuladorDeMisiones.semillas("misiones-de-progresion.json", "misiones-del-documento.json");
    }

    @Test
    @DisplayName("hay misiones de nivel 1 a 8 y ninguna recomienda un nivel que no existe (§6.1.1)")
    void nivelesDeUnoAOcho() {
        assertFalse(catalogo.isEmpty(), "no se leyó ninguna semilla de " + SimuladorDeMisiones.SEMILLAS);
        for (Mision m : catalogo) {
            assertNotNull(m.nivelRecomendado(), m.id() + " sin nivel recomendado");
            assertTrue(m.nivelRecomendado() >= 1 && m.nivelRecomendado() <= 8,
                    m.id() + " recomienda el nivel " + m.nivelRecomendado());
        }
        for (int nivel = 1; nivel <= 8; nivel++) {
            final int n = nivel;
            assertTrue(catalogo.stream().anyMatch(m -> m.nivelRecomendado() == n), "ninguna mision de nivel " + n);
        }
    }

    @Test
    @DisplayName("un héroe de nivel 1 tiene una misión sin requisitos que gana más veces de las que pierde")
    void nivelUnoViable() {
        Mision primera = catalogo.stream()
                .filter(m -> m.nivelRecomendado() == 1 && m.requisitos().isEmpty())
                .findFirst().orElseThrow(() -> new AssertionError("no hay mision de nivel 1 sin requisitos"));
        List<String> informe = new ArrayList<>();
        int ganadasDeLosQuePegan = 0;
        for (String prototipo : ATACANTES) {
            int ganadas = ganadas(primera, prototipo, 1, 20);
            informe.add(prototipo + " " + ganadas * 5 + " %");
            if (!prototipo.equals("Guerrero Tanque")) {
                ganadasDeLosQuePegan += ganadas;
                assertTrue(ganadas >= 10, prototipo + " gana " + ganadas + " de 20 en «" + primera.nombre() + "»");
            }
        }
        System.out.println("Nivel 1, «" + primera.nombre() + "»: " + informe);
        assertTrue(ganadasDeLosQuePegan >= 60, "de 100 ganan " + ganadasDeLosQuePegan);
    }

    /**
     * El Guerrero Tanque es el heroe del kit PROVISIONAL de DEV (D-29), asi que
     * es con el que empieza todo jugador nuevo alli. Por su fila de la Tabla 21
     * casi no hace dano: en su nivel no gana la mision de nivel 1 (verificacion
     * del 4-oct: 0 %, en el banco y en AWS DEV). Lo que se exige es que no se
     * quede atascado: con la experiencia de los enemigos que derrota al fallar
     * sube al nivel 2 en pocas ejecuciones, y entonces la gana. Si el PO cambia
     * el kit (D-29), esta prueba sigue valiendo para quien elija el Tanque.
     */
    @Test
    @DisplayName("el Guerrero Tanque del kit de DEV no se atasca en la misión de nivel 1: sube al 2 fallando y entonces la gana")
    void tanqueDelKitNoSeAtasca() {
        Mision primera = catalogo.stream()
                .filter(m -> m.nivelRecomendado() == 1 && m.requisitos().isEmpty())
                .findFirst().orElseThrow(() -> new AssertionError("no hay mision de nivel 1 sin requisitos"));
        int enNivelUno = ganadas(primera, "Guerrero Tanque", 1, 20);
        int enNivelDos = ganadas(primera, "Guerrero Tanque", 2, 20);
        Campana c = sim.campana(catalogo, "Guerrero Tanque", Juego.ROTACION, 80, "Guerrero Tanque".hashCode());
        int hastaNivelDos = 0;
        while (hastaNivelDos < c.recorrido().size() && c.recorrido().get(hastaNivelDos).endsWith("@1-")) {
            hastaNivelDos++;
        }
        System.out.println("Guerrero Tanque en «" + primera.nombre() + "»: nivel 1 " + enNivelUno * 5 + " %, nivel 2 "
                + enNivelDos * 5 + " %; " + hastaNivelDos + " intentos fallidos antes de subir al nivel 2");
        final int intentos = hastaNivelDos;
        assertAll(
                () -> assertTrue(intentos <= 6, "el Tanque necesita " + intentos + " intentos para subir al nivel 2"),
                () -> assertTrue(enNivelDos >= 15, "en nivel 2 gana " + enNivelDos + " de 20"));
    }

    @Test
    @DisplayName("en su nivel recomendado ninguna misión se gana ni se pierde siempre, y un nivel menos cuesta más")
    void niSiempreNiNunca() {
        List<String> informe = new ArrayList<>();
        for (Mision m : catalogo) {
            int nivel = m.nivelRecomendado();
            int total = ATACANTES.size() * EJECUCIONES_POR_PROTOTIPO;
            int enSuNivel = 0;
            int unoMenos = 0;
            for (String prototipo : ATACANTES) {
                enSuNivel += ganadas(m, prototipo, nivel, EJECUCIONES_POR_PROTOTIPO);
                if (nivel > 1) {
                    unoMenos += ganadas(m, prototipo, nivel - 1, EJECUCIONES_POR_PROTOTIPO);
                }
            }
            double tasa = (double) enSuNivel / total;
            informe.add(String.format(Locale.ROOT, "%s (nivel %d) %.0f %%%s", m.id(), nivel, 100 * tasa,
                    nivel > 1 ? String.format(Locale.ROOT, ", un nivel menos %.0f %%", 100.0 * unoMenos / total)
                            : ""));
            final int ganadasEnSuNivel = enSuNivel;
            final int ganadasUnoMenos = unoMenos;
            assertAll(m.id(),
                    () -> assertTrue(tasa >= 0.15, m.id() + ": casi imposible en su nivel (" + tasa + ")"),
                    () -> assertTrue(tasa <= 0.90, m.id() + ": casi regalada en su nivel (" + tasa + ")"),
                    () -> assertTrue(nivel == 1 || ganadasUnoMenos < ganadasEnSuNivel,
                            m.id() + ": un nivel menos no cuesta más (" + ganadasUnoMenos + " contra "
                                    + ganadasEnSuNivel + ")"));
        }
        System.out.println("Misiones en su nivel recomendado: " + informe);
    }

    @Test
    @DisplayName("el equilibrio se mide con las estrategias predefinidas de los enemigos, las que juega el servicio de misiones")
    void seMideConLasEstrategiasReales() {
        for (String prototipo : ATACANTES) {
            for (int nivel : new int[] {1, 4, 8}) {
                assertTrue(sim.estrategiaPara(prototipo, nivel).isPresent(),
                        "sin estrategia predefinida para " + prototipo + " en el nivel " + nivel
                                + ": el equilibrio se estaria midiendo con otra politica");
            }
        }
    }

    /** Informativa: la tasa de cada mision con la politica de antes de HU-SIM-004 y con las estrategias predefinidas. */
    @Test
    @DisplayName("tasa de cada misión con la política anterior y con las estrategias predefinidas (informe)")
    void comparacionConLaPoliticaAnterior() {
        SimuladorDeMisiones anterior = SimuladorDeMisiones.conPoliticaAnterior();
        List<String> informe = new ArrayList<>();
        for (Mision m : catalogo) {
            int total = ATACANTES.size() * EJECUCIONES_POR_PROTOTIPO;
            int conAnterior = 0;
            int conEstrategias = 0;
            for (String prototipo : ATACANTES) {
                conAnterior += ganadas(anterior, m, prototipo, m.nivelRecomendado(), EJECUCIONES_POR_PROTOTIPO);
                conEstrategias += ganadas(sim, m, prototipo, m.nivelRecomendado(), EJECUCIONES_POR_PROTOTIPO);
            }
            informe.add(String.format(Locale.ROOT, "%s (nivel %d): politica anterior %.0f %%, estrategias %.0f %%",
                    m.id(), m.nivelRecomendado(), 100.0 * conAnterior / total, 100.0 * conEstrategias / total));
        }
        System.out.println("Politica anterior contra estrategias predefinidas: " + informe);
    }

    @Test
    @DisplayName("un jugador nuevo llega al nivel 8 jugando la misión que le toca, sin quedarse sin misión")
    void deUnoAOcho() {
        List<String> informe = new ArrayList<>();
        for (String prototipo : ATACANTES) {
            Campana c = sim.campana(catalogo, prototipo, Juego.ROTACION, 80, prototipo.hashCode());
            informe.add(prototipo + ": nivel " + c.nivelAlcanzado() + " en " + c.ejecuciones() + " ejecuciones ("
                    + c.exitos() + " completadas)");
            assertAll(prototipo,
                    () -> assertFalse(c.callejonSinSalida(), prototipo + " se quedó sin misión: " + c.recorrido()),
                    () -> assertTrue(c.nivelAlcanzado() == 8, prototipo + " se quedó en el nivel "
                            + c.nivelAlcanzado() + ": " + c.recorrido()));
        }
        System.out.println("De nivel 1 a 8: " + informe);
    }

    private int ganadas(Mision m, String prototipo, int nivel, int veces) {
        return ganadas(sim, m, prototipo, nivel, veces);
    }

    private int ganadas(SimuladorDeMisiones simulador, Mision m, String prototipo, int nivel, int veces) {
        int ganadas = 0;
        for (int i = 0; i < veces; i++) {
            if (simulador.jugar(m, prototipo, nivel, Juego.ROTACION, 7919L * i + 31L * nivel + prototipo.hashCode())
                    .exito()) {
                ganadas++;
            }
        }
        return ganadas;
    }
}
