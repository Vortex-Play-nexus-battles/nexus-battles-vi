package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * La entrega del cofre al inventario — fuera de la transacción, idempotente
 * y con reintento (cofres.yaml 1.1.0, B7).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntregaDeCofres · al confirmar, con reintento y sin duplicar")
class EntregaDeCofresTest {

    private static final Instant AHORA = Instant.parse("2026-09-16T10:00:00Z");
    private static final TablaDeCofre TABLA = TablaDeCofre.desde("PRUEBA-1", "producto-a=1");

    @Mock
    private CofreEntregadoRepository cofres;

    private final InventarioFalso inventario = new InventarioFalso();
    private EntregaDeCofres entrega;

    /** Inventario que anota lo que se le pide y contesta lo programado. */
    static final class InventarioFalso implements InventarioDeCofres {
        final List<String> claves = new ArrayList<>();
        RuntimeException fallo;

        @Override
        public String entregar(CofreEntregado cofre) {
            claves.add(cofre.claveDeEntrega());
            if (fallo != null) {
                throw fallo;
            }
            return "entrega-" + cofre.getId();
        }
    }

    /** Lo que se lanza «en otro hilo», para ejecutarlo cuando la prueba quiera. */
    private final List<Runnable> lanzadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        entrega = new EntregaDeCofres(cofres, inventario,
                new ReglasDeCofres(ZoneOffset.UTC, true, TABLA, 3), Clock.fixed(AHORA, ZoneOffset.UTC),
                Runnable::run);
        org.mockito.Mockito.lenient().when(cofres.save(any(CofreEntregado.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private CofreEntregado pendiente() {
        CofreEntregado cofre = CofreEntregado.sorteado("uid-1", "2026-W38", AHORA, 42L, TABLA);
        when(cofres.findById(cofre.getId())).thenReturn(Optional.of(cofre));
        return cofre;
    }

    @Test
    @DisplayName("una entrega que entra deja el cofre ENTREGADO, con el id de la entrega")
    void entregaQueEntra() {
        CofreEntregado cofre = pendiente();

        assertThat(entrega.entregar(cofre.getId())).isTrue();

        assertThat(cofre.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.ENTREGADO);
        assertThat(cofre.getEntregaId()).isEqualTo("entrega-" + cofre.getId());
        assertThat(cofre.getUltimoError()).isNull();
        assertThat(cofre.getProximoIntentoEn()).isNull();
        assertThat(inventario.claves).containsExactly("cofre-" + cofre.getId());
        verify(cofres).save(cofre);
    }

    @Test
    @DisplayName("si inventario no la hace, queda PENDIENTE con el motivo y el próximo intento un minuto después")
    void entregaQueFalla() {
        CofreEntregado cofre = pendiente();
        inventario.fallo = new InventarioDeCofres.EntregaNoRealizada("inventario respondio 404");

        assertThat(entrega.entregar(cofre.getId())).isFalse();

        assertThat(cofre.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.PENDIENTE);
        assertThat(cofre.getIntentosEntrega()).isEqualTo(1);
        assertThat(cofre.getUltimoError()).isEqualTo("inventario respondio 404");
        assertThat(cofre.getProximoIntentoEn()).isEqualTo(AHORA.plusSeconds(60));
    }

    @Test
    @DisplayName("la espera entre reintentos crece un minuto por intento y tiene tope")
    void esperaConTope() {
        CofreEntregado cofre = CofreEntregado.sorteado("uid-1", "2026-W38", AHORA, 42L, TABLA);

        for (int i = 0; i < 10; i++) {
            cofre.entregaFallida("x".repeat(400), AHORA, 3);
        }

        assertThat(cofre.getIntentosEntrega()).isEqualTo(10);
        assertThat(cofre.getProximoIntentoEn()).isEqualTo(AHORA.plusSeconds(3 * 60));
        assertThat(cofre.getUltimoError()).hasSize(300);
    }

    @Test
    @DisplayName("un cofre ya entregado no se vuelve a pedir, y un fallo tardío no lo devuelve a pendiente")
    void yaEntregado() {
        CofreEntregado cofre = pendiente();
        cofre.entregado("entrega-previa");

        assertThat(entrega.entregar(cofre.getId())).isTrue();
        cofre.entregaFallida("tarde", AHORA, 3);

        assertThat(inventario.claves).isEmpty();
        assertThat(cofre.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.ENTREGADO);
        assertThat(cofre.getUltimoError()).isNull();
    }

    @Test
    @DisplayName("un cofre que no existe no se entrega, y uno sin contenido tampoco")
    void noExisteOSinContenido() {
        UUID fantasma = UUID.randomUUID();
        when(cofres.findById(fantasma)).thenReturn(Optional.empty());
        CofreEntregado antiguo = new CofreEntregado();
        antiguo.setId(UUID.randomUUID());
        when(cofres.findById(antiguo.getId())).thenReturn(Optional.of(antiguo));

        assertThat(entrega.entregar(fantasma)).isFalse();
        assertThat(entrega.entregar(antiguo.getId())).isFalse();
        assertThat(inventario.claves).isEmpty();
    }

    @Test
    @DisplayName("sin transacción en curso la entrega se intenta en el acto")
    void sinTransaccionEnElActo() {
        CofreEntregado cofre = pendiente();

        entrega.entregarTrasConfirmar(List.of(cofre.getId()));

        assertThat(inventario.claves).hasSize(1);
    }

    @Test
    @DisplayName("con transacción en curso la entrega espera a que se confirme: nunca dentro")
    void esperaALaConfirmacion() {
        CofreEntregado cofre = pendiente();
        TransactionSynchronizationManager.initSynchronization();

        entrega.entregarTrasConfirmar(List.of(cofre.getId()));
        assertThat(inventario.claves).as("dentro de la transaccion no se llama a nadie").isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(inventario.claves).hasSize(1);
    }

    @Test
    @DisplayName("la entrega corre en otro hilo con el trace id de quien la lanzó (regla 5)")
    void otroHiloConLaTraza() {
        CofreEntregado cofre = pendiente();
        List<String> trazasVistas = new ArrayList<>();
        EntregaDeCofres enOtroHilo = new EntregaDeCofres(cofres, cofreRecibido -> {
            trazasVistas.add(org.slf4j.MDC.get("trace.id"));
            return "entrega-1";
        }, new ReglasDeCofres(ZoneOffset.UTC, true, TABLA, 3), Clock.fixed(AHORA, ZoneOffset.UTC), lanzadas::add);
        org.slf4j.MDC.put("trace.id", "traza-de-la-partida");
        try {
            enOtroHilo.entregarTrasConfirmar(List.of(cofre.getId()));
        } finally {
            org.slf4j.MDC.clear();
        }

        assertThat(trazasVistas).as("nada corre en el hilo de la peticion").isEmpty();
        lanzadas.forEach(Runnable::run);

        assertThat(trazasVistas).containsExactly("traza-de-la-partida");
        assertThat(org.slf4j.MDC.get("trace.id")).as("el hilo queda limpio").isNull();
    }

    @Test
    @DisplayName("si el ejecutor ya no acepta tareas (apagando), el cofre queda pendiente sin romper nada")
    void ejecutorCerrado() {
        EntregaDeCofres apagando = new EntregaDeCofres(cofres, inventario,
                new ReglasDeCofres(ZoneOffset.UTC, true, TABLA, 3), Clock.fixed(AHORA, ZoneOffset.UTC), tarea -> {
                    throw new java.util.concurrent.RejectedExecutionException("cerrado");
                });

        apagando.entregarTrasConfirmar(List.of(UUID.randomUUID()));

        assertThat(inventario.claves).isEmpty();
    }

    @Test
    @DisplayName("sin cofres no se registra nada")
    void sinCofres() {
        entrega.entregarTrasConfirmar(List.of());
        entrega.entregarTrasConfirmar(null);

        assertThat(inventario.claves).isEmpty();
    }

    @Test
    @DisplayName("el reintento recorre los pendientes que ya tocan y un fallo no para a los demás")
    void reintento() {
        CofreEntregado conflicto = CofreEntregado.sorteado("uid-1", "2026-W38", AHORA, 1L, TABLA);
        CofreEntregado bueno = CofreEntregado.sorteado("uid-2", "2026-W38", AHORA, 2L, TABLA);
        when(cofres.findTop20ByEstadoEntregaAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEnAsc(
                CofreEntregado.EstadoEntrega.PENDIENTE, AHORA)).thenReturn(List.of(conflicto, bueno));
        when(cofres.findById(conflicto.getId())).thenThrow(new OptimisticLockingFailureException("otra escritura"));
        when(cofres.findById(bueno.getId())).thenReturn(Optional.of(bueno));

        int entregados = entrega.reintentarPendientes();

        assertThat(entregados).isEqualTo(1);
        assertThat(bueno.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.ENTREGADO);
        verify(cofres, times(1)).save(bueno);
        verify(cofres, never()).save(eq(conflicto));
    }
}
