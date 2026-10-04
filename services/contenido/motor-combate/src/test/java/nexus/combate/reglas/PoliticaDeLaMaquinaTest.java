package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La maquina juega con las mismas reglas (§6.1.3) y, desde D-41, con una IA
 * tactica por reglas: ensaya cada jugada legal con el motor real y juega la de
 * mejor puntaje esperado. No es una IA entrenada (D-B7-13).
 *
 * <p>Hasta D-41 la politica era fija (D-B7-12: la especial de ataque mas cara
 * contra el rival con menos vida). Las pruebas que la describian siguen aqui,
 * con lo que la maquina hace ahora y por que.
 */
class PoliticaDeLaMaquinaTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    private static Contendiente maquina(int nivel, int poder, Map<String, Integer> cargas, int turnosJugados) {
        return new Contendiente("ia", null, "Guerrero Armas", nivel, null, Integer.MAX_VALUE, poder, turnosJugados,
                cargas, List.of(), List.of(), List.of(), null);
    }

    private static Contendiente rival(String id, int vida) {
        return new Contendiente(id, null, "Guerrero Tanque", 1, null, vida, 10, 0, Map.of(), List.of(), List.of(),
                List.of(), null);
    }

    /** Un Guerrero Tanque de nivel 8: defensa 88, que el ataque basico de un Guerrero Armas (80 + 1d6) no supera. */
    private static Contendiente tanqueDeNivel8(String id) {
        return new Contendiente(id, null, "Guerrero Tanque", 8, null, Integer.MAX_VALUE, 10, 0, Map.of(), List.of(),
                List.of(), List.of(), null);
    }

    private ResultadoDeAccion decidir(Contendiente... combatientes) {
        return motor.resolver(new SolicitudDeAccion(Reglamento.DECISION_DE_LA_MAQUINA, "ia", null, false,
                List.of(combatientes)), new Random(1));
    }

    private static ResultadoDeAccion decidirCon(MotorDeAcciones motor, boolean porEquipos, Random azar,
                                                List<Contendiente> combatientes) {
        return motor.resolver(new SolicitudDeAccion(Reglamento.DECISION_DE_LA_MAQUINA, "ia", null, porEquipos,
                combatientes), azar);
    }

    @Test
    @DisplayName("contra una defensa que el básico no supera, juega la especial que sí la supera")
    void laMasCara() {
        // Golpe de tormenta: +(3d6)x8 al ataque. Embate sangriento tambien
        // supera los 88 (+16), pero con la mitad de dano; Lanza no los supera.
        ResultadoDeAccion r = decidir(maquina(8, 64, Map.of(), 0), tanqueDeNivel8("a"));
        assertEquals("Golpe de tormenta", r.accionEjecutada());
        assertEquals("Golpe de tormenta", r.accion());
    }

    @Test
    @DisplayName("a igual coste, la que más daño espera (antes: la primera de la Tabla 7)")
    void empateDeCoste() {
        // 5 de poder: no alcanza Golpe de tormenta (6); Embate y Lanza cuestan 4.
        // El rival de nivel 1 no para ninguna de las dos: Lanza (+16 de dano)
        // pega el doble que Embate (+8).
        ResultadoDeAccion r = decidir(maquina(8, 5, Map.of(), 0), rival("a", 44));
        assertEquals("Lanza de los dioses", r.accionEjecutada());
    }

    @Test
    @DisplayName("respeta la carga: la que está en carga no la elige, aunque fuera la mejor")
    void respetaLaCarga() {
        ResultadoDeAccion r = decidir(maquina(8, 64, Map.of("Golpe de tormenta", 0), 1), tanqueDeNivel8("a"));
        assertNotEquals("Golpe de tormenta", r.accionEjecutada());
        assertEquals("Embate sangriento", r.accionEjecutada(), "la otra que supera la defensa de 88");
    }

    @Test
    @DisplayName("sin poder para ninguna, ataque basico")
    void sinPoderAtaqueBasico() {
        ResultadoDeAccion r = decidir(maquina(1, 3, Map.of(), 0), rival("a", 44));
        assertEquals(Reglamento.ATAQUE_BASICO, r.accionEjecutada());
        assertEquals(false, r.enValorBase(), "decidir el basico no es quedarse sin poder");
    }

    @Test
    @DisplayName("ataca al rival en pie con menos vida")
    void alMasDebil() {
        ResultadoDeAccion r = decidir(maquina(1, 3, Map.of(), 0), rival("a", 40), rival("b", 12), rival("c", 30));
        assertEquals("b", r.objetivo());
    }

    @Test
    @DisplayName("es determinista: el mismo estado da la misma decision")
    void determinista() {
        ResultadoDeAccion primera = decidir(maquina(4, 20, Map.of(), 0), rival("a", 40), rival("b", 40));
        for (int i = 0; i < 5; i++) {
            ResultadoDeAccion r = decidir(maquina(4, 20, Map.of(), 0), rival("a", 40), rival("b", 40));
            assertEquals(primera.accionEjecutada(), r.accionEjecutada());
            assertEquals(primera.objetivo(), r.objetivo());
        }
        assertEquals("Lanza de los dioses", primera.accionEjecutada());
        assertEquals("a", primera.objetivo(), "empate de vida: el primero");
    }

    @Test
    @DisplayName("una maquina sanadora sana a quien tiene menos vida de su bando")
    void maquinaSanadora() {
        Contendiente chaman = new Contendiente("ia", 1, "Chamán", 1, null, 28, 10, 0, Map.of(), List.of(),
                List.of(), List.of(), null);
        Contendiente companero = new Contendiente("amigo", 1, "Guerrero Armas", 1, null, 10, 8, 0, Map.of(),
                List.of(), List.of(), List.of(), null);
        ResultadoDeAccion r = motor.resolver(new SolicitudDeAccion(Reglamento.DECISION_DE_LA_MAQUINA, "ia", null,
                true, List.of(chaman, companero, rival("rival", 44))), new Random(3));
        assertEquals(TipoDeAccion.SANACION, r.tipo());
        assertEquals("amigo", r.objetivo());
        assertTrue(r.combatientes().stream().filter(c -> c.id().equals("amigo")).findFirst().orElseThrow().vida() > 10);
    }

    @Test
    @DisplayName("no mira el azar de la partida: ni cambia la decisión ni gasta una sola tirada")
    void noMiraElAzarDeLaPartida() {
        List<Contendiente> mesa = List.of(maquina(4, 20, Map.of(), 0), rival("a", 40), rival("b", 25));
        ResultadoDeAccion conUno = decidirCon(motor, false, new Random(1), mesa);
        ResultadoDeAccion conOtro = decidirCon(motor, false, new Random(987_654_321L), mesa);
        assertEquals(conUno.accion(), conOtro.accion());
        assertEquals(conUno.objetivo(), conOtro.objetivo());

        // Decidir y jugar con la semilla 7 da EXACTAMENTE lo mismo que jugar esa
        // misma accion a mano con la semilla 7: decidir no consumio tiradas.
        ResultadoDeAccion decidida = decidirCon(motor, false, new Random(7), mesa);
        ResultadoDeAccion aMano = motor.resolver(new SolicitudDeAccion(decidida.accion(), "ia", decidida.objetivo(),
                false, mesa), new Random(7));
        assertEquals(aMano.eventos(), decidida.eventos());
        assertEquals(aMano.afectados(), decidida.afectados());
    }

    @Test
    @DisplayName("si el próximo golpe la tumba y no puede tumbar antes, se defiende")
    void seDefiende() {
        Contendiente tanque = new Contendiente("ia", null, "Guerrero Tanque", 4, null, 30, 40, 0, Map.of(),
                List.of(), List.of(), List.of(), null);
        Contendiente machete = new Contendiente("a", null, "Pícaro Machete", 4, null, Integer.MAX_VALUE, 32, 0,
                Map.of(), List.of(), List.of(), List.of(), null);
        for (DificultadDeLaMaquina d : List.of(DificultadDeLaMaquina.NORMAL, DificultadDeLaMaquina.DIFICIL)) {
            MotorDeAcciones conDificultad = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision(), d);
            ResultadoDeAccion r = decidirCon(conDificultad, false, new Random(1), List.of(tanque, machete));
            assertEquals(TipoDeAccion.DEFENSA, r.tipo(), d + " jugó " + r.accionEjecutada());
        }
    }

    @Test
    @DisplayName("remata: entre un rival entero y uno a punto de caer, al que puede tumbar")
    void remata() {
        Contendiente picaro = new Contendiente("ia", null, "Pícaro Machete", 1, null, Integer.MAX_VALUE, 8, 0,
                Map.of(), List.of(), List.of(), List.of(), null);
        Contendiente entero = rival("b", 44);
        Contendiente tocado = new Contendiente("a", null, "Mago Fuego", 1, null, 3, 8, 0, Map.of(), List.of(),
                List.of(), List.of(), null);
        for (DificultadDeLaMaquina d : DificultadDeLaMaquina.values()) {
            MotorDeAcciones conDificultad = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision(), d);
            assertEquals("a", decidirCon(conDificultad, false, new Random(1), List.of(picaro, entero, tocado))
                    .objetivo(), d.name());
        }
    }

    @Test
    @DisplayName("en cooperativo nunca se ataca a sí misma ni a un compañero, en ninguna dificultad")
    void nuncaContraLosSuyos() {
        for (DificultadDeLaMaquina d : DificultadDeLaMaquina.values()) {
            MotorDeAcciones conDificultad = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision(), d);
            for (int vidaDelCompanero = 2; vidaDelCompanero <= 44; vidaDelCompanero += 6) {
                for (String prototipo : List.of("Guerrero Armas", "Mago Hielo", "Pícaro Veneno", "Médico")) {
                    List<Contendiente> mesa = new ArrayList<>(List.of(
                            new Contendiente("ia", 1, prototipo, 4, null, Integer.MAX_VALUE, 20, 0, Map.of(),
                                    List.of(), List.of(), List.of(), null),
                            new Contendiente("aliado", 1, "Guerrero Tanque", 1, null, vidaDelCompanero, 10, 0,
                                    Map.of(), List.of(), List.of(), List.of(), null),
                            new Contendiente("rival1", 2, "Guerrero Armas", 4, null, Integer.MAX_VALUE, 20, 0,
                                    Map.of(), List.of(), List.of(), List.of(), null),
                            new Contendiente("rival2", 2, "Mago Fuego", 4, null, 30, 20, 0, Map.of(), List.of(),
                                    List.of(), List.of(), null)));
                    ResultadoDeAccion r = decidirCon(conDificultad, true, new Random(vidaDelCompanero), mesa);
                    String casilla = d + " · " + prototipo + " · compañero con " + vidaDelCompanero;
                    if (r.tipo() == TipoDeAccion.ATAQUE) {
                        assertTrue(r.objetivo().startsWith("rival"), casilla + " atacó a " + r.objetivo());
                    }
                    assertTrue(r.eventos().stream().noneMatch(e -> e.tipo() == TipoDeEvento.DANO
                            && !e.combatiente().startsWith("rival")), casilla + ": daño a uno de los suyos");
                }
            }
        }
    }

    @Test
    @DisplayName("FÁCIL a veces no juega la mejor; NORMAL siempre la misma para el mismo estado")
    void laDificultadSeNota() {
        MotorDeAcciones facil = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision(),
                DificultadDeLaMaquina.FACIL);
        int distintas = 0;
        for (int vida = 5; vida <= 44; vida++) {
            List<Contendiente> mesa = List.of(maquina(4, 20, Map.of(), 0), rival("a", vida), rival("b", 44));
            ResultadoDeAccion f = decidirCon(facil, false, new Random(1), mesa);
            ResultadoDeAccion n = decidirCon(motor, false, new Random(1), mesa);
            if (!f.accion().equals(n.accion()) || !f.objetivo().equals(n.objetivo())) {
                distintas++;
            }
        }
        assertTrue(distintas > 0, "FÁCIL se descuida alguna vez");
        assertTrue(distintas < 40, "pero no siempre");
    }

    @Test
    @DisplayName("la dificultad se escribe con o sin tildes; vacía es NORMAL; una desconocida no arranca")
    void dificultadDesdeTexto() {
        assertAll(
                () -> assertEquals(DificultadDeLaMaquina.FACIL, DificultadDeLaMaquina.desde("Fácil")),
                () -> assertEquals(DificultadDeLaMaquina.DIFICIL, DificultadDeLaMaquina.desde(" dificil ")),
                () -> assertEquals(DificultadDeLaMaquina.NORMAL, DificultadDeLaMaquina.desde(null)),
                () -> assertEquals(DificultadDeLaMaquina.NORMAL, DificultadDeLaMaquina.desde("")),
                () -> assertEquals(DificultadDeLaMaquina.NORMAL, new MotorDeAcciones(new CatalogoDePrueba(),
                        IndiceNormal.porOmision()).dificultad(), "por omisión, NORMAL"),
                () -> assertThrows(IllegalArgumentException.class, () -> DificultadDeLaMaquina.desde("extrema")));
    }
}
