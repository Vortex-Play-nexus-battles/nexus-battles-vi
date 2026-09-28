package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static nexus.combate.reglas.CatalogoDePrueba.heroe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que pasa al empezar el turno de un combatiente: §6.1.1, «el poder se
 * recupera ... cada dos (2) puntos por turno durante el combate»; terminan sus
 * protecciones y actuan sus efectos por turno.
 */
class InicioDeTurnoTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    private static Contendiente conPoderYVida(String id, String prototipo, int vida, int poder,
                                              List<EfectoActivo> efectos) {
        return new Contendiente(id, null, prototipo, 1, null, vida, poder, 3, Map.of(), efectos, List.of(),
                List.of(), null);
    }

    private ResultadoDeTurno empezar(String id, Contendiente... combatientes) {
        return motor.iniciarTurno(new SolicitudDeTurno(id, false, List.of(combatientes)), new AzarGuionado());
    }

    private static Contendiente de(ResultadoDeTurno r, String id) {
        return r.combatientes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("recupera dos puntos de poder por turno")
    void recuperaDos() {
        ResultadoDeTurno r = empezar("armas", conPoderYVida("armas", "Guerrero Armas", 44, 2, List.of()),
                heroe("tanque", "Guerrero Tanque", null));
        assertEquals(4, de(r, "armas").poder());
        assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.PODER_RECUPERADO
                && e.cantidad() == 2));
    }

    @Test
    @DisplayName("la recuperacion no pasa del maximo del heroe")
    void noPasaDelMaximo() {
        ResultadoDeTurno r = empezar("armas", conPoderYVida("armas", "Guerrero Armas", 44, 7, List.of()),
                heroe("tanque", "Guerrero Tanque", null));
        assertEquals(8, de(r, "armas").poder());
    }

    @Test
    @DisplayName("solo cambia quien empieza su turno")
    void soloQuienEmpieza() {
        ResultadoDeTurno r = empezar("armas", conPoderYVida("armas", "Guerrero Armas", 44, 2, List.of()),
                conPoderYVida("tanque", "Guerrero Tanque", 44, 2, List.of()));
        assertEquals(2, de(r, "tanque").poder());
        assertEquals(3, de(r, "armas").turnosJugados(), "empezar no es jugar: el contador lo sube la accion");
    }

    @Test
    @DisplayName("terminan sus protecciones «hasta su proximo turno»; sus bonos propios no")
    void terminanLasProtecciones() {
        List<EfectoActivo> efectos = List.of(
                new EfectoActivo("MANO_DE_PIEDRA", "Mano de piedra", TipoDeEfecto.BONO_DEFENSA, 12, 1, "tanque"),
                new EfectoActivo("CORTADA", "Cortada", TipoDeEfecto.BONO_DANO, 2, 1, "tanque"));
        ResultadoDeTurno r = empezar("tanque", conPoderYVida("tanque", "Guerrero Tanque", 44, 10, efectos),
                heroe("armas", "Guerrero Armas", null));
        Contendiente tanque = de(r, "tanque");
        assertFalse(tanque.tiene(TipoDeEfecto.BONO_DEFENSA));
        assertTrue(tanque.tiene(TipoDeEfecto.BONO_DANO));
        assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.EFECTO_TERMINADO
                && "Mano de piedra".equals(e.efecto())));
    }

    @Test
    @DisplayName("un sangrado que deja sin vida lo tumba y no recupera poder")
    void sangradoMortal() {
        List<EfectoActivo> sangrado = List.of(
                new EfectoActivo("CIERRA_SANGRIENTA", "Cierra sangrienta", TipoDeEfecto.DANO_POR_TURNO, 4, 2, "machete"));
        ResultadoDeTurno r = empezar("tanque", conPoderYVida("tanque", "Guerrero Tanque", 3, 2, sangrado),
                heroe("machete", "Pícaro Machete", null));
        Contendiente tanque = de(r, "tanque");
        assertEquals(0, tanque.vida());
        assertEquals(2, tanque.poder());
        assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.CAIDO));
        assertEquals(List.of(new Afectado("tanque", 3, 0)), r.afectados());
    }

    @Test
    @DisplayName("un caido no empieza turno: nada cambia")
    void caidoNoEmpieza() {
        ResultadoDeTurno r = empezar("tanque", conPoderYVida("tanque", "Guerrero Tanque", 0, 2, List.of()),
                heroe("armas", "Guerrero Armas", null));
        assertEquals(2, de(r, "tanque").poder());
        assertTrue(r.eventos().isEmpty());
    }

    @Test
    @DisplayName("quien empieza tiene que estar en la partida")
    void quienEmpiezaTieneQueEstar() {
        assertThrows(IllegalArgumentException.class, () -> empezar("nadie", heroe("armas", "Guerrero Armas", null)));
    }

    @Test
    @DisplayName("el resultado dice que puede jugar ahora quien empieza")
    void accionesDisponiblesAlEmpezar() {
        ResultadoDeTurno r = empezar("armas", conPoderYVida("armas", "Guerrero Armas", 44, 2, List.of()),
                heroe("tanque", "Guerrero Tanque", null));
        List<EstadoDeAccion> acciones = r.acciones().get("armas");
        EstadoDeAccion embate = acciones.stream().filter(a -> a.codigo().equals("Embate sangriento"))
                .findFirst().orElseThrow();
        assertTrue(embate.disponible(), "2 + 2 = 4, justo lo que cuesta");
    }
}
