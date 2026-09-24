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

    /**
     * El defecto de R10, en una prueba que no necesita base de datos.
     *
     * <p>Spring Data decide entre {@code persist} y {@code merge} preguntandole
     * a la entidad si es nueva. Mientras {@code Subasta} no respondia,
     * {@code JpaMetamodelEntityInformation} miraba el id, lo encontraba puesto
     * —porque lo asigna la aplicacion, que necesita conocerlo antes de guardar
     * para reservar el elemento en inventario— y concluia que la fila ya
     * existia. El {@code merge} sobre una fila inexistente terminaba en
     * {@code StaleObjectStateException}, y publicar una subasta devolvia 500
     * <b>siempre</b> contra Postgres. Ninguna prueba lo veia porque las de
     * publicacion usan un repositorio doble.
     */
    @Test
    void unaSubastaRecienCreadaEsNuevaAunqueYaTengaId() {
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());

        assertNotNull(subasta.getId(), "el id lo pone la aplicacion, no la base");
        assertTrue(subasta.isNew(), "con id puesto y sin esto, Spring Data mandaria merge");
    }

    @Test
    void dejaDeSerNuevaCuandoYaEstaEnLaBase() {
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());

        // Lo que Hibernate invoca al cargarla o al insertarla (@PostLoad /
        // @PostPersist). Asi una entidad ya persistida se actualiza con merge,
        // que es lo correcto para ella.
        subasta.yaEstaEnLaBase();

        assertFalse(subasta.isNew());
    }

    /**
     * El constructor historico de diez parametros tambien produce entidades
     * nuevas: lo usan las pruebas que siembran subastas, y si devolviera
     * «no es nueva» volverian al merge sobre una fila que no existe.
     */
    @Test
    void elConstructorHistoricoTambienProduceEntidadesNuevas() {
        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, BigDecimal.ONE, null, null,
                EstadoSubasta.ACTIVA, Instant.now().plusSeconds(3600), 0L);

        assertTrue(subasta.isNew());
    }
}
