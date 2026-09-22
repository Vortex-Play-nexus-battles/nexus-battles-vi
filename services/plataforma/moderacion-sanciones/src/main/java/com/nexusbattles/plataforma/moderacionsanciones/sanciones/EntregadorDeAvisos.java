package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Entrega los avisos pendientes al modulo de notificaciones y reintenta los
 * que no entraron — HU-NOT-005, CA-04.
 *
 * <p>Corre de forma periodica. Un aviso que el modulo no puede recibir (no
 * responde) se queda pendiente con su motivo y vuelve a la siguiente vuelta;
 * uno que el modulo rechaza (400) tambien se queda, con el error a la vista,
 * porque reintentarlo a ciegas no lo arreglaria. Nunca se descarta.
 */
@Component
public class EntregadorDeAvisos {

    private static final Logger BITACORA = LoggerFactory.getLogger(EntregadorDeAvisos.class);

    static final int LOTE = 50;

    private final AvisoPendienteRepository avisos;
    private final EmisorDeAvisos emisor;
    private final Clock reloj;

    public EntregadorDeAvisos(AvisoPendienteRepository avisos, EmisorDeAvisos emisor, Clock reloj) {
        this.avisos = avisos;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${sanciones.avisos.reintento-ms:15000}",
            initialDelayString = "${sanciones.avisos.reintento-ms:15000}")
    public void entregarPendientes() {
        ejecutar();
    }

    /** @return cuantos avisos quedaron entregados en esta vuelta */
    @Transactional
    public int ejecutar() {
        int entregados = 0;
        List<AvisoPendiente> pendientes = avisos.findByEntregadoEnIsNullOrderByCreadoEnAsc(PageRequest.of(0, LOTE));
        for (AvisoPendiente aviso : pendientes) {
            OffsetDateTime ahora = OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
            try {
                EmisorDeAvisos.Resultado resultado = emisor.entregar(aviso);
                if (resultado == EmisorDeAvisos.Resultado.ENTREGADO) {
                    aviso.entregado(ahora);
                    entregados++;
                } else {
                    aviso.fallo("el modulo de notificaciones rechazo el aviso", ahora);
                    BITACORA.error("Aviso {} rechazado por notificaciones; se deja pendiente para revision", aviso.id());
                }
            } catch (RuntimeException noResponde) {
                aviso.fallo(noResponde.getMessage(), ahora);
                BITACORA.warn("Aviso {} no entregado (intento {}): {}", aviso.id(), aviso.intentos(),
                        noResponde.getMessage());
            }
            avisos.save(aviso);
        }
        return entregados;
    }
}
