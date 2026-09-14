package com.nexusbattles.ms_subastas.notificaciones;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Entrega al modulo de notificaciones los avisos que {@link NotificacionOutbox}
 * dejo escritos, y marca cada fila cuando llega.
 *
 * <p>Sin esto, el patron outbox esta a medias: los avisos de los criterios 2 y
 * 4 de HU-SUB-004 se guardaban en la tabla y no salian de ms-subastas. Escribir
 * la intencion es lo que garantiza que no se pierdan; entregarla es lo que hace
 * que el jugador se entere.
 *
 * <p><b>Cada fila se marca por separado y fuera de una transaccion comun.</b> Si
 * el aviso numero siete falla, los seis anteriores ya quedaron marcados y no se
 * vuelven a enviar. Una transaccion unica los desharia todos y el jugador
 * recibiria seis avisos repetidos en el siguiente intento.
 *
 * <p><b>Al primer fallo se corta el lote.</b> Si el modulo de notificaciones no
 * responde, no va a responder para los 200 avisos siguientes: seguir seria
 * castigar a un servicio caido y llenar el log. Lo pendiente se queda en la
 * tabla, que es justo para lo que existe, y se reintenta en la pasada siguiente.
 */
@Component
@RequiredArgsConstructor
public class DrenadorDeNotificacionesJob {

    private static final Logger log = LoggerFactory.getLogger(DrenadorDeNotificacionesJob.class);

    private final NotificacionPendienteRepository repositorio;
    private final NotificacionesClient notificaciones;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.notificaciones.drenaje-intervalo-ms:5000}")
    public void drenar() {
        List<NotificacionPendiente> pendientes = repositorio.findByEnviadaEnIsNullOrderByCreadaEnAsc();
        if (pendientes.isEmpty()) {
            return;
        }

        int entregados = 0;
        for (NotificacionPendiente pendiente : pendientes) {
            try {
                notificaciones.entregar(avisoDe(pendiente));
            } catch (RuntimeException fallo) {
                log.warn("No se pudo entregar el aviso {} ({} de {} en este lote): {}. "
                                + "Queda en el outbox y se reintenta.",
                        pendiente.getId(), entregados + 1, pendientes.size(), fallo.getMessage());
                break;
            }
            pendiente.setEnviadaEn(clock.instant());
            repositorio.save(pendiente);
            entregados++;
        }

        if (entregados > 0) {
            log.debug("Entregados {} de {} avisos pendientes", entregados, pendientes.size());
        }
    }

    private NotificacionesClient.Aviso avisoDe(NotificacionPendiente pendiente) {
        return new NotificacionesClient.Aviso(
                pendiente.getId(),
                pendiente.getDestinatarioId(),
                pendiente.getTipo(),
                pendiente.getTipo().getTitulo(),
                pendiente.getDetalle(),
                pendiente.getCreadaEn());
    }
}
