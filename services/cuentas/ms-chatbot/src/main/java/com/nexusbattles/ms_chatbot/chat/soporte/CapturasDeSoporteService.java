package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ms-chatbot.yaml 1.14.0 (§7.4.6 «Opcion de adjuntar capturas de pantalla para
 * soporte tecnico»): las capturas de una solicitud de soporte.
 *
 * <ul>
 *   <li><b>De quien</b>: solo quien abrio la solicitud (el uid de su token o,
 *       si es un visitante, la clave de su conversacion; RF-CHA-007) adjunta y
 *       ve las suyas; un administrador las ve todas. A cualquier otro, la
 *       solicitud ajena le responde 404 igual que una que no existe.</li>
 *   <li><b>Cuando</b>: mientras la solicitud esta abierta (ABIERTO o
 *       EN_PROCESO); despues, 409 SOLICITUD_CERRADA.</li>
 *   <li><b>Cuantas y como</b>: tres por solicitud, PNG o JPEG de hasta 2 MB y
 *       4096 pixeles por lado, sin metadatos (ExaminadorDeCapturas, D-CHA-12).
 *       Adjuntar pasa por el mismo limite de frecuencia que un mensaje.</li>
 * </ul>
 *
 * <p>Una captura no se puede redactar como un texto: la ventana avisa antes de
 * subirla que no muestre contraseñas ni tarjetas, y solo la ven su dueño y
 * soporte.
 */
@Service
public class CapturasDeSoporteService {

    /** D-CHA-12: capturas por solicitud. */
    public static final int MAXIMO_POR_SOLICITUD = 3;

    private static final Set<EstadoTicket> ABIERTOS = Set.of(EstadoTicket.ABIERTO, EstadoTicket.EN_PROCESO);

    private final TicketSoporteRepository tickets;
    private final CapturaDeSoporteRepository capturas;
    private final LimitadorDeFrecuencia limitador;
    private final ExaminadorDeCapturas examinador;
    private final Clock reloj;
    private final TransactionTemplate transacciones;

    public CapturasDeSoporteService(TicketSoporteRepository tickets, CapturaDeSoporteRepository capturas,
                                    LimitadorDeFrecuencia limitador, Clock reloj,
                                    TransactionTemplate transacciones) {
        this.tickets = tickets;
        this.capturas = capturas;
        this.limitador = limitador;
        this.examinador = new ExaminadorDeCapturas();
        this.reloj = reloj;
        this.transacciones = transacciones;
    }

    /**
     * Adjunta una captura a una solicitud propia y abierta.
     *
     * @throws SesionRequeridaException        401 sin identidad
     * @throws TicketNoEncontradoException     404 si no existe o no es suya
     * @throws TicketCerradoException          409 si ya se respondio o cerro
     * @throws CapturasCompletasException      409 si ya tiene tres
     * @throws CapturaNoAdmitidaException      415 si no es un PNG o JPEG bien formado
     * @throws CapturaDemasiadoGrandeException 413 si pesa o mide demasiado
     */
    public CapturaDeSoporte adjuntar(IdentidadDelChat identidad, UUID ticketId, byte[] archivo) {
        String propietario = TicketSoporteService.propietario(identidad);
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, identidad.claveDeLimite());
        abierta(propia(propietario, ticketId));
        // Se examina fuera de la transaccion: no retiene una conexion.
        ExaminadorDeCapturas.CapturaExaminada examinada = examinador.examinar(archivo);
        return transacciones.execute(estado -> {
            // Con la solicitud bloqueada, otra vez: un administrador pudo
            // responderla o cerrarla mientras se examinaba la captura, y dos
            // subidas a la vez no pasan de tres.
            abierta(tickets.bloquear(ticketId).orElseThrow(TicketNoEncontradoException::new));
            if (capturas.countByTicketId(ticketId) >= MAXIMO_POR_SOLICITUD) {
                throw new CapturasCompletasException();
            }
            return capturas.save(new CapturaDeSoporte(ticketId, examinada, reloj.instant()));
        });
    }

    private static void abierta(TicketSoporte ticket) {
        if (!ABIERTOS.contains(ticket.getEstado())) {
            throw new TicketCerradoException();
        }
    }

    /** Una captura de una solicitud propia (404 si no existe o no es suya). */
    public CapturaDeSoporte propiaDe(IdentidadDelChat identidad, UUID ticketId, UUID capturaId) {
        propia(TicketSoporteService.propietario(identidad), ticketId);
        return capturas.findByIdAndTicketId(capturaId, ticketId).orElseThrow(TicketNoEncontradoException::new);
    }

    /** Para el panel: cualquier captura de cualquier solicitud (404 si no existe). */
    public CapturaDeSoporte deCualquiera(UUID ticketId, UUID capturaId) {
        return capturas.findByIdAndTicketId(capturaId, ticketId).orElseThrow(TicketNoEncontradoException::new);
    }

    /** Las de varias solicitudes, sin su contenido y en orden de subida. */
    public Map<UUID, List<CapturaDeSoporte.Resumen>> deLasSolicitudes(Collection<UUID> ticketIds) {
        Map<UUID, List<CapturaDeSoporte.Resumen>> porSolicitud = new LinkedHashMap<>();
        if (ticketIds.isEmpty()) {
            return porSolicitud;
        }
        for (CapturaDeSoporte.Resumen resumen : capturas.findByTicketIdInOrderBySubidaEnAsc(ticketIds)) {
            porSolicitud.computeIfAbsent(resumen.getTicketId(), clave -> new ArrayList<>()).add(resumen);
        }
        return porSolicitud;
    }

    private TicketSoporte propia(String propietario, UUID ticketId) {
        return tickets.findById(ticketId)
            .filter(ticket -> propietario.equals(ticket.getUid()))
            .orElseThrow(TicketNoEncontradoException::new);
    }
}
