package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La maquina juega con las mismas reglas (§6.1.3) y una politica simple y
 * determinista (D-B7-12): la accion de ataque mas cara que pueda pagar, contra
 * el rival con menos vida.
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

    private ResultadoDeAccion decidir(Contendiente... combatientes) {
        return motor.resolver(new SolicitudDeAccion(Reglamento.DECISION_DE_LA_MAQUINA, "ia", null, false,
                List.of(combatientes)), new Random(1));
    }

    @Test
    @DisplayName("con poder de sobra juega la accion de ataque mas cara que tiene")
    void laMasCara() {
        ResultadoDeAccion r = decidir(maquina(8, 64, Map.of(), 0), rival("a", 44));
        assertEquals("Golpe de tormenta", r.accionEjecutada());
        assertEquals("Golpe de tormenta", r.accion());
    }

    @Test
    @DisplayName("a igual coste, la primera de la Tabla 7")
    void empateDeCoste() {
        // 5 de poder: no alcanza Golpe de tormenta (6); Embate y Lanza cuestan 4.
        ResultadoDeAccion r = decidir(maquina(8, 5, Map.of(), 0), rival("a", 44));
        assertEquals("Embate sangriento", r.accionEjecutada());
    }

    @Test
    @DisplayName("respeta la carga: la que esta en carga no la elige")
    void respetaLaCarga() {
        ResultadoDeAccion r = decidir(maquina(8, 64, Map.of("Golpe de tormenta", 0), 1), rival("a", 44));
        assertEquals("Embate sangriento", r.accionEjecutada());
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
        for (int i = 0; i < 5; i++) {
            ResultadoDeAccion r = decidir(maquina(4, 20, Map.of(), 0), rival("a", 40), rival("b", 40));
            assertEquals("Embate sangriento", r.accionEjecutada());
            assertEquals("a", r.objetivo(), "empate de vida: el primero");
        }
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
        assertEquals(Reglamento.SANACION_BASICA, r.accionEjecutada());
        assertEquals("amigo", r.objetivo());
        assertTrue(r.combatientes().stream().filter(c -> c.id().equals("amigo")).findFirst().orElseThrow().vida() > 10);
    }
}
