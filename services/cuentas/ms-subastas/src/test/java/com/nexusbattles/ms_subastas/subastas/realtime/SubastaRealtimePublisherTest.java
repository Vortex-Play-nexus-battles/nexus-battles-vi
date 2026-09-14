package com.nexusbattles.ms_subastas.subastas.realtime;

import com.nexusbattles.ms_subastas.subastas.dto.SubastaResumenResponse;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubastaRealtimePublisherTest {

    @Test
    void enviaUnResumenActualizadoAlCanalDelListado() {
        SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
        SubastaRealtimePublisher publisher = new SubastaRealtimePublisher(mensajeria);
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());
        subasta.setElementoInventarioId("unidad-001");
        subasta.setNombreProducto("Espada de Hielo");
        subasta.setOfertaVigente(new BigDecimal("125.00"));
        subasta.setCantidadPujas(3);
        subasta.setFechaFin(Instant.parse("2026-09-15T12:00:00Z"));
        SubastaActualizadaEvent evento = new SubastaActualizadaEvent(this, subasta);

        assertSame(this, evento.getSource());
        assertSame(subasta, evento.getSubasta());
        publisher.alActualizarSubasta(evento);

        ArgumentCaptor<SubastaResumenResponse> mensaje =
            ArgumentCaptor.forClass(SubastaResumenResponse.class);
        verify(mensajeria).convertAndSend(eq("/topic/subastas/listado"), mensaje.capture());
        SubastaResumenResponse resumen = mensaje.getValue();
        assertEquals(subasta.getId(), resumen.id());
        assertEquals("Espada de Hielo", resumen.nombreProducto());
        assertEquals(new BigDecimal("125.00"), resumen.ofertaVigente());
        assertEquals(3, resumen.cantidadPujas());
        assertEquals(subasta.getFechaFin(), resumen.fechaFin());
        assertNull(resumen.vendedorId());
        verifyNoMoreInteractions(mensajeria);
    }
}
