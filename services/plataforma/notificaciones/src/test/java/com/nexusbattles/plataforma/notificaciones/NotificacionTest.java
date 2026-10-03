package com.nexusbattles.plataforma.notificaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Auditoria de DEV del 30-sep: torneos emite avisos con identificadores de
 * 101 a 106 caracteres y la bandeja solo guardaba 64. El aviso se perdia sin
 * que nadie se enterara (ver {@link Notificacion#LARGO_MAXIMO_ID}).
 */
class NotificacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T12:00:00Z");

    private static Notificacion con(String id, String tipo, String titulo) {
        return new Notificacion(id, tipo, titulo, "Cuerpo del aviso.", AHORA);
    }

    @Test
    @DisplayName("un identificador como el de torneos (mas de 100 caracteres) cabe entero")
    void elIdentificadorDeTorneosCabe() {
        String id = "torneo-" + UUID.randomUUID() + "-jugador-" + UUID.randomUUID() + "-aviso-inscripcion";
        assertTrue(id.length() > 100, "el caso real mide " + id.length());

        assertEquals(id, con(id, "TORNEO", "Inscripcion confirmada").id());
    }

    @Test
    @DisplayName("hasta 200 caracteres se acepta; desde 201 se rechaza con el motivo, sin recortar")
    void unIdentificadorDemasiadoLargoSeRechaza() {
        assertEquals(200, con("x".repeat(200), "TORNEO", "Titulo").id().length());

        IllegalArgumentException rechazo = assertThrows(IllegalArgumentException.class,
                () -> con("x".repeat(201), "TORNEO", "Titulo"));
        assertTrue(rechazo.getMessage().contains("200"), rechazo.getMessage());
        assertTrue(rechazo.getMessage().contains("201"), rechazo.getMessage());
    }

    @Test
    @DisplayName("el tipo y el titulo tienen el largo de su columna: lo que no cabe es un 400, no un 409")
    void tipoYTituloConSuLargo() {
        assertThrows(IllegalArgumentException.class, () -> con("evt-1", "T".repeat(41), "Titulo"));
        assertThrows(IllegalArgumentException.class, () -> con("evt-1", "TORNEO", "t".repeat(201)));
        assertEquals(200, con("evt-1", "T".repeat(40), "t".repeat(200)).titulo().length());
    }
}
