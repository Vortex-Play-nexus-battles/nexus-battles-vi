package nexus.semilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Los numeros que el dominio exige y la regla del curso escribe en texto.
 * Los textos de prueba son literales de las Tablas 8 a 19.
 */
class NumerosDeLaReglaTest {

    @Test
    @DisplayName("lee el +N al ataque de un arma")
    void bonificacionAlAtaque() {
        assertEquals(Optional.of(1),
                NumerosDeLaRegla.bonificacionAlAtaque("+1 al ataque, +1% de crítico al ataque"));
        assertEquals(Optional.of(1), NumerosDeLaRegla.bonificacionAlAtaque("+1 al ataque"));
    }

    @ParameterizedTest(name = "sin bonificacion propia al ataque: {0}")
    @ValueSource(strings = {
        "+1 a la defensa",
        "+2 al daño",
        "+1 al daño, +3% de crítico al ataque",
        "-1 al ataque del oponente",
        "-1 al daño del oponente, -2% de crítico al ataque del oponente",
        "+(2d4) de sanación"
    })
    void sinBonificacionAlAtaque(String efectos) {
        assertEquals(Optional.empty(), NumerosDeLaRegla.bonificacionAlAtaque(efectos));
    }

    @Test
    @DisplayName("lee el +N a la defensa de una armadura")
    void bonificacionALaDefensa() {
        assertEquals(Optional.of(2),
                NumerosDeLaRegla.bonificacionALaDefensa("+2 a la defensa, +2 de vida"));
        assertEquals(Optional.of(1),
                NumerosDeLaRegla.bonificacionALaDefensa("+1 a la defensa, +1 de vida"));
        assertEquals(Optional.empty(), NumerosDeLaRegla.bonificacionALaDefensa("+2 de vida"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"3%,3", "20%,20", "1%,1", "0.04%,0.04", "' 5 % ',5"})
    void porcentaje(String texto, String esperado) {
        assertEquals(new BigDecimal(esperado), NumerosDeLaRegla.porcentaje(texto));
    }

    @Test
    @DisplayName("un porcentaje ilegible no se inventa: falla")
    void porcentajeIlegible() {
        assertThrows(IllegalArgumentException.class, () -> NumerosDeLaRegla.porcentaje("alta"));
        assertThrows(IllegalArgumentException.class, () -> NumerosDeLaRegla.porcentaje(null));
    }
}
