package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante por remitente, en la memoria de esta instancia — B6.
 *
 * <p><b>Valores provisionales, decision del PO pendiente.</b> Ningun requisito
 * fija cuantos mensajes privados seguidos son razonables; por omision son 5
 * cada 10 segundos ({@code MENSAJES_DIRECTOS_LIMITE_MENSAJES} y
 * {@code MENSAJES_DIRECTOS_LIMITE_VENTANA_SEGUNDOS}), suficiente para una
 * conversacion rapida y demasiado poco para inundar la bandeja de nadie.
 *
 * <p><b>Por que en memoria.</b> salas-partidas corre como una sola instancia
 * y no tiene Redis (solo lo usa moderacion). Con varias instancias el limite
 * seria por instancia —mas permisivo, nunca mas estricto—; el dia que se
 * escale, se cambia este adaptador y el caso de uso no se entera.
 *
 * <p>Un intento que no cabe no se cuenta: si no, insistir alargaria la espera
 * sin fin. Las entradas de quien lleva un rato callado se barren solas.
 */
public class LimiteDeFrecuenciaEnMemoria implements LimiteDeFrecuencia {

    /** Cada cuantos registros se barren las ventanas vacias. */
    private static final int BARRER_CADA = 1_000;

    private final int maximo;
    private final Duration ventana;
    private final Clock reloj;
    private final ConcurrentHashMap<UUID, Deque<Instant>> envios = new ConcurrentHashMap<>();
    private int registrosDesdeElBarrido;

    /**
     * @param maximo  cuantos caben en la ventana (al menos 1)
     * @param ventana su duracion (positiva)
     */
    public LimiteDeFrecuenciaEnMemoria(int maximo, Duration ventana, Clock reloj) {
        if (maximo < 1) {
            throw new IllegalArgumentException("El limite de mensajes seguidos tiene que dejar pasar al menos uno.");
        }
        if (ventana == null || ventana.isZero() || ventana.isNegative()) {
            throw new IllegalArgumentException("La ventana del limite tiene que durar algo.");
        }
        this.maximo = maximo;
        this.ventana = ventana;
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public Optional<Duration> registrar(UUID remitente) {
        Objects.requireNonNull(remitente, "El limite es por remitente.");
        Instant ahora = reloj.instant();
        Duration[] espera = {null};
        envios.compute(remitente, (clave, enVentana) -> {
            Deque<Instant> cola = enVentana == null ? new ArrayDeque<>() : enVentana;
            olvidarLoViejo(cola, ahora);
            if (cola.size() >= maximo) {
                espera[0] = Duration.between(ahora, cola.peekFirst().plus(ventana));
            } else {
                cola.addLast(ahora);
            }
            return cola;
        });
        barrerDeVezEnCuando(ahora);
        return Optional.ofNullable(espera[0]);
    }

    private void olvidarLoViejo(Deque<Instant> cola, Instant ahora) {
        Instant limite = ahora.minus(ventana);
        while (!cola.isEmpty() && !cola.peekFirst().isAfter(limite)) {
            cola.pollFirst();
        }
    }

    private synchronized void barrerDeVezEnCuando(Instant ahora) {
        if (++registrosDesdeElBarrido < BARRER_CADA) {
            return;
        }
        registrosDesdeElBarrido = 0;
        for (UUID remitente : envios.keySet()) {
            envios.computeIfPresent(remitente, (clave, cola) -> {
                olvidarLoViejo(cola, ahora);
                return cola.isEmpty() ? null : cola;
            });
        }
    }

    /** Cuantos remitentes tienen ventana abierta; para las pruebas del barrido. */
    int remitentesEnMemoria() {
        return envios.size();
    }
}
