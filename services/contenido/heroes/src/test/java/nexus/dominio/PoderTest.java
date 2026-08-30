package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-HER-005 — Consumo y recuperacion de poder.
 * Fuente: heroes.md seccion 6.1.1, p. 26: recuperacion de 2 puntos por turno
 * durante el combate, recuperacion instantanea al concluir, y "si el heroe
 * carece de poder suficiente para ejecutar una accion, el valor de ataque se
 * reduce a su valor base".
 */
class PoderTest {

    private static final Accion CUESTA_4 = new Accion("Mano de piedra", 4, "+12 a la defensa");
    private static final Accion CUESTA_TODO = new Accion("Reanimación", null, "Sana el 100% de la vida del compañero");

    @Test
    @DisplayName("el poder inicial es el de la Tabla 6 para el prototipo")
    void poderInicialSegunPrototipo() {
        EstadoDePoder poder = EstadoDePoder.de(PrototiposIniciales.LISTA.get(0)); // Guerrero Tanque
        assertEquals(10, poder.actual());
        assertEquals(10, poder.maximo());
    }

    // Criterio 1: dos puntos por turno.
    @Test
    @DisplayName("cada turno en combate recupera dos puntos de poder")
    void recuperaDosPorTurno() {
        EstadoDePoder poder = new EstadoDePoder(6, 10).recuperarPorTurno();
        assertEquals(8, poder.actual());
    }

    @Test
    @DisplayName("la recuperacion por turno no supera el poder maximo del heroe")
    void recuperacionNoSuperaElMaximo() {
        EstadoDePoder poder = new EstadoDePoder(9, 10).recuperarPorTurno();
        assertEquals(10, poder.actual());
    }

    // Criterio 2: recuperacion instantanea al concluir.
    @Test
    @DisplayName("al concluir el combate el poder se recupera instantaneamente")
    void alConcluirSeRecuperaCompleto() {
        EstadoDePoder poder = new EstadoDePoder(3, 10).alConcluirCombate();
        assertEquals(10, poder.actual());
    }

    @Test
    @DisplayName("ejecutar una accion consume su costo en poder")
    void ejecutarConsumeElCosto() {
        ResultadoDeAccion resultado = new EstadoDePoder(10, 10).usar(CUESTA_4);
        assertTrue(resultado.ejecutada());
        assertEquals(6, resultado.estado().actual());
        assertFalse(resultado.ataqueEnValorBase());
    }

    @Test
    @DisplayName("una accion que cuesta todos los puntos deja el poder en cero")
    void accionQueCuestaTodoDejaEnCero() {
        // Reanimacion del Medico (Tabla 7): "Todos los puntos de poder".
        ResultadoDeAccion resultado = new EstadoDePoder(7, 10).usar(CUESTA_TODO);
        assertTrue(resultado.ejecutada());
        assertEquals(0, resultado.estado().actual());
    }

    // Criterio 3: sin poder suficiente, el ataque cae al valor base.
    @Test
    @DisplayName("sin poder suficiente la accion no se ejecuta y el ataque queda en su valor base")
    void sinPoderSuficienteAtaqueEnValorBase() {
        ResultadoDeAccion resultado = new EstadoDePoder(3, 10).usar(CUESTA_4);
        assertFalse(resultado.ejecutada());
        assertTrue(resultado.ataqueEnValorBase());
        assertEquals(3, resultado.estado().actual());
    }
}
