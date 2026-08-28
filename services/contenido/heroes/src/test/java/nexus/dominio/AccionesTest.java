package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * HU-HER-006 — Consulta de acciones especiales.
 * Fuente: habilidades-y-mejoras.md seccion 6.1.2, p. 28 (Tabla 7): tres
 * acciones por heroe, con costo en puntos de poder y efecto.
 */
class AccionesTest {

    // Criterio 1: exactamente 3 acciones por prototipo, cada una con costo y efecto.
    @Test
    @DisplayName("todo prototipo del catalogo dispone de exactamente tres acciones")
    void todoPrototipoTieneTresAcciones() {
        for (Prototipo p : Catalogo.conPrototiposIniciales().listar()) {
            assertEquals(3, p.acciones().size(), p.nombre());
        }
    }

    @Test
    @DisplayName("el dominio rechaza un prototipo que no tenga tres acciones")
    void prototipoSinTresAccionesRechazado() {
        assertThrows(IllegalArgumentException.class, () -> new Prototipo(
                "Incompleto", "Guerrero", "desc", false,
                new Estadisticas(8, 40, 10, new Formula(10, 1, 6), new Formula(0, 1, 6), null),
                List.of(new Accion("Única", 2, "+1 al ataque"))));
    }

    // Criterio 2: las 24 acciones coinciden con la Tabla 7 en costo y efecto.
    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "TODOS", value = {
            "Guerrero Tanque | 0 | Golpe con escudo          | 2     | +2 al ataque",
            "Guerrero Tanque | 1 | Mano de piedra            | 4     | +12 a la defensa",
            "Guerrero Tanque | 2 | Defensa feroz             | 6     | Inmune al daño físico y (3d6) al daño mágico",
            "Guerrero Armas  | 0 | Embate sangriento         | 4     | '+2 al ataque, +1 de daño'",
            "Guerrero Armas  | 1 | Lanza de los dioses       | 4     | +2 al daño",
            "Guerrero Armas  | 2 | Golpe de tormenta         | 6     | '+(3d6) al ataque, +2 al daño'",
            "Mago Fuego      | 0 | Misiles de magma          | 2     | '+1 al ataque, +2 de daño'",
            "Mago Fuego      | 1 | Vulcano                   | 6     | '+3 al ataque, +(3d9) al daño'",
            "Mago Fuego      | 2 | Pare de fuego             | 4     | +1 al ataque y retorna el (0dx) daño causado por el oponente en el turno anterior",
            "Mago Hielo      | 0 | Lluvia de hielo           | 2     | '+2 al ataque, +2 de daño'",
            "Mago Hielo      | 1 | Cono de hielo             | 6     | +2 al daño y afecta el ataque del enemigo en un (1d3) durante los dos turnos siguientes",
            "Mago Hielo      | 2 | Bola de hielo             | 4     | +2 al ataque y afecta en (0d4) al daño causado por el oponente",
            "Pícaro Veneno   | 0 | Flor de loto              | 2     | +(4d8) al daño",
            "Pícaro Veneno   | 1 | Agonía                    | 4     | +(2d9) de daño",
            "Pícaro Veneno   | 2 | Piquete                   | 4     | '+1 al ataque por dos turnos, +2 al daño por 1 turno'",
            "Pícaro Machete  | 0 | Cortada                   | 2     | +2 al daño por dos turnos",
            "Pícaro Machete  | 1 | Machetazo                 | 4     | '+(2d8) al daño, +1 al ataque'",
            "Pícaro Machete  | 2 | Planazo                   | 4     | '+(2d8) al ataque, +1 al daño'",
            "Chamán          | 0 | Toque de la Vida          | 2     | +2 de sanación",
            "Chamán          | 1 | Vínculo Natural           | 4     | +2 de sanación por dos turnos",
            "Chamán          | 2 | Canto del Bosque          | 6     | Sana a todo el grupo +(2d6) durante dos turnos",
            "Médico          | 0 | Curación Directa          | 2     | +2 de sanación",
            "Médico          | 1 | Neutralización de Efectos | 4     | '+2 y +(2d4) de sanación'",
            "Médico          | 2 | Reanimación               | TODOS | Sana el 100% de la vida del compañero"})
    @DisplayName("las 24 acciones coinciden con la Tabla 7 en nombre, costo y efecto")
    void lasAccionesCoincidenConTabla7(String prototipo, int posicion, String nombre, Integer costo, String efecto) {
        Accion accion = Catalogo.conPrototiposIniciales().fichaDe(prototipo).acciones().get(posicion);
        assertEquals(nombre.trim(), accion.nombre());
        if (costo == null) {
            assertNull(accion.costoPuntos(), "Reanimación cuesta todos los puntos");
        } else {
            assertEquals(costo, accion.costoPuntos());
        }
        assertEquals(efecto.trim(), accion.efecto());
    }
}
