package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.ms_chatbot.chat.api.TicketResponse;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketActualizado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 7.4.9 — soporte en vivo ({@code /user/queue/soporte/tickets},
 * {@code chatbot.yaml} 1.0.0): cuando un administrador atiende una
 * solicitud, su dueno lo ve en «Tus solicitudes» sin recargar.
 *
 * <ul>
 *   <li><b>Solo al dueno</b>: destino de usuario por el {@code uid} del
 *       ticket, que es el nombre de las conexiones de ese jugador
 *       ({@link PoliticaDelCanal}). Llega a todas sus conexiones abiertas; a
 *       nadie mas. Un visitante no tiene solicitudes.</li>
 *   <li><b>Lo que el jugador puede ver</b>: {@link TicketResponse}, la misma
 *       forma que {@code GET /chat/tickets}, sin {@code uid}, asignacion ni
 *       contexto de la conversacion.</li>
 *   <li><b>Despues del commit</b> ({@code AFTER_COMMIT}): un cambio que se
 *       deshace (409 de otro ticket abierto, un error de la base) no se
 *       anuncia. Si el jugador no esta conectado no pasa nada: lo vera al
 *       abrir «Soporte», que relee la lista por HTTP.</li>
 * </ul>
 *
 * <p>Un fallo al avisar no deshace nada ni llega al administrador: su cambio
 * ya esta guardado.
 */
@Component
public class AvisosDeSoporte {

    private static final Logger log = LoggerFactory.getLogger(AvisosDeSoporte.class);

    private final SimpMessagingTemplate mensajeria;

    public AvisosDeSoporte(SimpMessagingTemplate mensajeria) {
        this.mensajeria = mensajeria;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alActualizarTicket(TicketActualizado evento) {
        try {
            mensajeria.convertAndSendToUser(evento.ticket().getUid(), DestinosDelCanal.COLA_TICKETS,
                TicketResponse.desde(evento.ticket()));
        } catch (MessagingException noSePudo) {
            log.warn("No se pudo avisar por el canal en tiempo real del cambio de una solicitud de soporte.",
                noSePudo);
        }
    }
}
