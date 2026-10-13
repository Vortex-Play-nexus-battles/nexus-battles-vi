package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedaccionDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Set;

// ms-chatbot.yaml 1.3.0 (7.4.3, RF-CHA-007): el jugador abre un ticket de
// soporte humano y consulta los suyos. Mismo orden que ChatService:
//
//   1. Solo jugadores con sesion (401 SESION_REQUERIDA).
//   2. Limite de frecuencia: la MISMA regla y el mismo cupo que los mensajes
//      (un ticket es un mensaje mas hacia el servicio). Ademas, solo puede
//      haber uno abierto a la vez, lo que ya acota el abuso.
//   3. Se redactan asunto y mensaje ANTES de todo lo demas: ni la lista negra
//      (otro servicio) ni la base ven nunca una contrasena o una tarjeta.
//   4. Lista negra sobre el asunto y el mensaje ya redactados (422 / 503),
//      antes de guardar nada.
//   5. Un solo ticket abierto por jugador (409 TICKET_ABIERTO). Lo garantiza
//      tambien el indice unico parcial de V6 si llegan dos a la vez; solo ese
//      error de la base es 409, los demas se relanzan (IndiceDeTicketAbierto).
//   6. Se guarda lo redactado, con el contexto de la conversacion tambien
//      redactado.
//
// No abre transaccion propia: la lista negra es HTTP y no debe retener una
// conexion de la base; cada lectura y el guardado usan la suya.
@Service
public class TicketSoporteService {

    private static final Set<EstadoTicket> ABIERTOS = Set.of(EstadoTicket.ABIERTO, EstadoTicket.EN_PROCESO);

    private final TicketSoporteRepository tickets;
    private final ConversacionRepository conversaciones;
    private final MensajeRepository mensajes;
    private final LimitadorDeFrecuencia limitador;
    private final ModeracionDeContenido moderacion;
    private final RedaccionDeDatosSensibles redaccion;
    private final Clock reloj;

    public TicketSoporteService(TicketSoporteRepository tickets, ConversacionRepository conversaciones,
                                MensajeRepository mensajes, LimitadorDeFrecuencia limitador,
                                ModeracionDeContenido moderacion, RedaccionDeDatosSensibles redaccion,
                                Clock reloj) {
        this.tickets = tickets;
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.limitador = limitador;
        this.moderacion = moderacion;
        this.redaccion = redaccion;
        this.reloj = reloj;
    }

    public TicketSoporte abrir(IdentidadDelChat identidad, Categoria categoria, String asunto, String mensaje) {
        exigirSesion(identidad);
        limitador.exigir(LimitadorDeFrecuencia.Regla.MENSAJES, identidad.claveDeLimite());
        String asuntoRedactado = redaccion.redactar(asunto.strip());
        String mensajeRedactado = redaccion.redactar(mensaje.strip());
        moderacion.verificar(asuntoRedactado + System.lineSeparator() + mensajeRedactado);

        if (tickets.existsByUidAndEstadoIn(identidad.uid(), ABIERTOS)) {
            throw new TicketAbiertoException();
        }

        TicketSoporte ticket = TicketSoporte.abrir(identidad.uid(), categoria, asuntoRedactado, mensajeRedactado,
            contextoDe(identidad), reloj.instant());
        try {
            return tickets.saveAndFlush(ticket);
        } catch (DataIntegrityViolationException error) {
            // Dos tickets a la vez: el indice unico parcial de V6 deja pasar
            // uno. Cualquier otro error de la base se relanza tal cual.
            if (IndiceDeTicketAbierto.loIncumple(error)) {
                throw new TicketAbiertoException();
            }
            throw error;
        }
    }

    public List<TicketSoporte> misTickets(IdentidadDelChat identidad) {
        exigirSesion(identidad);
        return tickets.findByUidOrderByCreadoEnDesc(identidad.uid());
    }

    private static void exigirSesion(IdentidadDelChat identidad) {
        if (identidad == null || !identidad.autenticado() || identidad.uid() == null) {
            throw new SesionRequeridaException();
        }
    }

    // Los mensajes de la conversacion actual, redactados. El ticket se queda
    // con los mas recientes (TicketSoporte.MAXIMO_DE_CONTEXTO).
    private List<MensajeDeContexto> contextoDe(IdentidadDelChat identidad) {
        return conversaciones.findByIdentificadorSesion(identidad.claveDeConversacion())
            .map(c -> mensajes.findByConversacionIdOrderByFechaEnvioAsc(c.getId()))
            .orElseGet(List::of)
            .stream()
            .map(this::comoContexto)
            .toList();
    }

    private MensajeDeContexto comoContexto(Mensaje mensaje) {
        return new MensajeDeContexto(mensaje.getRemitente().name(), redaccion.redactar(mensaje.getContenido()),
            mensaje.getFechaEnvio());
    }
}
