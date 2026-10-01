package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluadorCaidaTest {

    @Test
    @DisplayName("una tasa de cero nunca entrega el objeto")
    void tasaCeroNuncaEntrega() {
        EvaluadorCaida evaluador = new EvaluadorCaida(() -> 0.0);

        assertFalse(evaluador.obtiene(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("una tasa de cien siempre entrega el objeto")
    void tasaCienSiempreEntrega() {
        EvaluadorCaida evaluador = new EvaluadorCaida(() -> 0.999999);

        assertTrue(evaluador.obtiene(BigDecimal.valueOf(100)));
    }

    @Test
    @DisplayName("un valor menor a la tasa produce botin")
    void valorMenorProduceBotin() {
        EvaluadorCaida evaluador = new EvaluadorCaida(() -> 0.1249);

        assertTrue(evaluador.obtiene(BigDecimal.valueOf(12.5)));
    }

    @Test
    @DisplayName("el limite exacto no produce botin")
    void limiteExactoNoProduceBotin() {
        EvaluadorCaida evaluador = new EvaluadorCaida(() -> 0.125);

        assertFalse(evaluador.obtiene(BigDecimal.valueOf(12.5)));
    }

    @Test
    @DisplayName("rechaza tasas y valores aleatorios fuera de rango")
    void rechazaValoresInvalidos() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluadorCaida(() -> 0.5).obtiene(BigDecimal.valueOf(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvaluadorCaida(() -> 0.5).obtiene(BigDecimal.valueOf(101)));
        assertThrows(
                IllegalStateException.class,
                () -> new EvaluadorCaida(() -> 1.0).obtiene(BigDecimal.TEN));
    }
}
