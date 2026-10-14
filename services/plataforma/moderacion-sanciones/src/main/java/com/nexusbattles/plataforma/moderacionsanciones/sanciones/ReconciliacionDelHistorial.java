package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La reconciliacion del historial de sanciones en identidad — SA-3.
 *
 * <p>La cola de salidas entrega el historial de cada emision nueva. Esto cubre
 * lo que la cola no ve: las sanciones emitidas antes de que existiera el canal
 * HISTORIAL (el primer arranque las sube todas) y cualquier diferencia que
 * pudiera quedar. Manda el resumen de cada cuenta con alguna sancion; es
 * idempotente, porque identidad guarda valores absolutos. Si identidad no
 * responde, para en la primera cuenta y lo intenta en la siguiente vuelta.
 */
public class ReconciliacionDelHistorial {

    private static final Logger BITACORA = LoggerFactory.getLogger(ReconciliacionDelHistorial.class);

    private final HistorialEnIdentidad historial;
    private final ClienteIdentidad identidad;
    private final SancionRepository sanciones;

    public ReconciliacionDelHistorial(HistorialEnIdentidad historial, ClienteIdentidad identidad,
                                      SancionRepository sanciones) {
        this.historial = Objects.requireNonNull(historial);
        this.identidad = Objects.requireNonNull(identidad);
        this.sanciones = Objects.requireNonNull(sanciones);
    }

    /** Un minuto despues de arrancar y cada seis horas, por omision. */
    @Scheduled(initialDelayString = "${sanciones.historial.reconciliacion-inicial-ms:60000}",
            fixedDelayString = "${sanciones.historial.reconciliacion-ms:21600000}")
    public void programada() {
        reconciliar();
    }

    /** @return cuantas cuentas quedaron al dia en esta vuelta */
    public int reconciliar() {
        List<UUID> cuentas = sanciones.usuariosConSanciones();
        int alDia = 0;
        for (UUID cuenta : cuentas) {
            try {
                identidad.historial(cuenta, historial.resumenDe(cuenta));
                alDia++;
            } catch (RuntimeException noResponde) {
                BITACORA.warn("Reconciliacion del historial de sanciones detenida en la cuenta {} ({} de {} al dia): {}",
                        cuenta, alDia, cuentas.size(), noResponde.getMessage());
                return alDia;
            }
        }
        if (!cuentas.isEmpty()) {
            BITACORA.info("Historial de sanciones reconciliado en identidad: {} cuentas", alDia);
        }
        return alDia;
    }
}
