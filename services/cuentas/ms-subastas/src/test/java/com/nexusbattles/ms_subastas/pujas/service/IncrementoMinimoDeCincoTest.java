package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D-43 (auditoria del 4-oct, cambio autorizado n.º 1): incremento minimo entre
 * pujas de 5 creditos, el que publica admin-parametros desde su migracion V5.
 *
 * <p>El incremento lo decide el servidor: cada subasta guarda el que regia al
 * publicarse y {@link MotorPujasService} lo exige en cada puja. Con una oferta
 * vigente de 100, la siguiente puja valida es 105: 104 se rechaza, 105 entra.
 * Y si dos jugadores ven 100 y pujan 105 a la vez, entra una sola: para la
 * otra la siguiente valida ya es 110.
 */
class IncrementoMinimoDeCincoTest {

    private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");
    private static final BigDecimal CINCO = new BigDecimal("5");

    private final CreditoClientFake creditos = new CreditoClientFake();
    private final MotorPujasService motor =
            new MotorPujasService(creditos, Clock.fixed(AHORA, ZoneOffset.UTC), new ParametrosPuja());

    /** Una subasta de precio minimo 100 con incremento 5 y una primera puja de 100 ya vigente. */
    private Subasta conCienVigente(AtomicReference<Puja> vigente) {
        Subasta subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"),
                CINCO, new BigDecimal("100000"), null, EstadoSubasta.ACTIVA, AHORA.plusSeconds(86_400), 0L);
        UUID primero = jugadorConSaldo();
        vigente.set(motor.pujar(subasta, null, primero, new BigDecimal("100"), ContextoParticipacion.sinHistorial(),
                UUID.randomUUID().toString(), TipoPuja.MANUAL));
        assertEquals(0, new BigDecimal("105").compareTo(subasta.pujaMinimaSiguiente()),
                "con 100 vigente e incremento 5, la siguiente valida es 105");
        return subasta;
    }

    private UUID jugadorConSaldo() {
        UUID jugador = UUID.randomUUID();
        creditos.acreditar(jugador, new BigDecimal("10000"));
        return jugador;
    }

    @Test
    @DisplayName("con 100 vigente, 104 se rechaza por oferta insuficiente y 105 entra")
    void cientoCuatroNoCientoCincoSi() {
        AtomicReference<Puja> vigente = new AtomicReference<>();
        Subasta subasta = conCienVigente(vigente);

        PujaRechazadaException rechazo = assertThrows(PujaRechazadaException.class,
                () -> motor.pujar(subasta, vigente.get(), jugadorConSaldo(), new BigDecimal("104"),
                        ContextoParticipacion.sinHistorial(), UUID.randomUUID().toString(), TipoPuja.MANUAL));
        Puja aceptada = motor.pujar(subasta, vigente.get(), jugadorConSaldo(), new BigDecimal("105"),
                ContextoParticipacion.sinHistorial(), UUID.randomUUID().toString(), TipoPuja.MANUAL);

        assertAll(
                () -> assertEquals(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE, rechazo.getMotivo()),
                () -> assertEquals(0, new BigDecimal("105").compareTo(aceptada.getMonto())),
                () -> assertEquals(0, new BigDecimal("105").compareTo(subasta.getOfertaVigente())),
                () -> assertEquals(0, new BigDecimal("110").compareTo(subasta.pujaMinimaSiguiente())));
    }

    @Test
    @DisplayName("dos jugadores ven 100 y pujan 105 a la vez: entra una, la otra se rechaza (ya hacía falta 110)")
    void dosPujanCientoCincoALaVez() throws InterruptedException {
        AtomicReference<Puja> vigente = new AtomicReference<>();
        Subasta subasta = conCienVigente(vigente);
        List<UUID> jugadores = List.of(jugadorConSaldo(), jugadorConSaldo());

        List<Puja> aceptadas = java.util.Collections.synchronizedList(new ArrayList<>());
        List<PujaRechazadaException> rechazadas = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(2);
        for (UUID jugador : jugadores) {
            hilos.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                    // Como en produccion dentro de la transaccion con
                    // SELECT ... FOR UPDATE: leer la vigente, validar y escribir.
                    synchronized (motor) {
                        Puja nueva = motor.pujar(subasta, vigente.get(), jugador, new BigDecimal("105"),
                                ContextoParticipacion.sinHistorial(), UUID.randomUUID().toString(), TipoPuja.MANUAL);
                        vigente.set(nueva);
                        aceptadas.add(nueva);
                    }
                } catch (PujaRechazadaException rechazo) {
                    rechazadas.add(rechazo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminados.countDown();
                }
            });
        }
        listos.await();
        salida.countDown();
        terminados.await();
        hilos.shutdown();

        assertAll(
                () -> assertEquals(1, aceptadas.size(), "entra una sola puja de 105"),
                () -> assertEquals(1, rechazadas.size(), "la otra se rechaza"),
                () -> assertEquals(PujaRechazadaException.Motivo.OFERTA_INSUFICIENTE, rechazadas.get(0).getMotivo()),
                () -> assertEquals(0, new BigDecimal("105").compareTo(subasta.getOfertaVigente())),
                () -> assertEquals(0, new BigDecimal("110").compareTo(subasta.pujaMinimaSiguiente())));
    }
}
