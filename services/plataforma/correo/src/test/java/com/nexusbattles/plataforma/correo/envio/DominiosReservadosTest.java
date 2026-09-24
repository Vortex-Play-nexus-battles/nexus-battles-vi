package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que direcciones no pueden llegar a ninguna bandeja (RFC 2606, 6761, 6762).
 *
 * <p>La mitad que importa es la segunda: una direccion real marcada como
 * reservada es un jugador que no recibe su codigo de recuperacion. Por eso
 * los casos negativos se parecen a proposito a los reservados.
 */
class DominiosReservadosTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "canario@nexus.test",
        "Canario@NEXUS.TEST",
        "smoke-1790263020@nexusbattles.test",
        "alguien@example.com",
        "alguien@EXAMPLE.org",
        "alguien@example.net",
        "alguien@correo.example.com",
        "alguien@tienda.example",
        "alguien@nada.invalid",
        "alguien@localhost",
        "alguien@maquina.localhost",
        "no-reply@nexusbattles.local",
        "alguien@nexus.test.",
    })
    void reconoceLosDominiosReservados(String direccion) {
        assertThat(DominiosReservados.esReservado(direccion)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jugador+qa@gmail.com",
        "simon.perezg@upb.edu.co",
        "alguien@testing.com",
        "alguien@mytest",
        "alguien@example.com.co",
        "alguien@notexample.com",
        "alguien@test.com",
        "alguien@local.dev",
    })
    void noConfundeUnaDireccionRealConUnaReservada(String direccion) {
        assertThat(DominiosReservados.esReservado(direccion)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sin-arroba", "alguien@", "   "})
    void loQueNoEsUnaDireccionNoSeTrataComoReservada(String direccion) {
        // No es asunto de esta regla: el envio fallara por su cuenta y quedara
        // anotado como rechazado, que es lo que hay que ver.
        assertThat(DominiosReservados.esReservado(direccion)).isFalse();
    }
}