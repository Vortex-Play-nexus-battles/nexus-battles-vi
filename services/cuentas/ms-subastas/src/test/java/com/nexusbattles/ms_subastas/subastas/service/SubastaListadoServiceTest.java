package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.FiltrosSubasta;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-SUB-011. Prueba unitaria de SubastaListadoService con SubastaRepository
 * mockeado -- a diferencia de SubastaSpecificationsIT (que verifica que el
 * SQL generado sea correcto contra Postgres real), esta prueba verifica dos
 * cosas que un mock si puede confirmar bien: que el Sort se arma con la
 * prioridad TEMPORAL del Maestro de Juego primero (pendiente de confirmar
 * con Edwin, ver comentario en SubastaListadoService), y que el mapeo final
 * a PaginaDeSubastasResponse es correcto.
 *
 * Sin stub generico en @BeforeEach a proposito: MockitoExtension en modo
 * estricto marca error un stub que una prueba no llega a usar. Cada prueba
 * que necesita el mock configurado lo hace ella misma.
 *
 * Clock.fixed inyectado, nunca Instant.now() directo en la prueba tampoco --
 * mismo criterio que exige ReglasDeArquitecturaTest (ArchUnit) en el
 * codigo de produccion.
 */
@ExtendWith(MockitoExtension.class)
class SubastaListadoServiceTest {

    private static final Clock RELOJ_FIJO =
        Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SubastaRepository subastaRepository;

    private SubastaListadoService servicio;

    @BeforeEach
    void configurar() {
        servicio = new SubastaListadoService(subastaRepository, RELOJ_FIJO);
    }

    private FiltrosSubasta filtrosVacios(String ordenarPor) {
        return new FiltrosSubasta(null, null, null, null, null, null, null, null, null, ordenarPor);
    }

    private void mockearPaginaVacia() {
        when(subastaRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
    }

    private Sort capturarSortUsado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(subastaRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue().getSort();
    }

    @Test
    void elMaestroDeJuegoSiempreVaPrimeroSinImportarElOrdenElegido() {
        mockearPaginaVacia();
        // TEMPORAL, pendiente de confirmar con Edwin (SRS 7.7.4) -- ver
        // comentario en SubastaListadoService.construirOrden. Esta prueba
        // documenta el comportamiento actual, no una decision cerrada.
        servicio.listar(filtrosVacios("PRECIO_ASC"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();

        assertEquals("esMaestroDeJuego", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    @Test
    void ordenarPorPrecioAscUsaOfertaVigenteAscendenteComoSegundoCriterio() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("PRECIO_ASC"), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("ofertaVigente", segundo.getProperty());
        assertEquals(Sort.Direction.ASC, segundo.getDirection());
    }

    @Test
    void ordenarPorPrecioDescUsaOfertaVigenteDescendente() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("PRECIO_DESC"), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("ofertaVigente", segundo.getProperty());
        assertEquals(Sort.Direction.DESC, segundo.getDirection());
    }

    @Test
    void ordenarPorTiempoRestanteUsaFechaFinAscendente() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("TIEMPO_RESTANTE"), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("fechaFin", segundo.getProperty());
        assertEquals(Sort.Direction.ASC, segundo.getDirection());
    }

    @Test
    void ordenarPorPujasUsaCantidadPujasDescendente() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("PUJAS"), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("cantidadPujas", segundo.getProperty());
        assertEquals(Sort.Direction.DESC, segundo.getDirection());
    }

    @Test
    void ordenarPorPopularidadUsaVistasDescendente() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("POPULARIDAD"), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("vistas", segundo.getProperty());
        assertEquals(Sort.Direction.DESC, segundo.getDirection());
    }

    @Test
    void ordenarPorNuloCaeEnFechaPublicacionDescendentePorDefecto() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios(null), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("fechaPublicacion", segundo.getProperty());
        assertEquals(Sort.Direction.DESC, segundo.getDirection());
    }

    @Test
    void unValorDeOrdenNoReconocidoTambienCaeEnElDefault() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("ALGO_QUE_NO_EXISTE"), 0, 16);

        Sort.Order segundo = capturarSortUsado().toList().get(1);
        assertEquals("fechaPublicacion", segundo.getProperty());
    }

    @Test
    void mapeaUnaPaginaConContenidoRealCorrectamente() {
        Subasta subasta = new Subasta(null, java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            new BigDecimal("150.00"), new BigDecimal("10.00"), null, null,
            EstadoSubasta.ACTIVA, Instant.now(RELOJ_FIJO).plusSeconds(3600), 0L);
        subasta.setNombreProducto("Espada del Alba Eterna");

        when(subastaRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(subasta), Pageable.ofSize(16), 1L));

        PaginaDeSubastasResponse respuesta = servicio.listar(filtrosVacios(null), 0, 16);

        assertEquals(1, respuesta.contenido().size());
        assertEquals("Espada del Alba Eterna", respuesta.contenido().get(0).nombreProducto());
        assertEquals(1L, respuesta.totalElementos());
    }

    @Test
    void pasaLaPaginaYElTamanoPedidosAlPageable() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios(null), 2, 8);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(subastaRepository).findAll(any(Specification.class), captor.capture());

        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(8, captor.getValue().getPageSize());
    }
}
