package com.nexusbattles.ms_subastas.subastas.dto;

import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-SUB-011. Verifica que SubastaResumenResponse.desde(...) mapea cada
 * campo correctamente, incluidos los casos donde un campo BORRADOR (ver
 * Subasta.java) llega null -- son nullable a proposito mientras el diseno
 * conjunto con Edwin no cierre, y un mapeo manual como este es justo donde
 * suele colarse un NullPointerException no anticipado.
 */
class SubastaResumenResponseTest {

    private Subasta subastaCompleta() {
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());
        subasta.setProductoId(UUID.randomUUID());
        subasta.setVendedorId(UUID.randomUUID());
        subasta.setOfertaVigente(new BigDecimal("150.00"));
        subasta.setIncrementoMinimo(new BigDecimal("10.00"));
        subasta.setPrecioCompraInmediata(new BigDecimal("300.00"));
        subasta.setEstado(EstadoSubasta.ACTIVA);
        subasta.setFechaFin(Instant.now().plusSeconds(3600));
        subasta.setNombreProducto("Espada del Alba Eterna");
        subasta.setTipoProducto(TipoProducto.ARMA);
        subasta.setRareza("Legendaria");
        subasta.setMiniaturaUrl("https://cdn.nexusbattles.test/armas/espada.png");
        subasta.setPrecioInicial(new BigDecimal("100.00"));
        subasta.setCantidadPujas(12);
        subasta.setEsMaestroDeJuego(true);
        return subasta;
    }

    @Test
    void mapeaTodosLosCamposCuandoEstanCompletos() {
        Subasta subasta = subastaCompleta();

        SubastaResumenResponse respuesta = SubastaResumenResponse.desde(subasta);

        assertEquals(subasta.getId(), respuesta.id());
        assertEquals("Espada del Alba Eterna", respuesta.nombreProducto());
        assertEquals("ARMA", respuesta.tipoProducto());
        assertEquals("Legendaria", respuesta.rareza());
        assertEquals(0, new BigDecimal("100.00").compareTo(respuesta.precioInicial()));
        assertEquals(0, new BigDecimal("150.00").compareTo(respuesta.ofertaVigente()));
        assertEquals(0, new BigDecimal("300.00").compareTo(respuesta.precioCompraInmediata()));
        assertEquals(12, respuesta.cantidadPujas());
        assertTrue(respuesta.esMaestroDeJuego());
        assertEquals(subasta.getVendedorId().toString(), respuesta.vendedorId());
    }

    @Test
    void tipoProductoNuloNoRevientaYQuedaNuloEnLaRespuesta() {
        Subasta subasta = subastaCompleta();
        subasta.setTipoProducto(null);

        SubastaResumenResponse respuesta = SubastaResumenResponse.desde(subasta);

        assertNull(respuesta.tipoProducto());
    }

    @Test
    void precioCompraInmediataNuloSignificaSoloPujas() {
        Subasta subasta = subastaCompleta();
        subasta.setPrecioCompraInmediata(null);

        SubastaResumenResponse respuesta = SubastaResumenResponse.desde(subasta);

        assertNull(respuesta.precioCompraInmediata());
    }

    @Test
    void vendedorIdSeConvierteATextoParaNoAtarseAUnTipoTodavia() {
        // vendedorId como String es deliberado (ver Javadoc del record):
        // su tipo final depende de la decision pendiente sobre el
        // identificador estable en el JWT (discusion Andres/Edwin/Santiago).
        UUID vendedorId = UUID.randomUUID();
        Subasta subasta = subastaCompleta();
        subasta.setVendedorId(vendedorId);

        SubastaResumenResponse respuesta = SubastaResumenResponse.desde(subasta);

        assertEquals(vendedorId.toString(), respuesta.vendedorId());
    }
}
