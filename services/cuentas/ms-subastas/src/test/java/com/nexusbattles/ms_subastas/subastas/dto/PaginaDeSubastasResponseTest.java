package com.nexusbattles.ms_subastas.subastas.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-SUB-011. Verifica que PaginaDeSubastasResponse.desde(...) traduce un
 * Page de Spring Data a los nombres de campo exactos del contrato
 * (contenido/pagina/tamanoPagina/totalElementos/totalPaginas) -- no los
 * nombres que Page usa internamente (content/number/size/totalElements).
 */
class PaginaDeSubastasResponseTest {

    private SubastaResumenResponse itemDePrueba() {
        return new SubastaResumenResponse(
            UUID.randomUUID(), "Espada del Alba Eterna", "ARMA", "Legendaria",
            "https://cdn.nexusbattles.test/armas/espada.png",
            new BigDecimal("100.00"), new BigDecimal("150.00"), new BigDecimal("300.00"),
            12, Instant.now().plusSeconds(3600), true, UUID.randomUUID().toString()
        );
    }

    @Test
    void mapeaContenidoYMetadatosDePaginacion() {
        // Tamaño de pagina 2 (no 16) a proposito: con offset 0 + tamaño 16
        // > total 4, PageImpl "corrige" el total a offset + contenido.size()
        // en vez de respetar el que se le paso -- una pagina de tamaño 2 con
        // 4 en total si es un escenario internamente consistente.
        List<SubastaResumenResponse> contenido = List.of(itemDePrueba(), itemDePrueba());
        Page<SubastaResumenResponse> page = new PageImpl<>(
            contenido, PageRequest.of(0, 2), 4L);

        PaginaDeSubastasResponse respuesta = PaginaDeSubastasResponse.desde(page);

        assertEquals(2, respuesta.contenido().size());
        assertEquals(0, respuesta.pagina());
        assertEquals(2, respuesta.tamanoPagina());
        assertEquals(4L, respuesta.totalElementos());
        assertEquals(2, respuesta.totalPaginas());
    }

    @Test
    void unaPaginaVaciaNoRevientaYQuedaEnCero() {
        Page<SubastaResumenResponse> page = new PageImpl<>(
            List.of(), PageRequest.of(0, 16), 0L);

        PaginaDeSubastasResponse respuesta = PaginaDeSubastasResponse.desde(page);

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(0L, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
    }

    @Test
    void calculaTotalPaginasCorrectamenteCuandoNoEsExacto() {
        // 5 subastas totales, tamano de pagina 2 -> 3 paginas (2, 2, 1)
        Page<SubastaResumenResponse> page = new PageImpl<>(
            List.of(itemDePrueba(), itemDePrueba()), PageRequest.of(0, 2), 5L);

        PaginaDeSubastasResponse respuesta = PaginaDeSubastasResponse.desde(page);

        assertEquals(3, respuesta.totalPaginas());
    }
}
