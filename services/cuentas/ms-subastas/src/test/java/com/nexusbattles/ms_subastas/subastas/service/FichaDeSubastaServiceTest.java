package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.panel.repository.VistaSubastaRepository;
import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;
import com.nexusbattles.ms_subastas.pujas.service.SubastaNoEncontradaException;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.reglas.PoliticaAlVencer;
import com.nexusbattles.ms_subastas.reglas.ReglasVigentes;
import com.nexusbattles.ms_subastas.subastas.dto.ReglasVigentesResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SubastaDetalleResponse;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La ficha de una subasta en cualquier estado ({@code GET /subastas/{id}}) con
 * visualizaciones unicas y la reputacion del vendedor (7.7.9), y las reglas
 * vigentes que la interfaz ya no escribe a mano ({@code GET /subastas/reglas}).
 */
@DisplayName("Ficha de subasta y reglas vigentes (B8)")
class FichaDeSubastaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    private final SubastaRepository subastas = mock(SubastaRepository.class);
    private final VistaSubastaRepository vistas = mock(VistaSubastaRepository.class);
    private final FichaDeSubastaService servicio =
            new FichaDeSubastaService(subastas, vistas, Clock.fixed(AHORA, ZoneOffset.UTC));
    private Subasta subasta;

    @BeforeEach
    void preparar() {
        subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, new BigDecimal("120"), BigDecimal.TEN,
                new BigDecimal("500"), UUID.randomUUID(), EstadoSubasta.ACTIVA, AHORA.plusSeconds(3600), 0L);
        subasta.setNombreProducto("Espada del Alba");
        subasta.setTipoProducto(TipoProducto.ARMA);
        subasta.setVistas(7);
        subasta.setApodoVendedor("forjador");
        when(subastas.findById(subasta.getId())).thenReturn(Optional.of(subasta));
        when(subastas.terminadasPorEstado(VENDEDOR)).thenReturn(List.of(
                new Object[]{EstadoSubasta.ADJUDICADA, 3L},
                new Object[]{EstadoSubasta.SIN_ADJUDICACION, 1L}));
    }

    @Test
    @DisplayName("la ficha trae la puja minima siguiente, la compra inmediata y la reputacion del vendedor")
    void ficha() {
        SubastaDetalleResponse ficha = servicio.ficha(subasta.getId(), null);

        assertEquals("ACTIVA", ficha.estado());
        assertEquals("ARMA", ficha.tipoProducto());
        assertEquals(0, new BigDecimal("130").compareTo(ficha.pujaMinimaSiguiente()));
        assertTrue(ficha.compraInmediataDisponible());
        assertEquals("CREDITOS", ficha.metodoPago());
        assertEquals("forjador", ficha.vendedorApodo());
        assertEquals(3, ficha.reputacionVendedor().ventasCompletadas());
        assertEquals(4, ficha.reputacionVendedor().subastasTerminadas());
        assertEquals(0.75, ficha.reputacionVendedor().tasaDeExito());
        assertEquals(7, ficha.vistas());
    }

    @Test
    @DisplayName("un visitante sin sesion no cuenta como visualizacion")
    void visitanteNoCuenta() {
        servicio.ficha(subasta.getId(), null);

        verifyNoInteractions(vistas);
    }

    @Test
    @DisplayName("el vendedor mirando su propia subasta tampoco")
    void vendedorNoCuenta() {
        servicio.ficha(subasta.getId(), VENDEDOR);

        verifyNoInteractions(vistas);
    }

    @Test
    @DisplayName("G5: la ficha dice si es propia sin publicar el uid del vendedor (apodo si)")
    void esPropiaSinUid() throws Exception {
        SubastaDetalleResponse suya = servicio.ficha(subasta.getId(), VENDEDOR);
        SubastaDetalleResponse visitante = servicio.ficha(subasta.getId(), null);

        assertTrue(suya.esPropia());
        assertFalse(visitante.esPropia());
        assertEquals("forjador", visitante.vendedorApodo());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .writeValueAsString(visitante);
        assertFalse(json.contains(VENDEDOR.toString()), json);
        assertNull(visitante.vendedorId(), "el campo sigue en la forma, vacio");
    }

    @Test
    @DisplayName("un jugador cuenta una vez: la primera suma, las siguientes no")
    void jugadorCuentaUnaVez() {
        UUID jugador = UUID.randomUUID();
        when(vistas.registrarSiEsNueva(subasta.getId(), jugador, AHORA)).thenReturn(1, 0);

        assertEquals(8, servicio.ficha(subasta.getId(), jugador).vistas());
        assertEquals(7, servicio.ficha(subasta.getId(), jugador).vistas());
        verify(vistas, times(1)).sumarVista(subasta.getId());
    }

    @Test
    @DisplayName("una cerrada se puede abrir: sin puja minima y sin compra inmediata; y un vendedor nuevo no tiene tasa")
    void cerradaYVendedorNuevo() {
        subasta.cerrar(EstadoSubasta.ADJUDICADA, AHORA);
        subasta.setEsMaestroDeJuego(true);
        when(subastas.terminadasPorEstado(VENDEDOR)).thenReturn(List.of());

        SubastaDetalleResponse ficha = servicio.ficha(subasta.getId(), null);

        assertNull(ficha.pujaMinimaSiguiente());
        assertFalse(ficha.compraInmediataDisponible());
        assertEquals(AHORA, ficha.cerradaEn());
        assertEquals("DINERO_REAL", ficha.metodoPago());
        assertNull(ficha.reputacionVendedor().tasaDeExito());
        assertEquals(0, ficha.reputacionVendedor().subastasTerminadas());
    }

    @Test
    @DisplayName("una que no existe es 404")
    void inexistente() {
        UUID id = UUID.randomUUID();
        when(subastas.findById(id)).thenReturn(Optional.empty());

        assertThrows(SubastaNoEncontradaException.class, () -> servicio.ficha(id, null));
        verify(vistas, never()).registrarSiEsNueva(any(), any(), any());
    }

    @Test
    @DisplayName("las reglas vigentes: Tabla 25, las cifras del documento y lo que diga administracion")
    void reglasVigentes() {
        ReglasVigentes vigentes = FuenteDeReglas.fijas(new ParametrosPuja(), new BigDecimal("5")).vigentes();

        ReglasVigentesResponse reglas = ReglasVigentesResponse.desde(vigentes, new CalculadorComisionPublicacion());

        assertEquals(2, reglas.duraciones().size());
        assertEquals(24, reglas.duraciones().get(0).horas());
        assertEquals(0, BigDecimal.ONE.compareTo(reglas.duraciones().get(0).comision()));
        assertEquals(48, reglas.duraciones().get(1).horas());
        assertEquals(0, new BigDecimal("3").compareTo(reglas.duraciones().get(1).comision()));
        assertTrue(reglas.incrementoMinimoConfigurado());
        assertEquals(0, new BigDecimal("5").compareTo(reglas.incrementoMinimo()));
        assertEquals(10, reglas.maxSubastasActivasPorJugador());
        assertEquals(50, reglas.penalizacionCancelacionPorcentaje());
        assertEquals(6, reglas.cancelacionProhibidaUltimasHoras());
        assertEquals(60, reglas.recordatorioMinutosAntesDelCierre());
        assertEquals(7, reglas.diasParaRecoger());
        assertEquals("ENTREGAR", reglas.alVencerPendientes());
    }

    @Test
    @DisplayName("sin incremento configurado lo dice, sin inventar una cifra")
    void reglasSinIncremento() {
        ReglasVigentes vigentes = new ReglasVigentes(null, 10, 50, 5, PoliticaAlVencer.DEVOLVER_AL_VENDEDOR);

        ReglasVigentesResponse reglas = ReglasVigentesResponse.desde(vigentes, new CalculadorComisionPublicacion());

        assertFalse(reglas.incrementoMinimoConfigurado());
        assertNull(reglas.incrementoMinimo());
        assertEquals("DEVOLVER_AL_VENDEDOR", reglas.alVencerPendientes());
        assertThrows(IllegalArgumentException.class,
                () -> new ReglasVigentes(BigDecimal.ZERO, 10, 50, 5, PoliticaAlVencer.ENTREGAR));
    }
}
