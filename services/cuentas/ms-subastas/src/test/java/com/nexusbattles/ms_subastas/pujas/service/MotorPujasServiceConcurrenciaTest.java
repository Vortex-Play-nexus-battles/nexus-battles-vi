package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Caso de prueba pedido por el Sprint Backlog para HU-SUB-004: "dos
 * jugadores pujando en la misma fraccion de segundo, verificar que solo una
 * puja gana la carrera y la otra se rechaza limpiamente".
 *
 * El bloque "leer puja vigente -> validar -> escribir nueva puja vigente" se
 * sincroniza sobre la misma instancia de MotorPujasService, igual que en
 * produccion ese bloque viviria dentro de una transaccion con
 * SELECT ... FOR UPDATE sobre la fila de Subasta. Cuando exista el
 * repositorio JPA real (Dia 1, tabla de Edwin), este test debe repetirse
 * contra Testcontainers para probar el lock pesimista real entre procesos.
 */
class MotorPujasServiceConcurrenciaTest {
    /** Cada puja de prueba usa su propia clave, como la enviaria un cliente distinto. */
    private static String claveUnica() {
        return UUID.randomUUID().toString();
    }


    @Test
    void soloUnaPujaGanaLaCarreraCuandoVariosJugadoresPujanAlMismoTiempo() throws InterruptedException {
        Instant ahora = Instant.parse("2026-09-11T12:00:00Z");
        CreditoClientFake creditoClient = new CreditoClientFake();
        ParametrosPuja parametros = new ParametrosPuja();
        Clock clock = Clock.fixed(ahora, ZoneOffset.UTC);
        MotorPujasService motor = new MotorPujasService(creditoClient, clock, parametros);

        UUID vendedor = UUID.randomUUID();
        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), vendedor, new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("100000"), null, EstadoSubasta.ACTIVA, ahora.plusSeconds(86400), 0L);

        int numeroDeJugadores = 20;
        List<UUID> jugadores = IntStream.range(0, numeroDeJugadores)
                .mapToObj(i -> UUID.randomUUID())
                .collect(Collectors.toList());
        jugadores.forEach(j -> creditoClient.acreditar(j, new BigDecimal("10000")));

        Map<UUID, Puja> pujasAceptadas = new ConcurrentHashMap<>();
        AtomicReference<Puja> pujaVigente = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(numeroDeJugadores);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch listos = new CountDownLatch(numeroDeJugadores);
        CountDownLatch terminados = new CountDownLatch(numeroDeJugadores);

        for (int i = 0; i < numeroDeJugadores; i++) {
            UUID jugador = jugadores.get(i);
            // Todos ofrecen un monto razonablemente alto: la carrera la decide
            // el orden de ejecucion de los hilos, no quien tecleo el numero mas grande.
            BigDecimal monto = new BigDecimal("100").add(new BigDecimal((i + 1) * 20));
            executor.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                    synchronized (motor) {
                        Puja actual = pujaVigente.get();
                        Puja nueva = motor.pujar(subasta, actual, jugador, monto, ContextoParticipacion.sinHistorial(), claveUnica());
                        pujaVigente.set(nueva);
                        pujasAceptadas.put(jugador, nueva);
                    }
                } catch (PujaRechazadaException ignorada) {
                    // rechazo limpio: perdio la carrera porque alguien ya subio
                    // la oferta vigente por encima de su monto. Es el resultado
                    // esperado para la mayoria de los hilos.
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminados.countDown();
                }
            });
        }

        listos.await();
        salida.countDown();
        terminados.await();
        executor.shutdown();

        long ganadoras = pujasAceptadas.values().stream().filter(p -> p.getEstado() == EstadoPuja.ACTIVA).count();
        assertEquals(1, ganadoras, "solo una puja debe quedar como oferta vigente al final de la carrera");

        UUID ganador = subasta.getMejorPostorId();
        assertEquals(subasta.getOfertaVigente(), pujasAceptadas.get(ganador).getMonto());

        for (UUID jugador : jugadores) {
            if (!jugador.equals(ganador) && pujasAceptadas.containsKey(jugador)) {
                // Fue aceptado en su momento pero luego lo superaron: su reserva debe quedar liberada.
                assertEquals(new BigDecimal("10000"), creditoClient.saldoDisponible(jugador),
                        "el jugador " + jugador + " fue superado y debe tener su saldo completo liberado");
            }
        }
    }
}
