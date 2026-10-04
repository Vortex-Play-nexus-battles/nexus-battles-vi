package com.nexusbattles.ms_finanzas.creditos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.ms_finanzas.common.exception.SaldoInsuficienteException;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.AcreditarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.DebitarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.DebitarResponse;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReversarRequest;
import com.nexusbattles.ms_finanzas.creditos.dto.CreditoDTOs.ReversarResponse;
import com.nexusbattles.ms_finanzas.partidas.InventarioDeCofres;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * G7 (continuación técnica del 4-oct, FASE 10) — la sanidad del libro de
 * créditos contra PostgreSQL real: lo que creditos.yaml 1.4.1 promete de
 * {@code debitar} y {@code reversar} (idempotencia por {@code refId}) y lo que
 * la base garantiza ({@code UNIQUE idempotency_key}, {@code CHECK saldo_bruto
 * >= 0}, cuenta tomada con {@code PESSIMISTIC_WRITE}). Sin cambiar
 * comportamiento: estas pruebas fijan el que el contrato define.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Libro de créditos: debitar y reversar sin cobros dobles ni saldos negativos (G7)")
class LibroDeCreditosIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CreditoService libro;

    /** Otro servicio; aquí no se llega a él, pero el contexto lo pide. */
    @MockitoBean
    private InventarioDeCofres inventario;

    private String jugadorCon(int creditos) {
        String uid = UUID.randomUUID().toString();
        libro.acreditar(new AcreditarRequest(uid, BigDecimal.valueOf(creditos), "alta-" + uid, "bienvenida"));
        return uid;
    }

    private BigDecimal saldo(String uid) {
        return libro.obtenerSaldo(uid).saldoDisponible();
    }

    private DebitarResponse debitar(String uid, int monto, String refId) {
        return libro.debitar(new DebitarRequest(uid, BigDecimal.valueOf(monto), refId, "compra-prueba"));
    }

    @Test
    @DisplayName("el mismo refId dos veces descuenta una sola vez y responde el mismo transaccionId")
    void sinDobleDebito() {
        String uid = jugadorCon(100);

        DebitarResponse primero = debitar(uid, 30, "orden-1");
        DebitarResponse repetido = debitar(uid, 30, "orden-1");

        assertThat(saldo(uid)).isEqualByComparingTo("70");
        assertThat(repetido.transaccionId()).isEqualTo(primero.transaccionId());
    }

    @Test
    @DisplayName("los reintentos no dejan saldo negativo: sin saldo es 422 y nada cambia")
    void sinSaldoNegativoPorReintentos() {
        String uid = jugadorCon(100);
        debitar(uid, 80, "orden-a");
        for (int i = 0; i < 3; i++) {
            debitar(uid, 80, "orden-a");
        }

        assertThatThrownBy(() -> debitar(uid, 80, "orden-b")).isInstanceOf(SaldoInsuficienteException.class);
        assertThatThrownBy(() -> debitar(uid, 80, "orden-b")).isInstanceOf(SaldoInsuficienteException.class);
        assertThat(saldo(uid)).isEqualByComparingTo("20");
        // El rechazado no dejó fila: un refId sin cobro sigue libre.
        assertThatThrownBy(() -> libro.consultarOperacionPorRefId("orden-b"))
                .isInstanceOf(com.nexusbattles.ms_finanzas.common.exception.ReservaNoEncontradaException.class);
    }

    @Test
    @DisplayName("reversado, el mismo refId ya no vuelve a cobrar (creditos.yaml: la respuesta del primero)")
    void sinReaplicacionTrasReverso() {
        String uid = jugadorCon(100);
        DebitarResponse cobro = debitar(uid, 40, "orden-x");
        ReversarResponse devolucion = libro.reversar(new ReversarRequest("orden-x", "compra anulada"));
        assertThat(devolucion.estado()).isEqualTo("REVERSADO");
        assertThat(saldo(uid)).isEqualByComparingTo("100");

        // Debitar con el refId reversado: convergencia que el contrato define —
        // «si ya existe una operacion registrada con ese refId, no se vuelve a
        // descontar»—. Quien quiera cobrar otra vez usa otro refId.
        DebitarResponse otraVez = debitar(uid, 40, "orden-x");

        assertThat(saldo(uid)).isEqualByComparingTo("100");
        assertThat(otraVez.transaccionId()).isEqualTo(cobro.transaccionId());
        assertThat(libro.consultarOperacionPorRefId("orden-x").estado()).isEqualTo("LIBERADA");

        // Y reversar dos veces no devuelve dos veces.
        assertThat(libro.reversar(new ReversarRequest("orden-x", "otra vez")).estado()).isEqualTo("YA_REVERSADO");
        assertThat(saldo(uid)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("concurrencia: ocho a la vez con el mismo refId cobran una sola vez")
    void mismoRefIdConcurrente() throws Exception {
        String uid = jugadorCon(100);

        List<Boolean> resultados = aLaVez(8, i -> debitar(uid, 10, "orden-concurrente"));

        assertThat(saldo(uid)).isEqualByComparingTo("90");
        assertThat(resultados).contains(true);
        assertThat(libro.consultarOperacionPorRefId("orden-concurrente").estado()).isEqualTo("CONSUMIDA");
    }

    @Test
    @DisplayName("concurrencia: ocho cobros distintos que juntos pasan del saldo nunca lo dejan negativo")
    void refIdsDistintosConcurrentesSinNegativo() throws Exception {
        String uid = jugadorCon(100);

        List<Boolean> resultados = aLaVez(8, i -> debitar(uid, 30, "orden-" + i));

        long cobrados = resultados.stream().filter(Boolean::booleanValue).count();
        assertThat(cobrados).isEqualTo(3);
        assertThat(saldo(uid)).isEqualByComparingTo(BigDecimal.valueOf(100 - 30 * cobrados));
        assertThat(saldo(uid)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @FunctionalInterface
    private interface Operacion {
        void hacer(int indice);
    }

    /** Lanza {@code hilos} operaciones a la vez; true las que terminaron sin error. */
    private static List<Boolean> aLaVez(int hilos, Operacion operacion) throws Exception {
        CyclicBarrier salida = new CyclicBarrier(hilos);
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        List<Future<Boolean>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < hilos; i++) {
                int indice = i;
                futuros.add(ejecutor.submit(() -> {
                    salida.await(20, TimeUnit.SECONDS);
                    try {
                        operacion.hacer(indice);
                        return true;
                    } catch (RuntimeException rechazada) {
                        return false;
                    }
                }));
            }
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> futuro : futuros) {
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            ejecutor.shutdownNow();
        }
    }
}
