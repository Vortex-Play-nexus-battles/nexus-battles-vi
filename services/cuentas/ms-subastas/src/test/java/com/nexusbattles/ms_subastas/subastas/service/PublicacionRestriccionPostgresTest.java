package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaRequest;
import com.nexusbattles.ms_subastas.subastas.model.*;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Verifica la excepcion real que genera PostgreSQL/Hibernate con V4 sin analizar mensajes. */
@SpringBootTest(properties = {
        "app.pujas.emision-automatica-intervalo-ms=3600000",
        "app.subastas.cierre-intervalo-ms=3600000",
        "app.notificaciones.drenaje-intervalo-ms=3600000"
})
@Testcontainers
class PublicacionRestriccionPostgresTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired SubastaRepository repo;

    @Test
    void nombreEstructuradoDelIndiceRealSeTraduceAConflicto() {
        String unidad = UUID.randomUUID().toString();
        repo.saveAndFlush(subasta(unidad, BigDecimal.ONE));
        var original = assertThrows(DataIntegrityViolationException.class,
                () -> repo.saveAndFlush(subasta(unidad, BigDecimal.ONE)));
        Throwable causa = original;
        while (!(causa instanceof org.hibernate.exception.ConstraintViolationException) && causa.getCause() != null) {
            causa = causa.getCause();
        }
        var hibernate = assertInstanceOf(org.hibernate.exception.ConstraintViolationException.class, causa);
        assertEquals("uq_subastas_elemento_inventario_activa", hibernate.getConstraintName());
        var traducida = assertThrows(PublicacionSubastaException.class, () -> publicarConFallo(original));
        assertEquals(PublicacionSubastaException.Motivo.CONFLICTO, traducida.getMotivo());
        assertSame(original, traducida.getCause());
    }

    @Test
    void otraRestriccionRealSigueSiendoErrorTecnico() {
        var original = assertThrows(DataIntegrityViolationException.class,
                () -> repo.saveAndFlush(subasta(UUID.randomUUID().toString(), BigDecimal.ZERO)));
        assertSame(original, assertThrows(DataIntegrityViolationException.class, () -> publicarConFallo(original)));
    }

    private void publicarConFallo(DataIntegrityViolationException original) {
        var repositorio = mock(SubastaRepository.class);
        when(repositorio.saveAndFlush(any())).thenThrow(original);
        var inventario = mock(InventarioClient.class);
        var catalogo = mock(CatalogoProductosClient.class);
        UUID jugador = UUID.randomUUID(), producto = UUID.randomUUID();
        when(inventario.buscar("unidad")).thenReturn(Optional.of(new InventarioClient.ElementoInventario("unidad", producto, jugador, false)));
        when(catalogo.buscar(producto)).thenReturn(Optional.of(new CatalogoProductosClient.Producto(producto, "Espada", null, null, null, null, null, true)));
        var servicio = new PublicarSubastaApplicationService(repositorio, inventario, catalogo,
                mock(FinanzasPublicacionClient.class), () -> new IdentidadClient.Identidad(jugador, false),
                mock(SancionesClient.class), new IdempotenciaPublicacionEnMemoria(), new CalculadorComisionPublicacion(), Clock.systemUTC(), "1");
        servicio.publicar(new PublicarSubastaRequest("unidad", producto, DuracionSubasta.H24, BigDecimal.TEN, null), "k");
    }

    private Subasta subasta(String unidad, BigDecimal incremento) {
        // El id lo asigna la aplicacion desde R10: la entidad dejo de declarar
        // @GeneratedValue, porque publicar necesita el identificador ANTES de
        // guardar para reservar el elemento en inventario con el.
        var s = new Subasta(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, incremento,
                null, null, EstadoSubasta.ACTIVA, Instant.now().plusSeconds(86400), 0L);
        s.setElementoInventarioId(unidad);
        return s;
    }
}
