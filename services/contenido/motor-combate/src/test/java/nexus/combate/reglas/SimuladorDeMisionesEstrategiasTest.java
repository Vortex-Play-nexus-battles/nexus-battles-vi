package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import nexus.combate.reglas.SimuladorDeMisiones.EstrategiaPredefinida;
import nexus.combate.reglas.SimuladorDeMisiones.Jugada;
import nexus.combate.reglas.SimuladorDeMisiones.Juego;
import nexus.combate.reglas.SimuladorDeMisiones.Mision;
import nexus.combate.reglas.SimuladorDeMisiones.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El simulador de misiones juega a los enemigos con las estrategias
 * predefinidas de HU-SIM-004 ({@code estrategias-de-enemigos.json}, leidas como
 * datos), y no con su ataque mas fuerte al alcance. Si no, el equilibrio de
 * D-42 se mediria sobre otro juego que el que de verdad juega el servicio de
 * misiones.
 *
 * <p>La regla es la de rotaciones del heroe (7.8.5): en cada turno, la primera
 * rotacion cuyo paso sea viable (poder y recarga), si no la siguiente, si no el
 * ataque basico.
 */
class SimuladorDeMisionesEstrategiasTest {

    private static final String ARCHIVO = "estrategias-de-enemigos.json";

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());
    private final SimuladorDeMisiones sim = new SimuladorDeMisiones();

    // ---------------------------------------------------------------- los datos

    @Test
    @DisplayName("lee las 24 estrategias del archivo de la semilla: una por prototipo y tramo de nivel")
    void leeLasEstrategias() {
        List<EstrategiaPredefinida> estrategias = SimuladorDeMisiones.estrategias(ARCHIVO);

        assertEquals(24, estrategias.size());
        EstrategiaPredefinida tanque = estrategias.stream().filter(e -> e.id().equals("guerrero-tanque-n8"))
                .findFirst().orElseThrow();
        assertEquals("Guerrero Tanque", tanque.prototipo());
        assertEquals(8, tanque.desdeNivel());
        assertEquals(List.of(List.of("Golpe con escudo"), List.of("Defensa feroz"), List.of("Mano de piedra")),
                tanque.rotaciones());
    }

    @Test
    @DisplayName("sin el archivo no hay estrategias, y el simulador juega a los enemigos como antes")
    void sinArchivo() {
        assertTrue(SimuladorDeMisiones.estrategias("no-existe.json").isEmpty());
        assertTrue(SimuladorDeMisiones.conPoliticaAnterior().estrategiaPara("Mago Fuego", 8).isEmpty());
    }

    @Test
    @DisplayName("el tramo es el de los desbloqueos: 1 a 3, 4 a 7 y 8 en adelante")
    void tramos() {
        assertEquals("mago-fuego-n1", sim.estrategiaPara("Mago Fuego", 1).orElseThrow().id());
        assertEquals("mago-fuego-n1", sim.estrategiaPara("Mago Fuego", 3).orElseThrow().id());
        assertEquals("mago-fuego-n4", sim.estrategiaPara("Mago Fuego", 4).orElseThrow().id());
        assertEquals("mago-fuego-n4", sim.estrategiaPara("Mago Fuego", 7).orElseThrow().id());
        assertEquals("mago-fuego-n8", sim.estrategiaPara("Mago Fuego", 8).orElseThrow().id());
        assertEquals("mago-fuego-n8", sim.estrategiaPara("mago fuego", 8).orElseThrow().id());
        assertTrue(sim.estrategiaPara("Prototipo Inventado", 8).isEmpty());
    }

    // ---------------------------------------------------------------- la regla de rotaciones

    private static final List<String> NADA = List.of();

    /** Una mesa con el heroe y un Mago de Fuego de nivel 8 ya resuelto, con el poder y las cargas que se piden. */
    private List<Contendiente> mesa(int poder, Map<String, Integer> usadas, int turnosJugados) {
        Contendiente heroe = new Contendiente("heroe", null, "Guerrero Armas", 8, null, Integer.MAX_VALUE, 0, 0,
                Map.of(), List.of(), NADA, NADA, null);
        Contendiente mago = new Contendiente("rival", null, "Mago Fuego", 8, null, Integer.MAX_VALUE, 0, 0,
                Map.of(), List.of(), NADA, NADA, null);
        List<Contendiente> resuelta = motor.iniciarTurno(
                new SolicitudDeTurno("rival", false, List.of(heroe, mago)), new Random(1)).combatientes();
        Contendiente rival = resuelta.stream().filter(c -> c.id().equals("rival")).findFirst().orElseThrow()
                .conPoder(poder).conTurnosJugados(turnosJugados);
        for (Map.Entry<String, Integer> uso : usadas.entrySet()) {
            rival = rival.conCarga(uso.getKey(), uso.getValue());
        }
        Contendiente elHeroe = resuelta.stream().filter(c -> c.id().equals("heroe")).findFirst().orElseThrow();
        return List.of(elHeroe, rival);
    }

    private static final List<List<String>> VULCANO_Y_MISILES = List.of(List.of("Vulcano"),
            List.of("Misiles de magma"));

    @Test
    @DisplayName("juega la primera rotación viable, contra el héroe")
    void laPrimeraViable() {
        int[] cursores = new int[2];

        Jugada jugada = sim.jugadaDeEstrategia("rival", mesa(12, Map.of(), 3), VULCANO_Y_MISILES, cursores);

        assertEquals("Vulcano", jugada.accion());
        assertEquals("heroe", jugada.objetivo());
        assertArrayEquals(new int[] {1, 0}, cursores);
    }

    @Test
    @DisplayName("si la primera está en recarga, juega la siguiente")
    void laPrimeraEnRecarga() {
        int[] cursores = new int[2];

        Jugada jugada = sim.jugadaDeEstrategia("rival", mesa(12, Map.of("Vulcano", 3), 3), VULCANO_Y_MISILES,
                cursores);

        assertEquals("Misiles de magma", jugada.accion());
        assertArrayEquals(new int[] {0, 1}, cursores);
    }

    @Test
    @DisplayName("si la primera no alcanza de poder, juega la siguiente")
    void laPrimeraSinPoder() {
        int[] cursores = new int[2];

        // Vulcano cuesta 6 y Misiles de magma 2: con 3 de poder solo alcanzan los misiles.
        Jugada jugada = sim.jugadaDeEstrategia("rival", mesa(3, Map.of(), 3), VULCANO_Y_MISILES, cursores);

        assertEquals("Misiles de magma", jugada.accion());
        assertArrayEquals(new int[] {0, 1}, cursores);
    }

    @Test
    @DisplayName("si ninguna es viable, ataque básico, y ningún cursor avanza")
    void ningunaViable() {
        int[] cursores = new int[2];

        Jugada sinPoder = sim.jugadaDeEstrategia("rival", mesa(0, Map.of(), 3), VULCANO_Y_MISILES, cursores);
        Jugada enRecarga = sim.jugadaDeEstrategia("rival",
                mesa(12, Map.of("Vulcano", 3, "Misiles de magma", 3), 3), VULCANO_Y_MISILES, cursores);

        assertEquals(Reglamento.ATAQUE_BASICO, sinPoder.accion());
        assertEquals("heroe", sinPoder.objetivo());
        assertEquals(Reglamento.ATAQUE_BASICO, enRecarga.accion());
        assertArrayEquals(new int[] {0, 0}, cursores);
    }

    @Test
    @DisplayName("una rotación de varios pasos avanza su cursor y da la vuelta al terminar")
    void elCursorDaLaVuelta() {
        int[] cursores = new int[1];
        List<List<String>> unaDeDosPasos = List.of(List.of("Misiles de magma", "Vulcano"));

        String primera = sim.jugadaDeEstrategia("rival", mesa(12, Map.of(), 3), unaDeDosPasos, cursores).accion();
        String segunda = sim.jugadaDeEstrategia("rival", mesa(12, Map.of(), 3), unaDeDosPasos, cursores).accion();
        String tercera = sim.jugadaDeEstrategia("rival", mesa(12, Map.of(), 3), unaDeDosPasos, cursores).accion();

        assertEquals(List.of("Misiles de magma", "Vulcano", "Misiles de magma"), List.of(primera, segunda, tercera));
        assertArrayEquals(new int[] {3}, cursores);
    }

    // ---------------------------------------------------------------- dentro de una mision

    private static Mision mision(String id) {
        return SimuladorDeMisiones.semillas("misiones-de-progresion.json", "misiones-del-documento.json").stream()
                .filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    /** Lo que jugaron los rivales de ese prototipo en varias ejecuciones de la mision. */
    private static Set<String> jugadasDe(SimuladorDeMisiones simulador, String rival, Mision m, String heroe,
                                         int nivelDelHeroe) {
        Set<String> jugadas = new HashSet<>();
        for (long semilla = 1; semilla <= 12; semilla++) {
            simulador.jugar(m, heroe, nivelDelHeroe, Juego.ROTACION, semilla);
        }
        for (String registro : simulador.jugadasRegistradas()) {
            String[] partes = registro.split("\\|");
            if (partes[0].equals(rival)) {
                jugadas.add(partes[1]);
            }
        }
        return jugadas;
    }

    @Test
    @DisplayName("los enemigos juegan su estrategia, no solo el ataque más fuerte: los tanques del Templo también se defienden")
    void losEnemigosJueganSuEstrategia() {
        Set<String> conEstrategia = jugadasDe(new SimuladorDeMisiones().registrandoJugadas(), "Guerrero Tanque",
                mision("templo-olvidado"), "Pícaro Veneno", 8);
        Set<String> antes = jugadasDe(SimuladorDeMisiones.conPoliticaAnterior().registrandoJugadas(),
                "Guerrero Tanque", mision("templo-olvidado"), "Pícaro Veneno", 8);

        assertTrue(conEstrategia.contains("Golpe con escudo"), conEstrategia.toString());
        assertTrue(conEstrategia.contains("Defensa feroz") || conEstrategia.contains("Mano de piedra"),
                "ninguna defensa en " + conEstrategia);
        // Con la estrategia solo juega lo suyo en el tramo y el basico de respaldo.
        assertTrue(Set.of("Golpe con escudo", "Defensa feroz", "Mano de piedra", Reglamento.ATAQUE_BASICO)
                .containsAll(conEstrategia), conEstrategia.toString());
        // Antes solo atacaba: su unico ataque es el golpe con escudo.
        assertFalse(antes.contains("Defensa feroz") || antes.contains("Mano de piedra"), antes.toString());
    }

    @Test
    @DisplayName("el tramo sale del nivel de la misión, no del héroe: contra el Templo (nivel 8) un héroe de nivel 3 enfrenta guerreros de la estrategia de 8")
    void elTramoEsElDeLaMision() {
        // Golpe de tormenta se aprende en el nivel 8: si el tramo fuera el del heroe (3), solo jugarian el embate.
        // (Un heroe de nivel 3 no pasa del primer encuentro del Templo: por eso se mira al primer enemigo, un guerrero.)
        Set<String> jugadas = jugadasDe(new SimuladorDeMisiones().registrandoJugadas(), "Guerrero Armas",
                mision("templo-olvidado"), "Guerrero Armas", 3);

        assertTrue(jugadas.contains("Golpe de tormenta"), jugadas.toString());
    }

    @Test
    @DisplayName("una estrategia inyectada manda: un mago que solo trae Misiles de magma no juega otra especial")
    void laEstrategiaInyectadaManda() {
        SimuladorDeMisiones solo = new SimuladorDeMisiones(List.of(
                new EstrategiaPredefinida("mago-de-prueba", "Mago Fuego", 1, List.of(List.of("Misiles de magma")))))
                .registrandoJugadas();

        Set<String> jugadas = jugadasDe(solo, "Mago Fuego", mision("volcan-dormido"), "Guerrero Armas", 6);

        assertFalse(jugadas.isEmpty());
        assertTrue(Set.of("Misiles de magma", Reglamento.ATAQUE_BASICO).containsAll(jugadas), jugadas.toString());
    }

    // ---------------------------------------------------------------- la politica anterior, intacta

    @Test
    @DisplayName("sin estrategias el simulador da exactamente los resultados de antes (ataque más fuerte al alcance)")
    void laPoliticaAnteriorNoCambia() {
        SimuladorDeMisiones antes = SimuladorDeMisiones.conPoliticaAnterior();
        // Capturado con la version del simulador anterior a este cambio: (exito, derrotados) por semilla 1 a 8.
        String senderoConArmas = recorrer(antes, mision("sendero-de-los-aprendices"), "Guerrero Armas", 1);
        String temploConVeneno = recorrer(antes, mision("templo-olvidado"), "Pícaro Veneno", 8);

        assertEquals("T3 F2 F2 T3 T3 T3 T3 F2", senderoConArmas);
        assertEquals("T19 F17 T19 T19 F16 T19 F17 F16", temploConVeneno);
    }

    private static String recorrer(SimuladorDeMisiones simulador, Mision m, String prototipo, int nivel) {
        return java.util.stream.LongStream.rangeClosed(1, 8).mapToObj(semilla -> {
            Resultado r = simulador.jugar(m, prototipo, nivel, Juego.ROTACION, semilla);
            return (r.exito() ? "T" : "F") + r.derrotados();
        }).collect(Collectors.joining(" "));
    }
}
