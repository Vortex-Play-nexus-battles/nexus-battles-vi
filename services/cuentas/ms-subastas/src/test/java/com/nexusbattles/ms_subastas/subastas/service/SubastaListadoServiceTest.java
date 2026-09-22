package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.FiltrosSubasta;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SugerenciasResponse;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-SUB-011. Prueba unitaria de SubastaListadoService con SubastaRepository
 * mockeado -- a diferencia de SubastaRepositoryIT (que verifica que el SQL
 * generado sea correcto contra Postgres real), esta prueba verifica que el
 * servicio arma bien el Sort/Pageable, y que la deduplicacion en Java
 * (LinkedHashSet) preserva el orden que ya trae la consulta.
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

    private Subasta subastaConNombre(String nombre) {
        Subasta subasta = new Subasta(null, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("50.00"), new BigDecimal("5.00"), null, null,
            EstadoSubasta.ACTIVA, Instant.now(RELOJ_FIJO).plusSeconds(3600), 0L);
        subasta.setNombreProducto(nombre);
        return subasta;
    }

    // --- Prioridad del Maestro de Juego (RF-SUB-010, confirmado con Edwin) ---
    // Solo aplica cuando NO hay un orden explicito del usuario. En cuanto el
    // usuario elige un criterio, el orden es global (una sola dimension),
    // sin agrupar por MdJ -- de lo contrario "ordenar por precio" no seria
    // un orden global real.

    @Test
    void sinOrdenExplicitoElMaestroDeJuegoVaPrimero() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios(null), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();

        assertEquals("esMaestroDeJuego", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("fechaPublicacion", orders.get(1).getProperty());
    }

    @Test
    void conOrdenExplicitoElMaestroDeJuegoNoTienePrioridadYElOrdenEsGlobal() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("PRECIO_ASC"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();

        assertEquals(1, orders.size(),
            "con un orden explicito, esMaestroDeJuego no debe aparecer como criterio adicional");
        assertEquals("ofertaVigente", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
    }

    @Test
    void ordenarPorPrecioDescUsaOfertaVigenteDescendenteComoUnicoCriterio() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("PRECIO_DESC"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();
        assertEquals(1, orders.size());
        assertEquals("ofertaVigente", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    @Test
    void ordenarPorTiempoRestanteUsaFechaFinAscendenteComoUnicoCriterio() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("TIEMPO_RESTANTE"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();
        assertEquals(1, orders.size());
        assertEquals("fechaFin", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
    }

    @Test
    void ordenarPorPujasUsaCantidadPujasDescendenteComoUnicoCriterio() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("PUJAS"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();
        assertEquals(1, orders.size());
        assertEquals("cantidadPujas", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    @Test
    void ordenarPorPopularidadUsaVistasDescendenteComoUnicoCriterio() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("POPULARIDAD"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();
        assertEquals(1, orders.size());
        assertEquals("vistas", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
    }

    @Test
    void ordenarPorNuloCaeEnFechaPublicacionConPrioridadMdjPorDefecto() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios(null), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();
        assertEquals(2, orders.size());
        assertEquals("esMaestroDeJuego", orders.get(0).getProperty());
        assertEquals("fechaPublicacion", orders.get(1).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());
    }

    /**
     * Un valor no reconocido se trata igual que "sin seleccion" -- no
     * cuenta como que el usuario eligio explicitamente un orden, asi que
     * SI debe llevar la prioridad de Maestro de Juego, no tratarse como
     * un orden global de un criterio desconocido.
     */
    @Test
    void unValorDeOrdenNoReconocidoTambienLlevaPrioridadMdj() {
        mockearPaginaVacia();
        servicio.listar(filtrosVacios("ALGO_QUE_NO_EXISTE"), 0, 16);

        List<Sort.Order> orders = capturarSortUsado().toList();
        assertEquals(2, orders.size());
        assertEquals("esMaestroDeJuego", orders.get(0).getProperty());
        assertEquals("fechaPublicacion", orders.get(1).getProperty());
    }

    @Test
    void mapeaUnaPaginaConContenidoRealCorrectamente() {
        Subasta subasta = subastaConNombre("Espada del Alba Eterna");

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

    // --- sugerir() ---

    @Test
    void sugerirDevuelveLosNombresDeLoQueTraeElRepositorio() {
        when(subastaRepository.buscarSugeridasPorTexto(eq("esp"), any(Pageable.class)))
            .thenReturn(List.of(subastaConNombre("Espada de Hielo"), subastaConNombre("Espada de Fuego")));

        SugerenciasResponse respuesta = servicio.sugerir("esp", 8);

        assertEquals(List.of("Espada de Hielo", "Espada de Fuego"), respuesta.sugerencias());
    }

    @Test
    void sugerirDeduplicaNombresRepetidosPreservandoElOrden() {
        when(subastaRepository.buscarSugeridasPorTexto(eq("escudo"), any(Pageable.class)))
            .thenReturn(List.of(
                subastaConNombre("Escudo de Roble"),
                subastaConNombre("Escudo de Roble"),
                subastaConNombre("Escudo de Hierro")
            ));

        SugerenciasResponse respuesta = servicio.sugerir("escudo", 8);

        assertEquals(List.of("Escudo de Roble", "Escudo de Hierro"), respuesta.sugerencias());
    }

    @Test
    void sugerirPasaElLimiteComoTamanoDePagina() {
        when(subastaRepository.buscarSugeridasPorTexto(eq("esp"), any(Pageable.class)))
            .thenReturn(List.of());

        servicio.sugerir("esp", 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(subastaRepository).buscarSugeridasPorTexto(eq("esp"), captor.capture());

        assertEquals(5, captor.getValue().getPageSize());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void sugerirSinCoincidenciasDevuelveListaVacia() {
        when(subastaRepository.buscarSugeridasPorTexto(eq("xyz"), any(Pageable.class)))
            .thenReturn(List.of());

        SugerenciasResponse respuesta = servicio.sugerir("xyz", 8);

        assertTrue(respuesta.sugerencias().isEmpty());
    }
}
