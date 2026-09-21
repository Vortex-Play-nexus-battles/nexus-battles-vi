package com.nexusbattles.ms_subastas.notificaciones;

import java.time.Instant;
import java.util.UUID;

/**
 * Salida hacia el modulo de notificaciones (equipo de plataforma). Se declara
 * como puerto para que el drenador se pueda probar sin levantar ese servicio.
 *
 * <p>Corresponde a {@code POST /internal/notifications} de
 * {@code contracts/openapi/notificaciones.yaml} (1.0.0). Ese camino es
 * provisional por decision de su dueno: cuando el equipo acuerde el bus de
 * eventos, se retira y el contrato abre version nueva. Entonces esto pasa a
 * publicar un evento y el drenador desaparece; nada mas de HU-SUB-004 cambia.
 */
public interface NotificacionesClient {

    /**
     * Entrega un aviso. Debe ser idempotente por {@code eventoId}: el contrato
     * dice que ese identificador lo pone quien emite, justamente para que un
     * reintento no duplique el aviso en la bandeja del jugador.
     */
    void entregar(Aviso aviso);

    /**
     * @param eventoId  id de la fila del outbox. Estable entre reintentos, que
     *                  es lo que hace segura la reentrega.
     * @param creadaEn  cuando ocurrio el hecho, no cuando se entrega: si el
     *                  drenador se atrasa, la bandeja debe mostrar la hora real
     *                  del cierre de la subasta.
     */
    record Aviso(UUID eventoId, UUID destinatarioId, TipoNotificacion tipo,
                 String titulo, String cuerpo, Instant creadaEn) { }
}
