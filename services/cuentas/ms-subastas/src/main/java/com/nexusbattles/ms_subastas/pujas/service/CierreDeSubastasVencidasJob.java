package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Dispara el cierre de las subastas cuyo plazo vencio, que es lo que activa la
 * restitucion de creditos del criterio 3 de HU-SUB-004.
 *
 * COORDINAR CON EDWIN: el contador de la subasta lo inicia HU-SUB-001, asi que
 * este disparador podria acabar duplicado si el tambien lo monta. La logica de
 * restitucion es de esta HU; el disparador es la costura entre las dos.
 *
 * Cada subasta se cierra en su propia transaccion para que una problematica no
 * arrastre al resto del lote.
 */
@Component
@RequiredArgsConstructor
public class CierreDeSubastasVencidasJob {

    private static final Logger log = LoggerFactory.getLogger(CierreDeSubastasVencidasJob.class);

    private final SubastaRepository subastaRepository;
    private final PujaApplicationService pujaApplicationService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.subastas.cierre-intervalo-ms:30000}")
    public void cerrarVencidas() {
        for (UUID subastaId : subastaRepository.findIdsDeActivasVencidas(clock.instant())) {
            try {
                pujaApplicationService.cerrarPorVencimiento(subastaId);
            } catch (RuntimeException e) {
                log.error("No se pudo cerrar la subasta vencida {}: {}", subastaId, e.getMessage(), e);
            }
        }
    }
}
