package com.nexusbattles.ms_subastas.notificaciones;

import com.nexusbattles.ms_subastas.reglas.ReglasDelDocumento;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 7.7.8: «Aviso 1 hora antes de finalizar subastas en las que se participa» y
 * «Recordatorio de subastas guardadas en lista de seguimiento» (B8).
 *
 * <p>Cada minuto busca las activas que cierran en la proxima hora y todavia no
 * avisaron. Cada una en su propia transaccion, con el candado de la subasta:
 * se marca {@code recordatorio_enviado_en} en la misma transaccion que encola
 * los avisos, asi que ni se repite (dos pasadas, dos replicas) ni se pierde.
 * Si el servicio estuvo parado y la subasta ya cerro, no se avisa: el
 * recordatorio de algo que ya termino confunde mas de lo que ayuda.
 */
@Component
public class RecordatorioDeCierreJob {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioDeCierreJob.class);

    private final SubastaRepository subastas;
    private final AvisosDeSubasta avisos;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public RecordatorioDeCierreJob(SubastaRepository subastas, AvisosDeSubasta avisos, Clock clock,
                                   PlatformTransactionManager transacciones) {
        this.subastas = Objects.requireNonNull(subastas);
        this.avisos = Objects.requireNonNull(avisos);
        this.clock = Objects.requireNonNull(clock);
        this.transaccion = new TransactionTemplate(transacciones);
    }

    @Scheduled(fixedDelayString = "${app.subastas.recordatorio-intervalo-ms:60000}",
            initialDelayString = "${app.subastas.recordatorio-intervalo-ms:60000}")
    public void recordar() {
        Instant ahora = clock.instant();
        for (UUID subastaId : subastas.idsParaRecordar(ahora, ahora.plus(ReglasDelDocumento.RECORDATORIO_ANTES_DEL_CIERRE))) {
            try {
                transaccion.executeWithoutResult(estado -> recordarUna(subastaId));
            } catch (RuntimeException fallo) {
                log.error("No se pudo encolar el recordatorio de la subasta {}: {}", subastaId, fallo.getMessage(), fallo);
            }
        }
    }

    /** @return si encolo el recordatorio (falso: otra pasada ya lo hizo o la subasta cambio) */
    boolean recordarUna(UUID subastaId) {
        Subasta subasta = subastas.findByIdParaActualizar(subastaId).orElse(null);
        Instant ahora = clock.instant();
        if (subasta == null || !subasta.estaActiva() || subasta.getRecordatorioEnviadoEn() != null
                || !subasta.getFechaFin().isAfter(ahora)) {
            return false;
        }
        avisos.recordatorio(subasta);
        subasta.setRecordatorioEnviadoEn(ahora);
        subastas.save(subasta);
        return true;
    }
}
