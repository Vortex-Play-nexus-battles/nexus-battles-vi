package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Entrega las salidas pendientes de las sanciones y reintenta las que no
 * entraron — HU-NOT-005 (CA-04) y 7.3.2. Antes de B2 era
 * {@code EntregadorDeAvisos} y solo conocia la bandeja; ahora reparte cada
 * salida al destino de su canal ({@link DestinoDeSalidas}).
 *
 * <p>Corre cada {@code sanciones.avisos.reintento-ms} (15 s por omision, el
 * mismo intervalo de siempre). En cada vuelta:
 * <ul>
 *   <li>toma las salidas sin entregar cuya espera ya paso, de la mas antigua a
 *       la mas reciente;</li>
 *   <li>una que el destino no pudo recibir (caido, lento, sin credencial) se
 *       queda con su intento, su motivo y una espera que crece con cada fallo
 *       hasta {@code sanciones.salidas.espera-maxima-ms}; ademas, las demas
 *       salidas de ese mismo canal no se intentan en esta vuelta: si identidad
 *       no responde, no tiene sentido esperar su tiempo de conexion cincuenta
 *       veces seguidas mientras los avisos esperan detras;</li>
 *   <li>una que el destino rechaza (4xx de fondo) tambien se queda, con el
 *       motivo a la vista y la misma espera creciente, porque reintentarla a
 *       ciegas no la arreglaria. Nunca se descarta nada.</li>
 * </ul>
 *
 * <p>No hay una transaccion alrededor de la vuelta: cada salida se guarda al
 * terminar su intento, sin tener una conexion a la base abierta mientras se
 * espera a otro servicio.
 */
public class EntregadorDeSalidas {

    private static final Logger BITACORA = LoggerFactory.getLogger(EntregadorDeSalidas.class);

    static final int LOTE = 50;

    private final SalidaPendienteRepository salidas;
    private final Map<CanalDeSalida, DestinoDeSalidas> destinos;
    private final Clock reloj;
    private final Duration esperaBase;
    private final Duration esperaMaxima;

    public EntregadorDeSalidas(SalidaPendienteRepository salidas, List<DestinoDeSalidas> destinos, Clock reloj,
                               Duration esperaBase, Duration esperaMaxima) {
        this.salidas = Objects.requireNonNull(salidas);
        this.destinos = new EnumMap<>(CanalDeSalida.class);
        destinos.forEach(destino -> this.destinos.put(destino.canal(), destino));
        this.reloj = Objects.requireNonNull(reloj);
        this.esperaBase = Objects.requireNonNull(esperaBase);
        this.esperaMaxima = Objects.requireNonNull(esperaMaxima);
    }

    @Scheduled(fixedDelayString = "${sanciones.avisos.reintento-ms:15000}",
            initialDelayString = "${sanciones.avisos.reintento-ms:15000}")
    public void entregarPendientes() {
        ejecutar();
    }

    /** @return cuantas salidas quedaron entregadas en esta vuelta */
    public int ejecutar() {
        int entregadas = 0;
        Set<CanalDeSalida> caidos = EnumSet.noneOf(CanalDeSalida.class);
        List<SalidaPendiente> pendientes = salidas.porEntregar(ahora(), PageRequest.of(0, LOTE));
        for (SalidaPendiente salida : pendientes) {
            if (caidos.contains(salida.canal())) {
                continue;
            }
            OffsetDateTime ahora = ahora();
            DestinoDeSalidas destino = destinos.get(salida.canal());
            try {
                if (destino == null) {
                    throw new IllegalStateException("no hay destino configurado para el canal " + salida.canal());
                }
                if (destino.entregar(salida) == DestinoDeSalidas.Resultado.ENTREGADO) {
                    salida.entregado(ahora);
                    entregadas++;
                } else {
                    salida.fallo("rechazada por el destino " + salida.canal(), ahora, esperaBase, esperaMaxima);
                    BITACORA.error("Salida {} ({} {}) rechazada por su destino; queda pendiente para revision",
                            salida.id(), salida.canal(), salida.tipo());
                }
            } catch (RuntimeException noResponde) {
                caidos.add(salida.canal());
                salida.fallo(noResponde.getClass().getSimpleName() + ": " + noResponde.getMessage(), ahora,
                        esperaBase, esperaMaxima);
                BITACORA.warn("Salida {} ({} {}) no entregada (intento {}): {}", salida.id(), salida.canal(),
                        salida.tipo(), salida.intentos(), noResponde.getMessage());
            }
            salidas.save(salida);
        }
        return entregadas;
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
