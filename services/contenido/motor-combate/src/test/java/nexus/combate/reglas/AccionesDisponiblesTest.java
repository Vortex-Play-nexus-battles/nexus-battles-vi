package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que un combatiente puede jugar ahora y por que no lo demas, calculado por
 * el motor para que la interfaz no reimplemente la regla.
 */
class AccionesDisponiblesTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());
    private final CatalogoDePrueba catalogo = new CatalogoDePrueba();

    private List<EstadoDeAccion> accionesDe(Contendiente c) {
        FichaDeCombate ficha = catalogo.ficha(c.prototipo(), c.nivel());
        return motor.accionesDe(c.conEstadisticas(ficha.estadisticas()), ficha);
    }

    private static EstadoDeAccion la(List<EstadoDeAccion> acciones, String codigo) {
        return acciones.stream().filter(a -> a.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private static Contendiente tanque(int poder, Map<String, Integer> cargas, int turnos, List<String> epicas) {
        return new Contendiente("tanque", null, "Guerrero Tanque", 1, null, 44, poder, turnos, cargas, List.of(),
                List.of(), epicas, null);
    }

    @Test
    @DisplayName("nivel 1: el ataque basico y la primera accion; las otras se aprenden en 4 y 8")
    void nivelUno() {
        List<EstadoDeAccion> acciones = accionesDe(tanque(10, Map.of(), 0, List.of()));
        assertTrue(la(acciones, Reglamento.ATAQUE_BASICO).disponible());
        assertTrue(la(acciones, "Golpe con escudo").disponible());
        assertEquals(2, la(acciones, "Golpe con escudo").costoPoder());
        assertEquals("Se aprende en el nivel 4.", la(acciones, "Mano de piedra").motivo());
        assertEquals("Se aprende en el nivel 8.", la(acciones, "Defensa feroz").motivo());
        assertEquals(TipoDeAccion.DEFENSA, la(acciones, "Mano de piedra").tipo());
    }

    @Test
    @DisplayName("en carga: dice cuantos turnos le faltan")
    void enCarga() {
        List<EstadoDeAccion> acciones = accionesDe(tanque(10, Map.of("Golpe con escudo", 0), 1, List.of()));
        EstadoDeAccion golpe = la(acciones, "Golpe con escudo");
        assertFalse(golpe.disponible());
        assertEquals("En carga: 1 turno.", golpe.motivo());
    }

    @Test
    @DisplayName("sin poder: dice cuanto cuesta y cuanto tiene")
    void sinPoder() {
        EstadoDeAccion golpe = la(accionesDe(tanque(1, Map.of(), 0, List.of())), "Golpe con escudo");
        assertFalse(golpe.disponible());
        assertEquals("Poder insuficiente: cuesta 2 y tienes 1.", golpe.motivo());
    }

    @Test
    @DisplayName("un sanador tiene sanacion basica y no ataque basico")
    void sanador() {
        Contendiente chaman = new Contendiente("chaman", null, "Chamán", 1, null, 28, 10, 0, Map.of(), List.of(),
                List.of(), List.of(), null);
        List<EstadoDeAccion> acciones = accionesDe(chaman);
        assertTrue(acciones.stream().noneMatch(a -> a.codigo().equals(Reglamento.ATAQUE_BASICO)));
        assertTrue(la(acciones, Reglamento.SANACION_BASICA).disponible());
    }

    @Test
    @DisplayName("las epicas: sin coste, dos turnos de carga, y «no aplica» cuando no tienen efecto")
    void epicas() {
        List<EstadoDeAccion> acciones = accionesDe(tanque(10, Map.of(), 0, List.of("Golpe de defensa", "Té changua")));
        EstadoDeAccion golpe = la(acciones, "Golpe de defensa");
        assertTrue(golpe.esEpica());
        assertTrue(golpe.disponible());
        assertEquals(2, golpe.turnosDeCarga());
        assertFalse(la(acciones, "Té changua").disponible());
    }

    @Test
    @DisplayName("un caido no puede jugar nada")
    void caido() {
        List<EstadoDeAccion> acciones = accionesDe(new Contendiente("tanque", null, "Guerrero Tanque", 1, null, 0,
                10, 0, Map.of(), List.of(), List.of(), List.of(), null));
        assertTrue(acciones.stream().noneMatch(EstadoDeAccion::disponible));
    }
}
