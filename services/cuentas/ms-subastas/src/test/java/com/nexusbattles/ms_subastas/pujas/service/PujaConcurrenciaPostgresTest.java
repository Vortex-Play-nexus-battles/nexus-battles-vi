package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integracion de la concurrencia de HU-SUB-004 contra PostgreSQL
 * real. Complementa a MotorPujasServiceConcurrenciaTest, que corre en memoria
 * y por tanto NO puede ejercitar las dos garantias que de verdad sostienen la
 * historia en produccion:
 *
 *   1. El lock pesimista (SELECT ... FOR UPDATE) que serializa las pujas sobre
 *      la misma subasta entre transacciones distintas.
 *   2. El indice unico parcial uq_pujas_una_vigente_por_subasta, que impide dos
 *      pujas ACTIVA sobre la misma subasta aunque la validacion en Java fallara.
 *
 * Se salta automaticamente si no hay Docker (disabledWithoutDocker), para que
 * `./mvnw verify` siga funcionando en una maquina sin Docker. En CI
 * (ubuntu-latest) si hay, asi que alli se ejecuta.
 *
 * Se llama *Test y no *IT a proposito: surefire solo recoge *Test / Test* /
 * *Tests, asi que un nombre terminado en IT no se ejecutaria sin configurar
 * failsafe. Mismo criterio que las pruebas con Testcontainers de ms-cumplimiento.
 */
@SpringBootTest(properties = {
        // Desactiva el bean de produccion del doble para poder inyectar uno al
        // que se le pueda acreditar saldo desde la prueba.
        "app.finanzas.modo=test",
        "app.pujas.intervalo-minimo-segundos=0"
})
@Testcontainers(disabledWithoutDocker = true)
class PujaConcurrenciaPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class DoblesDePrueba {
        // El bean de Clock ya lo provee MsSubastasApplication (systemUTC);
        // redefinirlo aqui rompe el contexto por definicion duplicada.
        @Bean
        CreditoClientFake creditoClient() {
            return new CreditoClientFake();
        }
    }

    @Autowired
    private PujaApplicationService servicio;

    @Autowired
    private SubastaRepository subastaRepository;

    @Autowired
    private PujaRepository pujaRepository;

    @Autowired
    private CreditoClientFake creditoClient;

    @Autowired
    private CreditoClient creditoClientInyectado;

    private Subasta subasta;

    @BeforeEach
    void sembrarSubasta() {
        pujaRepository.deleteAll();
        subastaRepository.deleteAll();

        Subasta nueva = new Subasta(null, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal("10.00"), new BigDecimal("100000.00"), null, EstadoSubasta.ACTIVA,
                Instant.now().plusSeconds(86400), 0L);
        subasta = subastaRepository.save(nueva);
    }

    @Test
    void elContextoDeSpringArranca() {
        assertNotNull(servicio, "si este bean falta, la aplicacion no arranca en ningun entorno");
        assertNotNull(creditoClientInyectado);
    }

    @Test
    void elLockPesimistaDejaUnaSolaPujaVigenteConVariasTransaccionesEnParalelo() throws InterruptedException {
        int numeroDeJugadores = 12;
        List<UUID> jugadores = IntStream.range(0, numeroDeJugadores)
                .mapToObj(i -> UUID.randomUUID())
                .collect(Collectors.toList());
        jugadores.forEach(j -> creditoClient.acreditar(j, new BigDecimal("100000.00")));

        ExecutorService executor = Executors.newFixedThreadPool(numeroDeJugadores);
        CountDownLatch listos = new CountDownLatch(numeroDeJugadores);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(numeroDeJugadores);

        // Los fallos inesperados se recogen en vez de perderse: un executor se
        // traga cualquier excepcion que no se mire, y entonces "0 pujas
        // vigentes" no distingue entre rechazo limpio y error de verdad.
        List<Throwable> fallosInesperados = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numeroDeJugadores; i++) {
            UUID jugador = jugadores.get(i);
            BigDecimal monto = new BigDecimal("100.00").add(new BigDecimal((i + 1) * 50));
            executor.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                    servicio.pujar(subasta.getId(), jugador, monto, UUID.randomUUID().toString());
                } catch (PujaRechazadaException ignorada) {
                    // Perdio la carrera limpiamente: cuando le toco el lock, la
                    // oferta vigente ya superaba su monto.
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (Throwable inesperado) {
                    fallosInesperados.add(inesperado);
                } finally {
                    terminados.countDown();
                }
            });
        }

        listos.await();
        salida.countDown();
        assertTrue(terminados.await(60, TimeUnit.SECONDS), "las pujas no terminaron a tiempo");
        executor.shutdown();

        assertTrue(fallosInesperados.isEmpty(),
                () -> "ninguna puja debio fallar por algo distinto a perder la carrera, pero hubo "
                        + fallosInesperados.size() + ": " + fallosInesperados);

        List<Puja> activas = pujaRepository.findAll().stream()
                .filter(p -> p.getEstado() == EstadoPuja.ACTIVA)
                .toList();
        assertEquals(1, activas.size(), "la base de datos debe quedar con exactamente una puja vigente");

        Subasta recargada = subastaRepository.findById(subasta.getId()).orElseThrow();
        Puja ganadora = activas.get(0);
        assertEquals(0, recargada.getOfertaVigente().compareTo(ganadora.getMonto()),
                "la oferta vigente de la subasta debe coincidir con la puja ganadora");
        assertEquals(ganadora.getJugadorId(), recargada.getMejorPostorId());

        for (UUID jugador : jugadores) {
            if (!jugador.equals(ganadora.getJugadorId())) {
                assertEquals(0, new BigDecimal("100000.00").compareTo(creditoClient.saldoDisponible(jugador)),
                        "el jugador " + jugador + " no gano: no puede quedarse con creditos reservados");
            }
        }
    }

    @Test
    void elIndiceUnicoParcialImpideDosPujasVigentesSobreLaMismaSubasta() {
        Puja primera = new Puja(null, subasta.getId(), UUID.randomUUID(), new BigDecimal("110.00"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, Instant.now(), UUID.randomUUID().toString());
        pujaRepository.saveAndFlush(primera);

        Puja segunda = new Puja(null, subasta.getId(), UUID.randomUUID(), new BigDecimal("120.00"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, Instant.now(), UUID.randomUUID().toString());

        assertThrows(DataIntegrityViolationException.class, () -> pujaRepository.saveAndFlush(segunda),
                "uq_pujas_una_vigente_por_subasta debe rechazar la segunda puja ACTIVA");
    }

    @Test
    void variasPujasSuperadasSiPuedenConvivirEnLaMismaSubasta() {
        Puja superada = new Puja(null, subasta.getId(), UUID.randomUUID(), new BigDecimal("110.00"),
                TipoPuja.MANUAL, EstadoPuja.SUPERADA, Instant.now(), UUID.randomUUID().toString());
        Puja otraSuperada = new Puja(null, subasta.getId(), UUID.randomUUID(), new BigDecimal("120.00"),
                TipoPuja.MANUAL, EstadoPuja.SUPERADA, Instant.now(), UUID.randomUUID().toString());

        pujaRepository.saveAndFlush(superada);
        pujaRepository.saveAndFlush(otraSuperada);

        assertEquals(2, pujaRepository.findAll().size(),
                "el indice es parcial: solo restringe las ACTIVA, el historial debe poder crecer");
    }
}
