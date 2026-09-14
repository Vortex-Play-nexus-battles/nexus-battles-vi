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

    private PublicarSubastaRequest solicitud() {
        return new PublicarSubastaRequest("elemento-1", productoId, DuracionSubasta.H24, BigDecimal.TEN, null);
    }

    @Test
    void publica48HorasConComisionTres() {
        var r = servicio.publicar(new PublicarSubastaRequest("elemento-1", productoId, DuracionSubasta.H48, BigDecimal.TEN, null), "48");
        assertEquals(new BigDecimal("3"), r.comisionCobrado());
        assertEquals(reloj.instant().plusSeconds(48 * 3600), r.fechaFin());
        assertEquals(jugador, r.vendedorId());
        assertEquals(productoId, r.productoId());
        assertEquals("elemento-1", r.elementoInventarioId());
        assertEquals(r.precioInicial(), r.ofertaVigente());
    }

    @Test
    void maestroExento() {
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(jugador, true));
        assertEquals(BigDecimal.ZERO, servicio.publicar(solicitud(), "mdj").comisionCobrado());
        verifyNoInteractions(finanzas);
    }

    @Test
    void saldoInsuficienteLiberaSinCompensarDebitoNoRealizado() {
        doThrow(new PublicacionSubastaException("Saldo insuficiente")).when(finanzas).debitarComision(any(), any(), any(), any());
        assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "saldo"));
        verify(inventario).liberarReserva(eq("elemento-1"), any(), eq("saldo"));
        verify(finanzas, never()).compensarDebito(any(), any(), any(), any());
        verifyNoInteractions(subastas);
    }

    @Test
    void elementoInexistente() {
        when(inventario.buscar(any())).thenReturn(Optional.empty());
        assertEquals(PublicacionSubastaException.Motivo.NO_ENCONTRADO,
                assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x")).getMotivo());
        verifyNoInteractions(finanzas, subastas);
    }

    @Test
    void elementoAjeno() {
        when(inventario.buscar(any())).thenReturn(Optional.of(new InventarioClient.ElementoInventario("elemento-1", productoId, UUID.randomUUID(), false)));
        assertEquals(PublicacionSubastaException.Motivo.PROHIBIDO,
                assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x")).getMotivo());
        verifyNoInteractions(finanzas, subastas);
    }

    @Test
    void productoInconsistente() {
        when(inventario.buscar(any())).thenReturn(Optional.of(new InventarioClient.ElementoInventario("elemento-1", UUID.randomUUID(), jugador, false)));
        assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x"));
        verifyNoInteractions(catalogo, finanzas, subastas);
    }

    @Test
    void equipado() {
        when(inventario.buscar(any())).thenReturn(Optional.of(new InventarioClient.ElementoInventario("elemento-1", productoId, jugador, true)));
        assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x"));
        verifyNoInteractions(catalogo, finanzas, subastas);
    }

    @Test
    void noSubastable() {
        when(catalogo.buscar(productoId)).thenReturn(Optional.of(new CatalogoProductosClient.Producto(productoId, "Premium", null, null, null, null, null, false)));
        assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x"));
        verify(inventario, never()).reservar(any(), any(), any(), any());
    }

    @Test
    void sancionActiva() {
        when(sanciones.tieneSancionActiva(jugador)).thenReturn(true);
        assertEquals(PublicacionSubastaException.Motivo.PROHIBIDO,
                assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x")).getMotivo());
        verifyNoInteractions(inventario, finanzas, subastas);
    }

    @Test
    void claveConOtraSolicitud() {
        servicio.publicar(solicitud(), "x");
        var otra = new PublicarSubastaRequest("elemento-1", productoId, DuracionSubasta.H48, BigDecimal.TEN, null);
        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO,
                assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(otra, "x")).getMotivo());
    }

    @Test
    void otroUsuarioNoObtieneRespuestaCacheada() {
        servicio.publicar(solicitud(), "x");
        when(identidad.actual()).thenReturn(new IdentidadClient.Identidad(UUID.randomUUID(), false));
        assertEquals(PublicacionSubastaException.Motivo.PROHIBIDO,
                assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x")).getMotivo());
    }

    @Test
    void unidadActivaCompensa() {
        var causa = new org.hibernate.exception.ConstraintViolationException("sin analizar texto",
                new java.sql.SQLException("restriccion", "23505"), "uq_subastas_elemento_inventario_activa");
        var original = new org.springframework.dao.DataIntegrityViolationException("persistencia", causa);
        when(subastas.saveAndFlush(any())).thenThrow(original);
        var fallo = assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x"));
        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO, fallo.getMotivo());
        assertSame(original, fallo.getCause());
        verify(finanzas).compensarDebito(eq(jugador), eq(BigDecimal.ONE), any(), eq("x"));
        verify(inventario).liberarReserva(eq("elemento-1"), any(), eq("x"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"otra_restriccion", "subastas_pkey"})
    void otraIntegridadConservaExcepcionOriginal(String constraint) {
        var causa = new org.hibernate.exception.ConstraintViolationException("uq_subastas_elemento_inventario_activa",
                new java.sql.SQLException("error", "23505"), constraint);
        var original = new org.springframework.dao.DataIntegrityViolationException("uq_subastas_elemento_inventario_activa", causa);
        when(subastas.saveAndFlush(any())).thenThrow(original);
        assertSame(original, assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> servicio.publicar(solicitud(), "otro")));
        verify(finanzas).compensarDebito(eq(jugador), eq(BigDecimal.ONE), any(), eq("otro"));
        verify(inventario).liberarReserva(eq("elemento-1"), any(), eq("otro"));
    }

    @Test
    void incrementoSinDefinirImpidePublicar() {
        servicio = new PublicarSubastaApplicationService(subastas, inventario, catalogo, finanzas, identidad,
                sanciones, idempotencia, new CalculadorComisionPublicacion(), reloj, "");
        assertEquals(PublicacionSubastaException.Motivo.DEPENDENCIA_NO_DISPONIBLE,
                assertThrows(PublicacionSubastaException.class, () -> servicio.publicar(solicitud(), "x")).getMotivo());
        verifyNoInteractions(inventario, finanzas, subastas);
    }

    @Test
    void rollbackDespuesDeFlushCompensaYNoPublicaIdempotencia() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            servicio.publicar(solicitud(), "rollback");
            assertTrue(idempotencia.buscar(jugador + ":rollback").isEmpty());
            for (var sync : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(finanzas).compensarDebito(eq(jugador), eq(BigDecimal.ONE), any(), eq("rollback"));
            verify(inventario).liberarReserva(eq("elemento-1"), any(), eq("rollback"));
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void commitPublicaIdempotenciaSinCompensar() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            var respuesta = servicio.publicar(solicitud(), "commit");
            assertTrue(idempotencia.buscar(jugador + ":commit").isEmpty());
            for (var sync : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED);
            }
            assertEquals(respuesta, idempotencia.buscar(jugador + ":commit").orElseThrow().respuesta());
            verify(finanzas, never()).compensarDebito(any(), any(), any(), any());
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }
}
