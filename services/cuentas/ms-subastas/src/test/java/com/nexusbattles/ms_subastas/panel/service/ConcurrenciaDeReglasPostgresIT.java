package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.notificaciones.AvisosDeSubasta;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;
import com.nexusbattles.ms_subastas.pujas.service.PujaApplicationService;
import com.nexusbattles.ms_subastas.pujas.service.PujaRechazadaException;
import com.nexusbattles.ms_subastas.reglas.FuenteDeReglas;
import com.nexusbattles.ms_subastas.soporte.FinanzasFalsa;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaRequest;
import com.nexusbattles.ms_subastas.subastas.model.DuracionSubasta;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.port.CatalogoProductosClient;
import com.nexusbattles.ms_subastas.subastas.port.FinanzasPublicacionClient;
import com.nexusbattles.ms_subastas.subastas.port.IdempotenciaPublicacion;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.SancionesClient;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.service.CalculadorComisionPublicacion;
import com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException;
import com.nexusbattles.ms_subastas.subastas.service.PublicarSubastaApplicationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Las reglas de 7.7 que solo se sostienen con candados, contra PostgreSQL de
 * verdad y con dos hilos a la vez:
 *
 * <ul>
 *   <li>una cancelacion y una puja simultaneas: gana una sola (7.7.10, «solo
 *       si no hay pujas registradas»);</li>
 *   <li>una compra inmediata y una puja que alcanza su precio: gana una sola;</li>
 *   <li>dos publicaciones del mismo vendedor con 9 activas: entra una, la otra
 *       choca con el limite de 10 (7.7.10), gracias al candado por vendedor.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "app.finanzas.modo=prueba",
        "app.pujas.emision-automatica-intervalo-ms=3600000",
        "app.subastas.cierre-intervalo-ms=3600000",
        "app.notificaciones.drenaje-intervalo-ms=3600000",
        "app.subastas.recordatorio-intervalo-ms=3600000",
        "app.subastas.pendientes-intervalo-ms=3600000",
        "app.pujas.intervalo-minimo-segundos=0"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Concurrencia de las reglas de 7.7 (PostgreSQL real)")
class ConcurrenciaDeReglasPostgresIT {

    private static final FinanzasFalsa FINANZAS = new FinanzasFalsa();

    @DynamicPropertySource
    static void finanzas(DynamicPropertyRegistry registro) {
        registro.add("app.finanzas.base-url", FINANZAS::base);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class CreditosDePrueba {
        @Bean
        CreditoClientFake creditoClientFake() {
            return new CreditoClientFake();
        }
    }

    @AfterAll
    static void apagar() {
        FINANZAS.close();
    }

    @Autowired
    private SubastaRepository subastas;

    @Autowired
    private PujaRepository pujasRepo;

    @Autowired
    private PujaApplicationService pujas;

    @Autowired
    private CancelacionService cancelaciones;

    @Autowired
    private CreditoClient creditos;

    @Autowired
    private InventarioClient inventarioFake;

    @Autowired
    private IdempotenciaPublicacion idempotencia;

    @Autowired
    private AvisosDeSubasta avisos;

    @Autowired
    private ApplicationEventPublisher eventos;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID jugadorConSaldo() {
        UUID jugador = UUID.randomUUID();
        ((CreditoClientFake) creditos).acreditar(jugador, new BigDecimal("10000"));
        return jugador;
    }

    private Subasta activa(UUID vendedor, BigDecimal compraInmediata) {
        Subasta subasta = new Subasta();
        subasta.setId(UUID.randomUUID());
        subasta.setProductoId(UUID.randomUUID());
        subasta.setElementoInventarioId("elem-" + subasta.getId());
        subasta.setVendedorId(vendedor);
        subasta.setPrecioInicial(new BigDecimal("100"));
        subasta.setOfertaVigente(new BigDecimal("100"));
        subasta.setIncrementoMinimo(new BigDecimal("10"));
        subasta.setPrecioCompraInmediata(compraInmediata);
        subasta.setEstado(EstadoSubasta.ACTIVA);
        subasta.setFechaFin(Instant.now().plus(Duration.ofDays(1)));
        subasta.setComisionCobrada(new BigDecimal("3"));
        inventarioFake.reservar(subasta.getElementoInventarioId(), vendedor, subasta.getId(), "pub-" + subasta.getId());
        return subastas.saveAndFlush(subasta);
    }

    /** Ejecuta las dos a la vez (salida comun) y devuelve el resultado o la excepcion de cada una. */
    private static Object[] aLaVez(Callable<Object> una, Callable<Object> otra) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch listos = new CountDownLatch(2);
            CountDownLatch salida = new CountDownLatch(1);
            Future<Object> primera = hilos.submit(() -> competir(una, listos, salida));
            Future<Object> segunda = hilos.submit(() -> competir(otra, listos, salida));
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            salida.countDown();
            return new Object[]{primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS)};
        } finally {
            hilos.shutdownNow();
        }
    }

    private static Object competir(Callable<Object> accion, CountDownLatch listos, CountDownLatch salida)
            throws InterruptedException {
        listos.countDown();
        salida.await();
        try {
            return accion.call();
        } catch (Exception | Error fallo) {
            return fallo;
        }
    }

    @RepeatedTest(8)
    @DisplayName("cancelar y pujar a la vez: o se cancela sin pujas, o hay puja y no se cancela")
    void cancelacionContraPuja() throws Exception {
        UUID vendedor = jugadorConSaldo();
        UUID postor = jugadorConSaldo();
        Subasta subasta = activa(vendedor, null);

        Object[] resultados = aLaVez(
                () -> cancelaciones.cancelar(subasta.getId(), vendedor),
                () -> pujas.pujar(subasta.getId(), postor, new BigDecimal("100"), "k-" + UUID.randomUUID()));

        Subasta final_ = subastas.findById(subasta.getId()).orElseThrow();
        boolean cancelo = !(resultados[0] instanceof Throwable);
        boolean pujo = !(resultados[1] instanceof Throwable);
        assertTrue(cancelo ^ pujo, "exactamente una gana: " + resultados[0] + " / " + resultados[1]);
        if (cancelo) {
            assertEquals(EstadoSubasta.CANCELADA, final_.getEstado());
            assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    ((PujaRechazadaException) resultados[1]).getMotivo());
            assertEquals(0, pujasRepo.countByJugadorIdAndEstado(postor, EstadoPuja.ACTIVA));
            assertEquals(0, new BigDecimal("10000").compareTo(creditos.saldoDisponible(postor)),
                    "al postor que perdio la carrera no le queda nada retenido");
        } else {
            assertEquals(EstadoSubasta.ACTIVA, final_.getEstado());
            assertEquals(OperacionRechazadaException.Motivo.CANCELACION_CON_PUJAS,
                    ((OperacionRechazadaException) resultados[0]).getMotivo());
            assertEquals(1, final_.getCantidadPujas());
        }
    }

    @RepeatedTest(8)
    @DisplayName("comprar ya y pujar el mismo precio a la vez: una sola se queda el producto")
    void compraInmediataContraPuja() throws Exception {
        UUID comprador = jugadorConSaldo();
        UUID postor = jugadorConSaldo();
        Subasta subasta = activa(UUID.randomUUID(), new BigDecimal("500"));

        Object[] resultados = aLaVez(
                () -> pujas.comprarAhora(subasta.getId(), comprador, "c-" + UUID.randomUUID()),
                () -> pujas.pujar(subasta.getId(), postor, new BigDecimal("500"), "p-" + UUID.randomUUID()));

        boolean compro = !(resultados[0] instanceof Throwable);
        boolean pujo = !(resultados[1] instanceof Throwable);
        assertTrue(compro ^ pujo, "exactamente una gana: " + resultados[0] + " / " + resultados[1]);
        Subasta final_ = subastas.findById(subasta.getId()).orElseThrow();
        if (compro) {
            assertEquals(EstadoSubasta.ADJUDICADA, final_.getEstado());
            assertEquals(PujaRechazadaException.Motivo.SUBASTA_NO_ACTIVA,
                    ((PujaRechazadaException) resultados[1]).getMotivo());
            assertEquals(0, new BigDecimal("10000").compareTo(creditos.saldoDisponible(postor)));
        } else {
            // La puja alcanzo el precio: comprar por debajo le quitaria el producto al mejor postor.
            assertEquals(EstadoSubasta.ACTIVA, final_.getEstado());
            assertEquals(PujaRechazadaException.Motivo.COMPRA_INMEDIATA_SUPERADA,
                    ((PujaRechazadaException) resultados[0]).getMotivo());
            assertEquals(0, new BigDecimal("10000").compareTo(creditos.saldoDisponible(comprador)));
        }
    }

    @Test
    @DisplayName("dos publicaciones con 9 activas: entra una y la otra choca con el limite de 10")
    void limiteDePublicacionesConDosALaVez() throws Exception {
        UUID vendedor = UUID.randomUUID();
        for (int i = 0; i < 9; i++) {
            activa(vendedor, null);
        }
        UUID producto = UUID.randomUUID();
        InventarioClient inventario = mock(InventarioClient.class);
        when(inventario.buscar(any())).thenAnswer(i -> Optional.of(new InventarioClient.ElementoInventario(
                i.getArgument(0), producto, vendedor, false)));
        CountDownLatch otroEsperando = new CountDownLatch(1);
        // El primero en entrar se detiene con el candado tomado hasta ver al otro
        // esperandolo en PostgreSQL (pg_locks), sin esperas a ojo.
        doAnswer(i -> {
            if (otroEsperando.getCount() > 0) {
                esperarAQueAlguienEspereElCandado();
                otroEsperando.countDown();
            }
            return null;
        }).when(inventario).reservar(any(), any(), any(), any());
        CatalogoProductosClient catalogo = mock(CatalogoProductosClient.class);
        when(catalogo.buscar(producto)).thenReturn(Optional.of(new CatalogoProductosClient.Producto(
                producto, "Espada", null, null, null, null, null, true)));
        SancionesClient sanciones = mock(SancionesClient.class);
        PublicarSubastaApplicationService servicio = new PublicarSubastaApplicationService(subastas, inventario,
                catalogo, mock(FinanzasPublicacionClient.class), () -> new IdentidadClient.Identidad(vendedor, false),
                sanciones, idempotencia, new CalculadorComisionPublicacion(), Clock.systemUTC(),
                FuenteDeReglas.fijas(new ParametrosPuja(), BigDecimal.ONE), avisos, eventos);
        TransactionTemplate tx = new TransactionTemplate(transacciones);

        Object[] resultados = aLaVez(
                () -> tx.execute(s -> servicio.publicar(new PublicarSubastaRequest("u-1", producto,
                        DuracionSubasta.H24, BigDecimal.TEN, null), "clave-1")),
                () -> tx.execute(s -> servicio.publicar(new PublicarSubastaRequest("u-2", producto,
                        DuracionSubasta.H24, BigDecimal.TEN, null), "clave-2")));

        List<Object> fallos = java.util.Arrays.stream(resultados).filter(r -> r instanceof Throwable).toList();
        assertEquals(1, fallos.size(), "una entra y otra no: " + java.util.Arrays.toString(resultados));
        PublicacionSubastaException rechazo = assertInstanceOf(PublicacionSubastaException.class, fallos.getFirst());
        assertEquals(PublicacionSubastaException.LIMITE_PUBLICACIONES_ACTIVAS, rechazo.getCodigo());
        assertEquals(10, subastas.countByVendedorIdAndEstado(vendedor, EstadoSubasta.ACTIVA));
    }

    private void esperarAQueAlguienEspereElCandado() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            Integer esperando = jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype = 'advisory' and not granted", Integer.class);
            if (esperando != null && esperando > 0) {
                return;
            }
            Thread.sleep(20);
        }
        // Sin candado nadie espera: se sigue, y la prueba falla por la cuenta, no por un cuelgue.
    }
}
