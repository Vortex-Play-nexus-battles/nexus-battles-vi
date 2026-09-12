package com.nexusbattles.ms_subastas.notificaciones;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Encola los avisos que exige HU-SUB-004 sin depender del microservicio de
 * notificaciones, que es del grupo de Simon y no entra en el Sprint 2.
 *
 * Patron outbox transaccional: se escribe la intencion del aviso en la BD de
 * este servicio, dentro de la misma transaccion que el cambio de negocio. Asi
 * el criterio "notificando a quienes hubieran pujado" queda cumplido hasta la
 * frontera de ms-subastas, y cuando exista el servicio de notificaciones solo
 * hay que drenar la tabla — sin volver a tocar la logica de pujas.
 *
 * No incluye el drenador a proposito: entregar a donde nadie escucha todavia
 * seria codigo sin forma de verificarse.
 */
@Service
@RequiredArgsConstructor
public class NotificacionOutbox {

    private final NotificacionPendienteRepository repositorio;
    private final Clock clock;

    /**
     * @param postores todos los jugadores que pujaron en la subasta
     * @param comprador quien ejecuto la compra inmediata; se excluye porque ya
     *                  recibe el resultado de su propia peticion
     */
    public void avisarCierrePorCompraInmediata(UUID subastaId, List<UUID> postores, UUID comprador) {
        List<NotificacionPendiente> avisos = postores.stream()
                .filter(postor -> !postor.equals(comprador))
                .map(postor -> nuevoAviso(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA, postor, subastaId,
                        "La subasta se cerro porque otro jugador la compro de forma inmediata."))
                .toList();

        if (avisos.isEmpty()) {
            return;
        }
        repositorio.saveAll(avisos);
    }

    public void avisarLimiteAutomaticoAlcanzado(UUID subastaId, UUID jugadorId, BigDecimal limite) {
        repositorio.save(nuevoAviso(TipoNotificacion.LIMITE_AUTOMATICO_ALCANZADO, jugadorId, subastaId,
                "Tu puja automatica se detuvo: la siguiente oferta superaria tu limite de " + limite + " creditos."));
    }

    private NotificacionPendiente nuevoAviso(TipoNotificacion tipo, UUID destinatarioId, UUID subastaId, String detalle) {
        return new NotificacionPendiente(null, tipo, destinatarioId, subastaId, detalle, clock.instant(), null);
    }
}
