package com.nexusbattles.ms_subastas.subastas.model;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubastaTest {

    @Test
    void elementoInventarioEsUnStringIndependienteDelProducto() {
        UUID productoId = UUID.randomUUID();
        Subasta subasta = new Subasta();
        subasta.setProductoId(productoId);
        subasta.setElementoInventarioId("inventario/unidad-001");

        assertEquals("inventario/unidad-001", subasta.getElementoInventarioId());
        assertEquals(productoId, subasta.getProductoId());

        subasta.setProductoId(UUID.randomUUID());
        assertEquals("inventario/unidad-001", subasta.getElementoInventarioId());
    }

    @Test
    void mapeoExigeElementoInventarioSinImponerUnicidadGlobal() throws NoSuchFieldException {
        var campo = Subasta.class.getDeclaredField("elementoInventarioId");
        Column columna = campo.getAnnotation(Column.class);

        assertEquals(String.class, campo.getType());
        assertNotNull(columna);
        assertFalse(columna.nullable());
        // La unicidad por estado pertenece al indice parcial de PostgreSQL.
        assertFalse(columna.unique());
    }

    @Test
    void constructorHistoricoPermiteCompletarLaReferenciaSinCambiarElProducto() {
        UUID productoId = UUID.randomUUID();
        UUID vendedorId = UUID.randomUUID();
        Instant fechaFin = Instant.parse("2026-09-15T12:00:00Z");
        Subasta subasta = new Subasta(null, productoId, vendedorId,
            BigDecimal.TEN, BigDecimal.ONE, null, null, EstadoSubasta.ACTIVA, fechaFin, 0L);

        assertNull(subasta.getElementoInventarioId());
        subasta.setElementoInventarioId("unidad-002");

        assertEquals("unidad-002", subasta.getElementoInventarioId());
        assertEquals(productoId, subasta.getProductoId());
        assertEquals(fechaFin, subasta.getFechaFin());
        assertTrue(subasta.estaActiva());
        assertTrue(subasta.esVendedor(vendedorId));
    }
}
