package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.*;
import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Dos hilos y ciclo transaccional Spring real, sin sleeps ni callbacks invocados a mano. */
class PublicacionConcurrenteTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), producto = UUID.randomUUID();
    private final ThreadLocal<UUID> usuario = ThreadLocal.withInitial(() -> a);
    private final SubastaRepository repo = mock(SubastaRepository.class);
    private final InventarioClient inventario = mock(InventarioClient.class);
    private final FinanzasPublicacionClient finanzas = mock(FinanzasPublicacionClient.class);
    private final CatalogoProductosClient catalogo = mock(CatalogoProductosClient.class);
    private final SancionesClient sanciones = mock(SancionesClient.class);
    private final IdempotenciaPublicacionEnMemoria claves = new IdempotenciaPublicacionEnMemoria();
    private final Gestor gestor = new Gestor();
    private final TransactionTemplate tx = new TransactionTemplate(gestor);
    private PublicarSubastaApplicationService servicio;

    @BeforeEach
    void preparar() {
        servicio = new PublicarSubastaApplicationService(repo, inventario, catalogo, finanzas,
                () -> new IdentidadClient.Identidad(usuario.get(), false), sanciones, claves,
                new CalculadorComisionPublicacion(), Clock.systemUTC(), "1");
        when(inventario.buscar(any())).thenAnswer(i -> Optional.of(new InventarioClient.ElementoInventario(
                i.getArgument(0), producto, "b".equals(i.getArgument(0)) ? b : a, false)));
        when(catalogo.buscar(producto)).thenReturn(Optional.of(new CatalogoProductosClient.Producto(
                producto, "Espada", null, null, null, null, null, true)));
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mismaClaveConcurrenteSoloEjecutaUnaPublicacion(boolean diferenteRequest) throws Exception {
        var entro = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        when(repo.saveAndFlush(any())).thenAnswer(i -> { entro.countDown(); esperar(continuar); return i.getArgument(0); });
        try (var hilos = Executors.newFixedThreadPool(2)) {
            try {
                var primera = hilos.submit(() -> publicar(a, "a"));
                esperar(entro);
                var segunda = hilos.submit(() -> assertThrows(PublicacionSubastaException.class,
                        () -> publicar(a, diferenteRequest ? "otra-unidad" : "a")));
                assertEquals(PublicacionSubastaException.Motivo.CONFLICTO, segunda.get(5, TimeUnit.SECONDS).getMotivo());
                verify(inventario, times(1)).reservar(any(), any(), any(), any());
                verify(finanzas, times(1)).debitarComision(any(), any(), any(), any());
                verify(repo, times(1)).saveAndFlush(any());
                assertTrue(claves.buscar(a + ":k").isEmpty());
                continuar.countDown();
                var resultado = primera.get(5, TimeUnit.SECONDS);
                assertEquals(resultado, publicar(a, "a"));
                verify(repo, times(1)).saveAndFlush(any());
            } finally { continuar.countDown(); }
        }
    }

    @Test
    void usuariosDistintosMismaClavePublicanConcurrentemente() throws Exception {
        var ambas = new CountDownLatch(2);
        when(repo.saveAndFlush(any())).thenAnswer(i -> { ambas.countDown(); esperar(ambas); return i.getArgument(0); });
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var primera = hilos.submit(() -> publicar(a, "a"));
            var segunda = hilos.submit(() -> publicar(b, "b"));
            var ra = primera.get(5, TimeUnit.SECONDS);
            var rb = segunda.get(5, TimeUnit.SECONDS);
            assertEquals(a, ra.vendedorId());
            assertEquals(b, rb.vendedorId());
            assertNotEquals(ra.id(), rb.id());
            assertEquals(ra, publicar(a, "a"));
            assertEquals(rb, publicar(b, "b"));
            verify(repo, times(2)).saveAndFlush(any());
            verify(inventario, times(2)).reservar(any(), any(), any(), any());
            verify(finanzas, times(2)).debitarComision(any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falloORollbackPermiteReintentarTrasCompetir(boolean falloEnCommit) throws Exception {
        var entro = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        var primeraVez = new AtomicBoolean(true);
        gestor.fallarCommit.set(falloEnCommit);
        when(repo.saveAndFlush(any())).thenAnswer(i -> {
            if (primeraVez.getAndSet(false)) {
                entro.countDown();
                esperar(continuar);
                if (!falloEnCommit) throw new IllegalStateException("fallo despues de debito");
            }
            return i.getArgument(0);
        });
        try (var hilos = Executors.newFixedThreadPool(2)) {
            try {
                var primera = hilos.submit(() -> assertThrows(RuntimeException.class, () -> publicar(a, "a")));
                esperar(entro);
                var segunda = hilos.submit(() -> assertThrows(PublicacionSubastaException.class, () -> publicar(a, "a")));
                assertEquals(PublicacionSubastaException.Motivo.CONFLICTO, segunda.get(5, TimeUnit.SECONDS).getMotivo());
                continuar.countDown();
                primera.get(5, TimeUnit.SECONDS);
                assertTrue(claves.buscar(a + ":k").isEmpty());
                assertNotNull(publicar(a, "a"));
                verify(repo, times(2)).saveAndFlush(any());
                verify(finanzas, times(1)).compensarDebito(any(), any(), any(), any());
                verify(inventario, times(1)).liberarReserva(any(), any(), any());
            } finally { continuar.countDown(); }
        }
    }

    @Test
    void falloAntesDeReservarLiberaClaveSinCompensar() {
        when(sanciones.tieneSancionActiva(a)).thenThrow(new IllegalStateException("HTTP")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> publicar(a, "a"));
        verifyNoInteractions(inventario, finanzas);
        assertNotNull(publicar(a, "a"));
    }

    @Test
    void compensacionesFallidasNoOcultanFalloYNoRetienenClave() {
        var original = new IllegalStateException("persistencia");
        when(repo.saveAndFlush(any())).thenThrow(original).thenAnswer(i -> i.getArgument(0));
        doThrow(new IllegalStateException("compensacion")).when(finanzas).compensarDebito(any(), any(), any(), any());
        doThrow(new IllegalStateException("liberacion")).when(inventario).liberarReserva(any(), any(), any());
        assertSame(original, assertThrows(IllegalStateException.class, () -> publicar(a, "a")));
        verify(inventario).liberarReserva(any(), any(), any());
        assertNotNull(publicar(a, "a"));
    }

    private PublicarSubastaResponse publicar(UUID jugador, String unidad) {
        usuario.set(jugador);
        try {
            return tx.execute(status -> servicio.publicar(new PublicarSubastaRequest(
                    unidad, producto, DuracionSubasta.H24, BigDecimal.TEN, null), "k"));
        } finally { usuario.remove(); }
    }

    private static void esperar(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "La prueba no debe bloquearse");
    }

    private static class Gestor extends AbstractPlatformTransactionManager {
        final AtomicBoolean fallarCommit = new AtomicBoolean();
        Gestor() { setRollbackOnCommitFailure(true); }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (fallarCommit.getAndSet(false)) throw new TransactionSystemException("fallo al confirmar");
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
