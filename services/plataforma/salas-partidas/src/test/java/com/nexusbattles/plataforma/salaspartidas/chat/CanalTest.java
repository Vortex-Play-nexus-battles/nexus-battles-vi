package com.nexusbattles.plataforma.salaspartidas.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class CanalTest {

    @Test
    @DisplayName("el canal de sala apunta al destino del contrato y se reconstruye desde su clave")
    void canalDeSala() {
        UUID id = UUID.randomUUID();
        Canal sala = Canal.deSala(id);

        assertEquals("/tema/salas/" + id + "/chat", sala.destino());
        assertEquals("sala:" + id, sala.clave());
        assertEquals(sala, Canal.desdeClave(sala.clave()));
    }

    @Test
    @DisplayName("el canal general no tiene sala y usa el destino de la vista general")
    void canalGeneral() {
        Canal general = Canal.general();

        assertTrue(general.esGeneral());
        assertEquals("/tema/chat/general", general.destino());
        assertEquals(general, Canal.desdeClave("general"));
        assertThrows(IllegalArgumentException.class, () -> Canal.deSala(null));
    }
}
