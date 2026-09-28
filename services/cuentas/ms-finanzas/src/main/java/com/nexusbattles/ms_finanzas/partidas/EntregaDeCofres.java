package com.nexusbattles.ms_finanzas.partidas;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lleva el contenido de los cofres al inventario del jugador — cofres.yaml
 * 1.1.0 (B7).
 *
 * <p><b>Fuera de la transacción.</b> El cofre se gana dentro de la transacción
 * que procesa el resultado de la partida, pero la llamada a inventario no va
 * ahí: una llamada de red dentro de una transacción retiene las filas del libro
 * mientras otro servicio contesta, y si la transacción se deshiciera después,
 * el jugador tendría el premio de un cofre que no existe. Se intenta al
 * CONFIRMAR ({@link #entregarTrasConfirmar}), en otro hilo: dentro de
 * {@code afterCommit} la transacción ya confirmada sigue ligada al hilo y lo
 * que se escribiera ahí no se confirmaría nunca (lo destapó {@code CofresIT}).
 * Lo que no entre lo reintenta {@link #reintentarPendientes} con una espera
 * creciente.
 *
 * <p><b>Reintentar es seguro.</b> La entrega lleva
 * {@link CofreEntregado#claveDeEntrega()} como {@code Idempotency-Key}:
 * inventario devuelve la entrega original en vez de duplicarla. Si el intento
 * inmediato y el programado coinciden, el cofre lleva {@code @Version} y la
 * escritura que llega tarde no pisa a la otra.
 */
public class EntregaDeCofres {

    private static final Logger BITACORA = LoggerFactory.getLogger(EntregaDeCofres.class);

    private final CofreEntregadoRepository cofres;
    private final InventarioDeCofres inventario;
    private final ReglasDeCofres reglas;
    private final Clock reloj;
    private final Executor ejecutor;

    /**
     * @param ejecutor donde corre la entrega tras confirmar: hilos virtuales en
     *                 producción, el propio hilo en las pruebas unitarias
     */
    public EntregaDeCofres(CofreEntregadoRepository cofres, InventarioDeCofres inventario, ReglasDeCofres reglas,
                           Clock reloj, Executor ejecutor) {
        this.cofres = Objects.requireNonNull(cofres);
        this.inventario = Objects.requireNonNull(inventario);
        this.reglas = Objects.requireNonNull(reglas);
        this.reloj = Objects.requireNonNull(reloj);
        this.ejecutor = Objects.requireNonNull(ejecutor);
    }

    /**
     * Entrega estos cofres cuando se confirme la transacción en curso (sin
     * transacción, ya), fuera del hilo que la llevó. Nunca lanza: lo que falle
     * queda pendiente y lo recoge el reintento.
     */
    public void entregarTrasConfirmar(List<UUID> idsDeCofres) {
        if (idsDeCofres == null || idsDeCofres.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(idsDeCofres);
        Runnable tarea = conLaTrazaActual(() -> ids.forEach(this::intentarSinFallar));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lanzar(tarea);
                }
            });
            return;
        }
        lanzar(tarea);
    }

    private void lanzar(Runnable tarea) {
        try {
            ejecutor.execute(tarea);
        } catch (RejectedExecutionException cerrando) {
            // El servicio se esta apagando: el cofre queda PENDIENTE y lo
            // entrega el reintento programado al volver.
            BITACORA.warn("No se pudo lanzar la entrega de cofres: {}", cerrando.getMessage());
        }
    }

    /**
     * La tarea con el contexto de la bitácora de quien la lanza: el trace id
     * viaja al otro hilo y de ahí a la llamada a inventario (regla 5).
     */
    private static Runnable conLaTrazaActual(Runnable tarea) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previo = MDC.getCopyOfContextMap();
            if (contexto != null) {
                MDC.setContextMap(contexto);
            }
            try {
                tarea.run();
            } finally {
                if (previo != null) {
                    MDC.setContextMap(previo);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * Reintenta las entregas pendientes cuyo turno ya llegó, las más antiguas
     * primero, de veinte en veinte.
     *
     * @return cuántas quedaron entregadas en esta pasada
     */
    public int reintentarPendientes() {
        List<CofreEntregado> pendientes = cofres
                .findTop20ByEstadoEntregaAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEnAsc(
                        CofreEntregado.EstadoEntrega.PENDIENTE, reloj.instant());
        int entregados = 0;
        for (CofreEntregado cofre : pendientes) {
            if (intentarSinFallar(cofre.getId())) {
                entregados++;
            }
        }
        return entregados;
    }

    /**
     * Un intento de entrega de un cofre.
     *
     * @return true si el cofre quedó entregado (o ya lo estaba)
     */
    public boolean entregar(UUID idCofre) {
        CofreEntregado cofre = cofres.findById(idCofre).orElse(null);
        if (cofre == null) {
            return false;
        }
        if (!cofre.pendienteDeEntrega()) {
            return cofre.getEstadoEntrega() == CofreEntregado.EstadoEntrega.ENTREGADO;
        }
        try {
            String idDeEntrega = inventario.entregar(cofre);
            actualizar(idCofre, c -> c.entregado(idDeEntrega));
            BITACORA.info("Cofre {} entregado al inventario del jugador (entrega {})", idCofre, idDeEntrega);
            return true;
        } catch (InventarioDeCofres.EntregaNoRealizada fallo) {
            actualizar(idCofre, c -> c.entregaFallida(fallo.getMessage(), reloj.instant(),
                    reglas.esperaMaximaMinutos()));
            BITACORA.warn("El cofre {} queda pendiente de entrega y se reintentara: {}", idCofre, fallo.getMessage());
            return false;
        }
    }

    private boolean intentarSinFallar(UUID idCofre) {
        try {
            return entregar(idCofre);
        } catch (RuntimeException inesperado) {
            // Una escritura concurrente (el intento inmediato y el programado a
            // la vez) o la base sin responder: el cofre sigue como estaba y la
            // siguiente pasada lo vuelve a mirar.
            BITACORA.warn("No se pudo completar la entrega del cofre {}: {}", idCofre, inesperado.getMessage());
            return false;
        }
    }

    /** Relee el cofre, le aplica el cambio y lo guarda (bloqueo optimista). */
    private void actualizar(UUID idCofre, Consumer<CofreEntregado> cambio) {
        cofres.findById(idCofre).ifPresent(cofre -> {
            cambio.accept(cofre);
            cofres.save(cofre);
        });
    }
}
