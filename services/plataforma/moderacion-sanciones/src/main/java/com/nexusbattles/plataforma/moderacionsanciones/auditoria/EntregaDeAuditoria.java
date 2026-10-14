package com.nexusbattles.plataforma.moderacionsanciones.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Entrega a ms-cumplimiento los asientos pendientes — §7.3.5 (SA-5).
 *
 * <p>Cada {@code auditoria.reintento-ms} (10 s por omision) toma los
 * pendientes cuya espera ya paso, del mas antiguo al mas reciente:
 * <ul>
 *   <li>201 o 200: entregado (200 es que la referencia ya estaba: una entrega
 *       anterior entro y no llego a marcarse).</li>
 *   <li>4xx: se queda con el motivo a la vista y una espera creciente; nunca
 *       se descarta.</li>
 *   <li>Sin respuesta o 5xx: ms-cumplimiento esta caido; se para la vuelta
 *       (los demas fallarian igual) y se reintenta con espera creciente.</li>
 * </ul>
 *
 * <p>Sin {@code AUDITORIA_URL} no hay cliente: los asientos se quedan en la
 * cola, con su linea en la bitacora JSON, y salen en cuanto se configure.
 */
public class EntregaDeAuditoria {

    private static final Logger BITACORA = LoggerFactory.getLogger(EntregaDeAuditoria.class);

    static final int LOTE = 50;

    private final AsientoDeAuditoriaRepository asientos;
    private final ClienteDeCumplimiento cumplimiento;
    private final Clock reloj;
    private final Duration esperaBase;
    private final Duration esperaMaxima;

    public EntregaDeAuditoria(AsientoDeAuditoriaRepository asientos, ClienteDeCumplimiento cumplimiento,
                              Clock reloj, Duration esperaBase, Duration esperaMaxima) {
        this.asientos = Objects.requireNonNull(asientos);
        this.cumplimiento = cumplimiento;
        this.reloj = Objects.requireNonNull(reloj);
        this.esperaBase = Objects.requireNonNull(esperaBase);
        this.esperaMaxima = Objects.requireNonNull(esperaMaxima);
    }

    @Scheduled(fixedDelayString = "${auditoria.reintento-ms:10000}",
            initialDelayString = "${auditoria.reintento-ms:10000}")
    public void entregarPendientes() {
        ejecutar();
    }

    /** @return cuantos asientos quedaron entregados en esta vuelta */
    public int ejecutar() {
        if (cumplimiento == null) {
            return 0;
        }
        int entregados = 0;
        List<AsientoDeAuditoria> pendientes = asientos.porEntregar(ahora(), PageRequest.of(0, LOTE));
        for (AsientoDeAuditoria asiento : pendientes) {
            OffsetDateTime ahora = ahora();
            try {
                if (cumplimiento.entregar(asiento) == ClienteDeCumplimiento.Resultado.REGISTRADO) {
                    asiento.entregado(ahora);
                    entregados++;
                } else {
                    asiento.fallo("rechazado por ms-cumplimiento", ahora, esperaBase, esperaMaxima);
                    BITACORA.error("Asiento de auditoria {} ({} sobre {}) rechazado por ms-cumplimiento; "
                            + "queda pendiente para revision", asiento.id(), asiento.tipoAccion(), asiento.afectado());
                }
                asientos.save(asiento);
            } catch (RuntimeException noResponde) {
                asiento.fallo(noResponde.getClass().getSimpleName() + ": " + noResponde.getMessage(), ahora,
                        esperaBase, esperaMaxima);
                asientos.save(asiento);
                BITACORA.warn("La bitacora central no responde; {} asiento(s) esperan (intento {} de {}): {}",
                        pendientes.size() - entregados, asiento.intentos(), asiento.id(), noResponde.getMessage());
                break;
            }
        }
        return entregados;
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
