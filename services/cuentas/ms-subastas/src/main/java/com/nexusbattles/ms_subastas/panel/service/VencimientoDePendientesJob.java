package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.panel.model.EstadoPendiente;
import com.nexusbattles.ms_subastas.panel.repository.PendienteDeRecogerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Aplica, cuando vencen los 7 dias de 7.7.9, la politica del parametro
 * {@code subastas.pendientes.al-vencer} a lo que nadie recogio. Cada pendiente
 * en su propia transaccion: uno que falla (inventario caido) se reintenta en la
 * pasada siguiente sin arrastrar a los demas.
 */
@Component
public class VencimientoDePendientesJob {

    private static final Logger log = LoggerFactory.getLogger(VencimientoDePendientesJob.class);

    private final PendienteDeRecogerRepository pendientes;
    private final PendientesService servicio;
    private final Clock clock;

    public VencimientoDePendientesJob(PendienteDeRecogerRepository pendientes, PendientesService servicio,
                                      Clock clock) {
        this.pendientes = pendientes;
        this.servicio = servicio;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.subastas.pendientes-intervalo-ms:300000}",
            initialDelayString = "${app.subastas.pendientes-intervalo-ms:300000}")
    public void resolverVencidos() {
        for (UUID subastaId : pendientes.idsVencidos(EstadoPendiente.PENDIENTE, clock.instant())) {
            try {
                servicio.resolverVencido(subastaId);
            } catch (RuntimeException fallo) {
                log.error("No se pudo resolver el pendiente vencido de la subasta {}: {}", subastaId,
                        fallo.getMessage(), fallo);
            }
        }
    }
}
