package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Largo maximo del texto — contrato 1.11.0, decision del Product Owner sobre el
 * issue 34 (revision final del modulo Jugador / Inventario, 13-oct).
 */
class SolicitudDePublicacionTest {

    private static SolicitudDePublicacion con(String texto) {
        return new SolicitudDePublicacion(
                "c-1", "uid-1", "Lyra", texto, List.of(), null, Instant.parse("2026-10-13T00:00:00Z"));
    }

    @Test
    @DisplayName("2000 caracteres entran")
    void dosMilEntran() {
        String texto = "a".repeat(SolicitudDePublicacion.MAXIMO_DE_CARACTERES);

        assertEquals(2000, con(texto).texto().length());
    }

    @Test
    @DisplayName("2001 caracteres se rechazan con TextoDemasiadoLargo, que dice el maximo y cuantos llegaron")
    void dosMilUnoNo() {
        String texto = "a".repeat(SolicitudDePublicacion.MAXIMO_DE_CARACTERES + 1);

        var rechazo = assertThrows(SolicitudDePublicacion.TextoDemasiadoLargo.class, () -> con(texto));

        assertInstanceOf(IllegalArgumentException.class, rechazo);
        assertTrue(rechazo.getMessage().contains("2000"));
        assertTrue(rechazo.getMessage().contains("2001"));
    }

    @Test
    @DisplayName("se cuentan caracteres y no unidades UTF-16: 2000 emojis entran")
    void unEmojiCuentaUno() {
        String emoji = new String(Character.toChars(0x1F5E1)); // daga, fuera del plano basico
        String texto = emoji.repeat(SolicitudDePublicacion.MAXIMO_DE_CARACTERES);

        assertEquals(4000, texto.length());
        assertEquals(texto, con(texto).texto());
        assertThrows(SolicitudDePublicacion.TextoDemasiadoLargo.class, () -> con(texto + emoji));
    }

    @Test
    @DisplayName("sin texto sigue siendo obligatorio (RN-CMT-001), con su propio rechazo")
    void sinTexto() {
        var rechazo = assertThrows(IllegalArgumentException.class, () -> con("   "));

        assertFalse(rechazo instanceof SolicitudDePublicacion.TextoDemasiadoLargo);
    }
}
