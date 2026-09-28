package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.panel.model.EstadoPendiente;
import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.repository.PendienteDeRecogerRepository;
import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.reglas.PoliticaAlVencer;
import com.nexusbattles.ms_subastas.reglas.ReglasVigentes;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientException;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * «Productos pendientes de recoger» y «Tiempo limite para reclamar (7 dias)»
 * (7.7.9). Que pasa al vencer no lo dice el documento: es el parametro
 * {@code subastas.pendientes.al-vencer} (decision del PO, provisional
 * ENTREGAR), y aqui se prueban las dos politicas.
 */
@DisplayName("Pendientes de recoger (7.7.9)")
class PendientesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID GANADOR = UUID.randomUUID();
    private static final UUID VENDEDOR = UUID.randomUUID();

    private final PendienteDeRecogerRepository pendientes = mock(PendienteDeRecogerRepository.class);
    private final SubastaRepository subastas = mock(SubastaRepository.class);
    private final InventarioClient inventario = mock(InventarioClient.class);
    private final AvisosDeSubasta avisos = mock(AvisosDeSubasta.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);

    private PoliticaAlVencer politica = PoliticaAlVencer.ENTREGAR;
    private PendientesService servicio;

    @BeforeEach
    void preparar() {
        ParametrosPuja parametros = new ParametrosPuja();
        FuenteDeReglas reglas = () -> new ReglasVigentes(null, parametros.getMaxSubastasActivasPorJugador(),
                parametros.getMaxPujasActivasPorJugador(), parametros.getIntervaloMinimoSegundos(), politica);
        servicio = new PendientesService(pendientes, subastas, inventario, avisos, reglas,
                Clock.fixed(AHORA, ZoneOffset.UTC), transacciones);
    }

    private Subasta subasta(UUID id) {
        Subasta subasta = new Subasta(id, UUID.randomUUID(), VENDEDOR, new BigDecimal("150"), BigDecimal.TEN, null,
                GANADOR, EstadoSubasta.ADJUDICADA, AHORA.minus(Duration.ofDays(1)), 0L);
        subasta.setNombreProducto("Espada del Alba");
        subasta.setMiniaturaUrl("/img/espada.png");
        when(subastas.findById(id)).thenReturn(Optional.of(subasta));
        return subasta;
    }

    /** Un pendiente de GANADOR que vence dentro de {@code venceDentroDe} (negativo: ya vencio). */
    private PendienteDeRecoger pendiente(Duration venceDentroDe) {
        UUID subastaId = UUID.randomUUID();
        PendienteDeRecoger pendiente = new PendienteDeRecoger(subastaId, GANADOR, "elemento-" + subastaId,
                new BigDecimal("150"), AHORA.minus(Duration.ofDays(1)), AHORA.plus(venceDentroDe));
        when(pendientes.findByIdParaActualizar(subastaId)).thenReturn(Optional.of(pendiente));
        subasta(subastaId);
        return pendiente;
    }

    // --- listar -----------------------------------------------------------------

    @Test
    @DisplayName("sin pendientes, lista vacia y ni una consulta mas")
    void sinPendientes() {
        when(pendientes.findByGanadorIdAndEstadoOrderByVenceEnAsc(GANADOR, EstadoPendiente.PENDIENTE))
                .thenReturn(List.of());

        assertTrue(servicio.pendientesDe(GANADOR).isEmpty());
        verifyNoInteractions(subastas);
    }

    @Test
    @DisplayName("cada pendiente viene con el producto y su plazo")
    void listaConProductoYPlazo() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofDays(6));
        Subasta subasta = subastas.findById(pendiente.getSubastaId()).orElseThrow();
        when(pendientes.findByGanadorIdAndEstadoOrderByVenceEnAsc(GANADOR, EstadoPendiente.PENDIENTE))
                .thenReturn(List.of(pendiente));
        when(subastas.findAllById(List.of(pendiente.getSubastaId()))).thenReturn(List.of(subasta));

        List<PanelDtos.Pendiente> lista = servicio.pendientesDe(GANADOR);

        assertEquals(1, lista.size());
        PanelDtos.Pendiente unico = lista.getFirst();
        assertEquals("Espada del Alba", unico.nombreProducto());
        assertEquals("/img/espada.png", unico.miniaturaUrl());
        assertEquals(pendiente.getVenceEn(), unico.venceEn());
        assertEquals("PENDIENTE", unico.estado());
        assertNull(unico.resueltoEn());
    }

    // --- recoger ------------------------------------------------------------------

    @Test
    @DisplayName("recoger suelta el bloqueo de la subasta, lo marca RECOGIDO y confirma el producto en el inventario")
    void recoger() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofDays(6));

        PanelDtos.Pendiente recogido = servicio.recoger(pendiente.getSubastaId(), GANADOR);

        verify(inventario).liberarReserva(pendiente.getElementoInventarioId(), pendiente.getSubastaId(),
                "recoger-" + pendiente.getSubastaId());
        assertEquals("RECOGIDO", recogido.estado());
        assertEquals(AHORA, recogido.resueltoEn());
        verify(pendientes).save(pendiente);
        verify(avisos).recogido(any(Subasta.class), eq(pendiente));
    }

    @Test
    @DisplayName("recoger dos veces devuelve lo mismo sin volver a llamar a inventario")
    void recogerEsIdempotente() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofDays(6));
        servicio.recoger(pendiente.getSubastaId(), GANADOR);

        PanelDtos.Pendiente otraVez = servicio.recoger(pendiente.getSubastaId(), GANADOR);

        assertEquals("RECOGIDO", otraVez.estado());
        verify(inventario, times(1)).liberarReserva(any(), any(), any());
        verify(avisos, times(1)).recogido(any(), any());
    }

    @Test
    @DisplayName("el pendiente de otro jugador no existe para ti (404, sin decir de quien es)")
    void elDeOtroNoSeRecoge() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofDays(6));

        OperacionRechazadaException rechazo = assertThrows(OperacionRechazadaException.class,
                () -> servicio.recoger(pendiente.getSubastaId(), UUID.randomUUID()));

        assertEquals(OperacionRechazadaException.Motivo.PENDIENTE_NO_ENCONTRADO, rechazo.getMotivo());
        verifyNoInteractions(inventario);
    }

    @Test
    @DisplayName("lo que ya se resolvio al vencer no se recoge: 409")
    void loVencidoNoSeRecoge() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofDays(-1));
        pendiente.resolver(EstadoPendiente.ENTREGADO_AL_VENCER, AHORA.minusSeconds(60));

        OperacionRechazadaException rechazo = assertThrows(OperacionRechazadaException.class,
                () -> servicio.recoger(pendiente.getSubastaId(), GANADOR));

        assertEquals(OperacionRechazadaException.Motivo.PENDIENTE_YA_RESUELTO, rechazo.getMotivo());
        verifyNoInteractions(inventario);
    }

    @Test
    @DisplayName("si inventario no responde, el pendiente sigue pendiente")
    void inventarioCaidoNoLoResuelve() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofDays(6));
        doThrow(new InventarioClientException("caido")).when(inventario).liberarReserva(any(), any(), any());

        assertThrows(InventarioClientException.class, () -> servicio.recoger(pendiente.getSubastaId(), GANADOR));

        assertEquals(EstadoPendiente.PENDIENTE, pendiente.getEstado());
        verify(pendientes, never()).save(any());
    }

    @Test
    @DisplayName("«recoger todo»: uno que falla no deshace ni retiene a los demas")
    void recogerTodo() {
        PendienteDeRecoger bien = pendiente(Duration.ofDays(2));
        PendienteDeRecoger mal = pendiente(Duration.ofDays(5));
        when(pendientes.findByGanadorIdAndEstadoOrderByVenceEnAsc(GANADOR, EstadoPendiente.PENDIENTE))
                .thenReturn(List.of(bien, mal));
        doThrow(new InventarioClientException("caido")).when(inventario)
                .liberarReserva(eq(mal.getElementoInventarioId()), any(), any());

        PanelDtos.ResultadoDeRecogida resultado = servicio.recogerTodo(GANADOR);

        assertEquals(List.of(bien.getSubastaId()),
                resultado.recogidos().stream().map(PanelDtos.Pendiente::subastaId).toList());
        assertEquals(List.of(mal.getSubastaId()),
                resultado.fallidos().stream().map(PanelDtos.ResultadoDeRecogida.Fallo::subastaId).toList());
        assertEquals(EstadoPendiente.RECOGIDO, bien.getEstado());
        assertEquals(EstadoPendiente.PENDIENTE, mal.getEstado());
        // Cada uno en su propia transaccion.
        verify(transacciones, times(2)).getTransaction(any());
    }

    // --- vencer -------------------------------------------------------------------

    @Test
    @DisplayName("al vencer, con la politica provisional ENTREGAR, queda disponible en el inventario del ganador")
    void vencerConPoliticaEntregar() {
        PendienteDeRecoger pendiente = pendiente(Duration.ofSeconds(-1));

        assertTrue(servicio.resolverVencido(pendiente.getSubastaId()));

        verify(inventario).liberarReserva(pendiente.getElementoInventarioId(), pendiente.getSubastaId(),
                "vencido-" + pendiente.getSubastaId());
        verify(inventario, never()).transferirProducto(any(), any(), any(), any());
        assertEquals(EstadoPendiente.ENTREGADO_AL_VENCER, pendiente.getEstado());
        assertEquals(AHORA, pendiente.getResueltoEn());
        verify(avisos).pendienteVencido(any(Subasta.class), eq(pendiente), eq(PoliticaAlVencer.ENTREGAR));
    }

    @Test
    @DisplayName("con DEVOLVER_AL_VENDEDOR vuelve al vendedor y se le suelta el bloqueo")
    void vencerConPoliticaDevolver() {
        politica = PoliticaAlVencer.DEVOLVER_AL_VENDEDOR;
        PendienteDeRecoger pendiente = pendiente(Duration.ofSeconds(-1));

        assertTrue(servicio.resolverVencido(pendiente.getSubastaId()));

        var orden = inOrder(inventario);
        orden.verify(inventario).transferirProducto(pendiente.getElementoInventarioId(), VENDEDOR,
                pendiente.getSubastaId(), "devolver-" + pendiente.getSubastaId());
        orden.verify(inventario).liberarReserva(pendiente.getElementoInventarioId(), pendiente.getSubastaId(),
                "devuelto-" + pendiente.getSubastaId());
        assertEquals(EstadoPendiente.DEVUELTO_AL_VENDEDOR, pendiente.getEstado());
        verify(avisos).pendienteVencido(any(Subasta.class), eq(pendiente),
                eq(PoliticaAlVencer.DEVOLVER_AL_VENDEDOR));
    }

    @Test
    @DisplayName("lo que todavia no vence, ya se recogio o no existe no se toca")
    void soloSeResuelveLoVencidoYPendiente() {
        PendienteDeRecoger aTiempo = pendiente(Duration.ofHours(1));
        PendienteDeRecoger recogido = pendiente(Duration.ofSeconds(-1));
        recogido.resolver(EstadoPendiente.RECOGIDO, AHORA.minusSeconds(5));
        UUID inexistente = UUID.randomUUID();
        when(pendientes.findByIdParaActualizar(inexistente)).thenReturn(Optional.empty());

        assertFalse(servicio.resolverVencido(aTiempo.getSubastaId()));
        assertFalse(servicio.resolverVencido(recogido.getSubastaId()));
        assertFalse(servicio.resolverVencido(inexistente));
        verifyNoInteractions(inventario, avisos);
    }

    @Test
    @DisplayName("el trabajo programado resuelve cada vencido por separado: uno que falla no frena a los demas")
    void elTrabajoResuelveCadaUnoPorSeparado() {
        PendientesService mockServicio = mock(PendientesService.class);
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        when(pendientes.idsVencidos(EstadoPendiente.PENDIENTE, AHORA)).thenReturn(List.of(primero, segundo));
        when(mockServicio.resolverVencido(primero)).thenThrow(new InventarioClientException("caido"));

        new VencimientoDePendientesJob(pendientes, mockServicio, Clock.fixed(AHORA, ZoneOffset.UTC))
                .resolverVencidos();

        verify(mockServicio).resolverVencido(primero);
        verify(mockServicio).resolverVencido(segundo);
    }
}
