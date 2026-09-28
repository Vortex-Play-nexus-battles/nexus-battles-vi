package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClient;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClientException;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClientHttp;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientException;
import com.nexusbattles.ms_subastas.subastas.realtime.SubastaActualizadaEvent;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 7.7.10 del documento del curso: «Posible solo si no hay pujas registradas»,
 * «Penalizacion del 50% de la comision pagada si se cancela», «No permitida en
 * las ultimas 6 horas de la subasta». Y lo que el documento no dice pero la
 * cancelacion necesita: que el cobro y la liberacion del producto se deshagan
 * si la cancelacion no llega a confirmarse.
 */
@DisplayName("Cancelar una subasta (7.7.10)")
class CancelacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    private final SubastaRepository subastas = mock(SubastaRepository.class);
    private final PujaRepository pujas = mock(PujaRepository.class);
    private final PujaAutomaticaRepository automaticas = mock(PujaAutomaticaRepository.class);
    private final FinanzasPublicacionClient finanzas = mock(FinanzasPublicacionClient.class);
    private final InventarioClient inventario = mock(InventarioClient.class);
    private final AvisosDeSubasta avisos = mock(AvisosDeSubasta.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    private CancelacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CancelacionService(subastas, pujas, automaticas, finanzas, inventario, avisos, eventos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Activa, sin pujas, cerrando dentro de 24 h y con la comision de 48 h (3 creditos) pagada. */
    private Subasta subasta(Duration faltan, BigDecimal comision) {
        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, new BigDecimal("100"),
                BigDecimal.TEN, null, null, EstadoSubasta.ACTIVA, AHORA.plus(faltan), 0L);
        subasta.setElementoInventarioId("elemento-" + subasta.getId());
        subasta.setComisionCobrada(comision);
        when(subastas.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        return subasta;
    }

    private Subasta subastaCancelable() {
        return subasta(Duration.ofHours(24), new BigDecimal("3"));
    }

    private OperacionRechazadaException.Motivo motivoAlCancelar(Subasta subasta, UUID quien) {
        return assertThrows(OperacionRechazadaException.class, () -> servicio.cancelar(subasta.getId(), quien))
                .getMotivo();
    }

    // --- el camino feliz ------------------------------------------------------

    @Test
    @DisplayName("sin pujas y a tiempo: cobra el 50 % de la comision, libera el producto, cierra y avisa")
    void cancelaCobrandoLaMitadDeLaComision() {
        Subasta subasta = subastaCancelable();

        Subasta cancelada = servicio.cancelar(subasta.getId(), VENDEDOR);

        assertEquals(EstadoSubasta.CANCELADA, cancelada.getEstado());
        assertEquals(AHORA, cancelada.getCerradaEn());
        assertEquals(0, new BigDecimal("1.50").compareTo(cancelada.getPenalizacionCobrada()));
        verify(finanzas).debitarPenalizacionCancelacion(VENDEDOR, new BigDecimal("1.50"), subasta.getId());
        verify(inventario).liberarReserva(subasta.getElementoInventarioId(), subasta.getId(),
                "cancelacion-" + subasta.getId());
        verify(subastas).save(subasta);
        var orden = inOrder(avisos, automaticas);
        // Los avisos leen quien tenia automatica ANTES de desactivarlas.
        orden.verify(avisos).cancelada(subasta);
        orden.verify(automaticas).desactivarTodas(subasta.getId());
        verify(eventos).publishEvent(any(SubastaActualizadaEvent.class));
        verify(finanzas, never()).compensarPenalizacionCancelacion(any(), any());
    }

    @Test
    @DisplayName("a 24 h la comision es 1 y la penalizacion 0,50")
    void penalizacionDeUnaSubastaDe24Horas() {
        Subasta subasta = subasta(Duration.ofHours(20), BigDecimal.ONE);

        servicio.cancelar(subasta.getId(), VENDEDOR);

        verify(finanzas).debitarPenalizacionCancelacion(VENDEDOR, new BigDecimal("0.50"), subasta.getId());
    }

    @Test
    @DisplayName("sin comision cobrada (Maestro de Juego o subasta anterior a B8) no se cobra nada")
    void sinComisionNoHayPenalizacion() {
        Subasta subasta = subasta(Duration.ofHours(24), null);

        Subasta cancelada = servicio.cancelar(subasta.getId(), VENDEDOR);

        assertEquals(EstadoSubasta.CANCELADA, cancelada.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(cancelada.getPenalizacionCobrada()));
        verifyNoInteractions(finanzas);
        verify(inventario).liberarReserva(any(), any(), any());
    }

    @Test
    @DisplayName("repetir la cancelacion devuelve la misma y no cobra dos veces")
    void cancelarDosVecesEsIdempotente() {
        Subasta subasta = subastaCancelable();
        servicio.cancelar(subasta.getId(), VENDEDOR);

        Subasta otraVez = servicio.cancelar(subasta.getId(), VENDEDOR);

        assertEquals(EstadoSubasta.CANCELADA, otraVez.getEstado());
        verify(finanzas, times(1)).debitarPenalizacionCancelacion(any(), any(), any());
        verify(avisos, times(1)).cancelada(any());
    }

    // --- las reglas de 7.7.10 ---------------------------------------------------

    @Test
    @DisplayName("solo el vendedor puede cancelar: una ajena es 403 y no toca nada")
    void unaAjenaNoSeCancela() {
        Subasta subasta = subastaCancelable();

        assertEquals(OperacionRechazadaException.Motivo.NO_ES_EL_VENDEDOR,
                motivoAlCancelar(subasta, UUID.randomUUID()));
        assertEquals(EstadoSubasta.ACTIVA, subasta.getEstado());
        verifyNoInteractions(finanzas, inventario, avisos, eventos);
    }

    @Test
    @DisplayName("con una puja registrada no se cancela (mejor postor)")
    void conMejorPostorNoSeCancela() {
        Subasta subasta = subastaCancelable();
        subasta.setMejorPostorId(UUID.randomUUID());

        assertEquals(OperacionRechazadaException.Motivo.CANCELACION_CON_PUJAS, motivoAlCancelar(subasta, VENDEDOR));
        verifyNoInteractions(finanzas, inventario, avisos);
    }

    @Test
    @DisplayName("con pujas contadas aunque ya no haya mejor postor tampoco")
    void conPujasContadasNoSeCancela() {
        Subasta subasta = subastaCancelable();
        subasta.setCantidadPujas(1);

        assertEquals(OperacionRechazadaException.Motivo.CANCELACION_CON_PUJAS, motivoAlCancelar(subasta, VENDEDOR));
    }

    @Test
    @DisplayName("y si hay pujas en la tabla aunque la subasta no las cuente, tampoco")
    void conPujasEnLaTablaNoSeCancela() {
        Subasta subasta = subastaCancelable();
        when(pujas.existsBySubastaId(subasta.getId())).thenReturn(true);

        assertEquals(OperacionRechazadaException.Motivo.CANCELACION_CON_PUJAS, motivoAlCancelar(subasta, VENDEDOR));
        verifyNoInteractions(finanzas, inventario);
    }

    @Test
    @DisplayName("a exactamente 6 h del cierre ya no se puede")
    void enElLimiteDeLasSeisHorasNoSeCancela() {
        Subasta subasta = subasta(Duration.ofHours(6), new BigDecimal("3"));

        assertEquals(OperacionRechazadaException.Motivo.CANCELACION_FUERA_DE_PLAZO,
                motivoAlCancelar(subasta, VENDEDOR));
        verifyNoInteractions(finanzas, inventario, avisos);
    }

    @Test
    @DisplayName("dentro de las ultimas 6 h tampoco")
    void enLasUltimasSeisHorasNoSeCancela() {
        Subasta subasta = subasta(Duration.ofMinutes(30), new BigDecimal("3"));

        assertEquals(OperacionRechazadaException.Motivo.CANCELACION_FUERA_DE_PLAZO,
                motivoAlCancelar(subasta, VENDEDOR));
    }

    @Test
    @DisplayName("un segundo antes de las 6 h si se puede")
    void justoAntesDeLasSeisHorasSeCancela() {
        Subasta subasta = subasta(Duration.ofHours(6).plusSeconds(1), new BigDecimal("3"));

        assertEquals(EstadoSubasta.CANCELADA, servicio.cancelar(subasta.getId(), VENDEDOR).getEstado());
    }

    @Test
    @DisplayName("una subasta ya terminada no se cancela")
    void unaTerminadaNoSeCancela() {
        Subasta subasta = subastaCancelable();
        subasta.setEstado(EstadoSubasta.ADJUDICADA);

        assertEquals(OperacionRechazadaException.Motivo.SUBASTA_NO_ACTIVA, motivoAlCancelar(subasta, VENDEDOR));
    }

    @Test
    @DisplayName("una subasta que no existe es 404")
    void unaInexistenteEsNoEncontrada() {
        UUID id = UUID.randomUUID();
        when(subastas.findByIdParaActualizar(id)).thenReturn(Optional.empty());

        assertThrows(SubastaNoEncontradaException.class, () -> servicio.cancelar(id, VENDEDOR));
    }

    // --- cobro y compensacion -----------------------------------------------------

    @Test
    @DisplayName("sin creditos para la penalizacion: 422 SALDO_INSUFICIENTE y la subasta sigue activa")
    void sinSaldoParaLaPenalizacion() {
        Subasta subasta = subastaCancelable();
        doThrow(new PublicacionSubastaException(PublicacionSubastaException.Motivo.REGLA_NEGOCIO,
                FinanzasPublicacionClientHttp.SALDO_INSUFICIENTE, "sin saldo"))
                .when(finanzas).debitarPenalizacionCancelacion(any(), any(), any());

        assertEquals(OperacionRechazadaException.Motivo.SALDO_INSUFICIENTE, motivoAlCancelar(subasta, VENDEDOR));
        assertEquals(EstadoSubasta.ACTIVA, subasta.getEstado());
        verifyNoInteractions(inventario, avisos, eventos);
        verify(finanzas, never()).compensarPenalizacionCancelacion(any(), any());
    }

    @Test
    @DisplayName("otro rechazo de finanzas sube tal cual")
    void otroRechazoDeFinanzasSubeTalCual() {
        Subasta subasta = subastaCancelable();
        PublicacionSubastaException rechazo = new PublicacionSubastaException("otra cosa");
        doThrow(rechazo).when(finanzas).debitarPenalizacionCancelacion(any(), any(), any());

        assertSame(rechazo, assertThrows(PublicacionSubastaException.class,
                () -> servicio.cancelar(subasta.getId(), VENDEDOR)));
        verifyNoInteractions(inventario);
    }

    @Test
    @DisplayName("finanzas caido: no se cancela ni se libera nada")
    void finanzasCaido() {
        Subasta subasta = subastaCancelable();
        doThrow(new FinanzasPublicacionClientException("caido"))
                .when(finanzas).debitarPenalizacionCancelacion(any(), any(), any());

        assertThrows(FinanzasPublicacionClientException.class, () -> servicio.cancelar(subasta.getId(), VENDEDOR));
        assertEquals(EstadoSubasta.ACTIVA, subasta.getEstado());
        verifyNoInteractions(inventario, avisos);
    }

    @Test
    @DisplayName("si inventario falla despues de cobrar, se devuelve la penalizacion")
    void inventarioFallaDespuesDeCobrar() {
        Subasta subasta = subastaCancelable();
        doThrow(new InventarioClientException("caido")).when(inventario).liberarReserva(any(), any(), any());

        assertThrows(InventarioClientException.class, () -> servicio.cancelar(subasta.getId(), VENDEDOR));

        verify(finanzas).compensarPenalizacionCancelacion(subasta.getId(), "cancelacion-fallida");
        verify(inventario, never()).reservar(any(), any(), any(), any());
        verifyNoInteractions(avisos, eventos);
    }

    @Test
    @DisplayName("con transaccion: si el commit se deshace, se devuelve la penalizacion y se vuelve a bloquear el producto")
    void rollbackCompensaLoHechoFuera() {
        Subasta subasta = subastaCancelable();
        TransactionSynchronizationManager.initSynchronization();

        servicio.cancelar(subasta.getId(), VENDEDOR);
        // Todavia nada que compensar: la transaccion no ha terminado.
        verify(finanzas, never()).compensarPenalizacionCancelacion(any(), any());

        for (TransactionSynchronization sincronizacion : TransactionSynchronizationManager.getSynchronizations()) {
            sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(finanzas).compensarPenalizacionCancelacion(subasta.getId(), "cancelacion-fallida");
        verify(inventario).reservar(subasta.getElementoInventarioId(), VENDEDOR, subasta.getId(),
                "recancelacion-" + subasta.getId());
    }

    @Test
    @DisplayName("con transaccion confirmada no se compensa nada")
    void commitNoCompensa() {
        Subasta subasta = subastaCancelable();
        TransactionSynchronizationManager.initSynchronization();

        servicio.cancelar(subasta.getId(), VENDEDOR);
        for (TransactionSynchronization sincronizacion : TransactionSynchronizationManager.getSynchronizations()) {
            sincronizacion.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            sincronizacion.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        }

        verify(finanzas, never()).compensarPenalizacionCancelacion(any(), any());
        verify(inventario, never()).reservar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("si la compensacion tambien falla se registra y se sigue: no tapa el fallo original")
    void compensacionFallidaNoTapaElFallo() {
        Subasta subasta = subastaCancelable();
        TransactionSynchronizationManager.initSynchronization();
        servicio.cancelar(subasta.getId(), VENDEDOR);
        doThrow(new FinanzasPublicacionClientException("caido")).when(finanzas)
                .compensarPenalizacionCancelacion(any(), anyString());
        doThrow(new InventarioClientException("caido")).when(inventario).reservar(any(), any(), any(), any());

        for (TransactionSynchronization sincronizacion : TransactionSynchronizationManager.getSynchronizations()) {
            assertDoesNotThrow(() -> sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        }
        verify(finanzas).compensarPenalizacionCancelacion(eq(subasta.getId()), anyString());
    }
}
