package com.nexusbattles.plataforma.salaspartidas.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El texto del rechazo por saldo — auditoría de DEV del 30-sep: decía
 * «1 creditos» y «para crear esta sala» también a quien intentaba entrar.
 */
@DisplayName("CreditosInsuficientes · lo que se le dice al jugador")
class CreditosInsuficientesTest {

    @Test
    @DisplayName("con tilde, en singular cuando es uno, y válido para crear y para entrar")
    void texto() {
        CreditosInsuficientes uno = new CreditosInsuficientes(1, 9999);
        CreditosInsuficientes varios = new CreditosInsuficientes(240, 400);
        CreditosInsuficientes ninguno = new CreditosInsuficientes(0, 150);

        assertAll(
                () -> assertEquals("Créditos insuficientes", uno.titulo()),
                () -> assertEquals("Tienes 1 crédito y la apuesta de esta sala es de 9999.", uno.detalle()),
                () -> assertEquals("Tienes 240 créditos y la apuesta de esta sala es de 400.", varios.detalle()),
                () -> assertEquals("Tienes 0 créditos y la apuesta de esta sala es de 150.", ninguno.detalle()),
                () -> assertEquals(422, varios.estado()),
                () -> assertEquals(240, varios.disponibles()),
                () -> assertEquals(400, varios.requeridos()));
    }
}
