package com.nexusbattles.comun.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Un error de campo mal formado es peor que no tenerlo: la interfaz no sabria
 * que campo marcar y la persona no sabria que corregir.
 */
@DisplayName("ErrorDeCampo")
class ErrorDeCampoTest {

    @Test
    @DisplayName("guarda el campo y el mensaje")
    void valido() {
        ErrorDeCampo error = new ErrorDeCampo("recompensaCreditos", "No puede ser negativa.");

        assertEquals("recompensaCreditos", error.campo());
        assertEquals("No puede ser negativa.", error.mensaje());
    }

    @Test
    @DisplayName("rechaza quedarse sin saber a que campo se refiere")
    void sinCampo() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorDeCampo(null, "algo"));
        assertThrows(IllegalArgumentException.class, () -> new ErrorDeCampo("", "algo"));
        assertThrows(IllegalArgumentException.class, () -> new ErrorDeCampo("   ", "algo"));
    }

    @Test
    @DisplayName("rechaza quedarse sin mensaje para la persona")
    void sinMensaje() {
        assertThrows(IllegalArgumentException.class, () -> new ErrorDeCampo("nombre", null));
        assertThrows(IllegalArgumentException.class, () -> new ErrorDeCampo("nombre", ""));
        assertThrows(IllegalArgumentException.class, () -> new ErrorDeCampo("nombre", "  "));
    }
}
