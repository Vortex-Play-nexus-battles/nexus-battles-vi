package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaRequest;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicarSubastaApplicationServiceTest {
    private final SubastaRepository subastas = mock(SubastaRepository.class);
    private final InventarioClient inventario = mock(InventarioClient.class);
    private final CatalogoProductosClient catalogo = mock(CatalogoProductosClient.class);
    private final FinanzasPublicacionClient finanzas = mock(FinanzasPublicacionClient.class);
    private final IdentidadClient identidad = mock(IdentidadClient.class);
    private final SancionesClient sanciones = mock(SancionesClient.class);
    private final IdempotenciaPublicacionEnMemoria idempotencia = new IdempotenciaPublicacionEnMemoria();
    private PublicarSubastaApplicationService servicio;
    private final UUID jugador = UUID.randomUUID();
    private final UUID productoId = UUID.randomUUID();
    private final Clock reloj = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void preparar() {
        servicio = new PublicarSubastaApplicationService(subastas, inventario, catalogo, finanzas, identidad,
                sanciones, idempotencia, new CalculadorComisionPublicacion(), reloj, "2.50");
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(jugador, false));
        when(sanciones.tieneSancionActiva(jugador)).thenReturn(false);
        when(inventario.buscar("elemento-1")).thenReturn(Optional.of(new InventarioClient.ElementoInventario("elemento-1", productoId, jugador, false)));
        when(catalogo.buscar(productoId)).thenReturn(Optional.of(new CatalogoProductosClient.Producto(productoId, "Espada", null, "Rara", null, "", "", true)));
        when(subastas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void publica24HorasCobraUnaUnidadYConstruyeLaSubasta() {
        PublicarSubastaResponse respuesta = servicio.publicar(new PublicarSubastaRequest("elemento-1", productoId, DuracionSubasta.H24, new BigDecimal("10"), null), "k-1");
        assertEquals(new BigDecimal("1"), respuesta.comisionCobrado());
        assertEquals(Instant.parse("2026-09-13T12:00:00Z"), respuesta.fechaFin());
        assertEquals("ACTIVA", respuesta.estado());
        verify(finanzas).debitarComision(eq(jugador), eq(BigDecimal.ONE), any(), eq("k-1"));
        verify(inventario).reservar(eq("elemento-1"), eq(jugador), any(), eq("k-1"));
    }

    @Test
    void reintentoConLaMismaClaveDevuelveElMismoResultadoSinDuplicarOperaciones() {
        PublicarSubastaRequest solicitud = new PublicarSubastaRequest("elemento-1", productoId, DuracionSubasta.H48, new BigDecimal("10"), new BigDecimal("20"));
        PublicarSubastaResponse primera = servicio.publicar(solicitud, "k-2");
        PublicarSubastaResponse segunda = servicio.publicar(solicitud, "k-2");
        assertEquals(primera, segunda);
        verify(finanzas, times(1)).debitarComision(any(), any(), any(), eq("k-2"));
        verify(inventario, times(1)).reservar(any(), any(), any(), eq("k-2"));
    }

    @Test
    void falloDePersistenciaCompensaDebitoYReserva() {
        when(subastas.saveAndFlush(any())).thenThrow(new RuntimeException("db"));
        assertThrows(RuntimeException.class, () -> servicio.publicar(new PublicarSubastaRequest("elemento-1", productoId, DuracionSubasta.H24, new BigDecimal("10"), null), "k-3"));
        verify(finanzas).compensarDebito(eq(jugador), eq(BigDecimal.ONE), any(), eq("k-3"));
        verify(inventario).liberarReserva(eq("elemento-1"), any(), eq("k-3"));
    }
}
