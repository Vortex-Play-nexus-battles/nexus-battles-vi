package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.ms_chatbot.chat.api.TicketResponse;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonimaRepository;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturaDeSoporte;
import com.nexusbattles.ms_chatbot.chat.soporte.CapturasDeSoporteService;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketActualizado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 7.4.9 — soporte en vivo ({@code /user/queue/soporte/tickets},
 * {@code chatbot.yaml} 1.0.0): cuando un administrador atiende una
 * solicitud, su dueno lo ve en «Tus solicitudes» sin recargar.
 *
 * <ul>
 *   <li><b>Solo al dueno</b>: destino de usuario por el {@code uid} del
 *       ticket, que es el nombre de las conexiones de ese jugador
 *       ({@link PoliticaDelCanal}). Llega a todas sus conexiones abiertas; a
 *       nadie mas. Desde 1.12.0 un visitante tambien tiene solicitudes: son
 *       de la clave de su conversacion ({@code anonimo:<sesion>}) y sus
 *       conexiones se llaman {@code anonimo:<huella de la sesion>}; se
 *       traduce una a la otra con la sesion registrada, nunca con un dato
 *       del cliente. Si la sesion ya no existe, no se avisa a nadie.</li>
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
    private final SesionAnonimaRepository sesiones;
    private final CapturasDeSoporteService capturas;

    public AvisosDeSoporte(SimpMessagingTemplate mensajeria, SesionAnonimaRepository sesiones,
                           CapturasDeSoporteService capturas) {
        this.mensajeria = mensajeria;
        this.sesiones = sesiones;
        this.capturas = capturas;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alActualizarTicket(TicketActualizado evento) {
        try {
            Optional<String> destinatario = nombreEnElCanal(evento.ticket().getUid());
            if (destinatario.isEmpty()) {
                return;
            }
            // 1.14.0: igual que en GET /chat/tickets, con sus capturas.
            UUID id = evento.ticket().getId();
            List<CapturaDeSoporte.Resumen> adjuntas = id == null
                ? List.of() : capturas.deLasSolicitudes(List.of(id)).getOrDefault(id, List.of());
            mensajeria.convertAndSendToUser(destinatario.get(), DestinosDelCanal.COLA_TICKETS,
                TicketResponse.desde(evento.ticket(), adjuntas));
        } catch (MessagingException noSePudo) {
            log.warn("No se pudo avisar por el canal en tiempo real del cambio de una solicitud de soporte.",
                noSePudo);
        }
    }

    // El nombre de las conexiones del dueno de una solicitud: el uid de un
    // jugador tal cual; el de un visitante, por la huella de su sesion.
    Optional<String> nombreEnElCanal(String propietario) {
        if (!IdentidadDelChat.esDeVisitante(propietario)) {
            return Optional.ofNullable(propietario);
        }
        String prefijo = IdentidadDelChat.PREFIJO_VISITANTE;
        try {
            UUID sesion = UUID.fromString(propietario.substring(prefijo.length()));
            return sesiones.findById(sesion).map(registrada -> prefijo + registrada.getHuella());
        } catch (IllegalArgumentException sinForma) {
            return Optional.empty();
        }
    }
}
