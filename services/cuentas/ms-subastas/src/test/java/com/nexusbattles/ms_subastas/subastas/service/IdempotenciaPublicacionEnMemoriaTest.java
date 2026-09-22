package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdempotenciaPublicacionEnMemoriaTest {
    @Test
    void titularViejoNoLiberaNiConfirmaUnaNuevaAdquisicion() {
        var claves = new IdempotenciaPublicacionEnMemoria();
        var vieja = claves.adquirir("uid:k", "f");
        claves.liberar("uid:k", vieja.titular());
        var nueva = claves.adquirir("uid:k", "f");
        claves.confirmar("uid:k", vieja.titular(), respuesta());
        claves.liberar("uid:k", vieja.titular());
        claves.marcarIncierta("uid:k", vieja.titular());
        assertTrue(claves.buscar("uid:k").isEmpty());
        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO,
                assertThrows(PublicacionSubastaException.class, () -> claves.adquirir("uid:k", "f")).getMotivo());
        var respuesta = respuesta();
        claves.confirmar("uid:k", nueva.titular(), respuesta);
        claves.liberar("uid:k", nueva.titular());
        claves.marcarIncierta("uid:k", nueva.titular());
        assertEquals(respuesta, claves.adquirir("uid:k", "f").resultado().orElseThrow().respuesta());
    }

    @Test
    void commitDesconocidoNoSePresentaComoExitoNiRepiteEfectos() {
        var claves = new IdempotenciaPublicacionEnMemoria();
        var adquirida = claves.adquirir("uid:k", "f");
        claves.marcarIncierta("uid:k", adquirida.titular());
        assertEquals(PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE,
                assertThrows(PublicacionSubastaException.class, () -> claves.adquirir("uid:k", "f")).getMotivo());
        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO,
                assertThrows(PublicacionSubastaException.class, () -> claves.adquirir("uid:k", "otra")).getMotivo());
        assertTrue(claves.buscar("uid:k").isEmpty());
    }

    private PublicarSubastaResponse respuesta() {
        return new PublicarSubastaResponse(UUID.randomUUID(), UUID.randomUUID(), "unidad", UUID.randomUUID(),
                BigDecimal.TEN, BigDecimal.TEN, null, "ACTIVA", Instant.EPOCH, Instant.EPOCH.plusSeconds(86400),
                BigDecimal.ONE, null, null, null, null, null, null);
    }
}
