package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.validation.PasswordPolicyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF-AUT-002, y desde HU-AUT-006 CA-03: el rechazo nombra la regla que falla. */
@DisplayName("PasswordPolicyValidator · RF-AUT-002")
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator politica = new PasswordPolicyValidator();

    @Test
    @DisplayName("mas de 8 caracteres con las cuatro clases: cumple")
    void cumple() {
        assertDoesNotThrow(() -> politica.validar("Nueva-Segura-2!"));
        assertEquals(List.of(), politica.reglasQueFaltan("Nueva-Segura-2!"));
    }

    @Test
    @DisplayName("exactamente 8 caracteres no basta: la regla es MAS de 8")
    void ochoNoBasta() {
        assertEquals(List.of("debe tener más de 8 caracteres"), politica.reglasQueFaltan("Ab1!Ab1!"));
    }

    @Test
    @DisplayName("dice exactamente que clases faltan, y solo esas")
    void diceQueFalta() {
        assertAll(
                () -> assertEquals(List.of("debe incluir al menos una mayúscula"),
                        politica.reglasQueFaltan("minusculas-1!")),
                () -> assertEquals(List.of("debe incluir al menos un número"),
                        politica.reglasQueFaltan("SinNumeros-!")),
                () -> assertEquals(List.of("debe incluir al menos un símbolo"),
                        politica.reglasQueFaltan("SinSimbolos12")),
                () -> assertEquals(List.of("debe incluir al menos una minúscula"),
                        politica.reglasQueFaltan("MAYUSCULAS-1!")));
    }

    @Test
    @DisplayName("varias reglas a la vez se listan todas, en orden")
    void variasALaVez() {
        assertEquals(List.of(
                "debe tener más de 8 caracteres",
                "debe incluir al menos una mayúscula",
                "debe incluir al menos un símbolo"),
                politica.reglasQueFaltan("abc123"));
    }

    @Test
    @DisplayName("nula: solo la longitud, sin reventar")
    void nula() {
        assertEquals(List.of("debe tener más de 8 caracteres"), politica.reglasQueFaltan(null));
    }

    @Test
    @DisplayName("validar() lanza con el detalle de las reglas")
    void validarLanza() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> politica.validar("corta"));

        assertTrue(error.getMessage().startsWith("La contraseña no cumple la política: "));
        assertTrue(error.getMessage().contains("más de 8 caracteres"));
    }
}
