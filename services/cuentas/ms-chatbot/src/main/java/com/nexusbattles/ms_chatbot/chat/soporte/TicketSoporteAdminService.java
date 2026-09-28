package com.nexusbattles.ms_chatbot.chat.soporte;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.UUID;

// ms-chatbot.yaml 1.3.0 (RF-ADM-004): el administrador lista, lee y atiende
// los tickets de soporte. La ruta (/chatbot/admin/**) ya exige rol
// ADMINISTRADOR o SUPER_ADMINISTRADOR en SecurityConfig.
@Service
public class TicketSoporteAdminService {

    static final int TAMANO_MAXIMO = 100;
    private static final String NO_ENCONTRADO = "No existe un ticket con ese identificador.";

    private final TicketSoporteRepository tickets;
    private final Clock reloj;

    public TicketSoporteAdminService(TicketSoporteRepository tickets, Clock reloj) {
        this.tickets = tickets;
        this.reloj = reloj;
    }

    /** Una pagina, los mas recientes primero; con {@code estado} null, todos. */
    @Transactional(readOnly = true)
    public Page<TicketSoporte> listar(EstadoTicket estado, int pagina, int tamano) {
        Pageable orden = PageRequest.of(Math.max(0, pagina), Math.clamp(tamano, 1, TAMANO_MAXIMO),
            Sort.by(Sort.Direction.DESC, "creadoEn"));
        return estado == null ? tickets.findAll(orden) : tickets.findByEstado(estado, orden);
    }

    /** El ticket con su contexto ya cargado (se lee fuera de la transaccion). */
    @Transactional(readOnly = true)
    public TicketSoporte obtener(UUID ticketId) {
        TicketSoporte ticket = buscar(ticketId);
        Hibernate.initialize(ticket.getContexto());
        return ticket;
    }

    /**
     * Aplica el PATCH: estado, respuesta y asignacion (solo lo que viene).
     *
     * @throws TransicionNoPermitidaException 409 si el estado no lo permite.
     */
    @Transactional
    public TicketSoporte atender(UUID ticketId, EstadoTicket estado, String respuesta, String asignadoA,
                                 boolean desasignar) {
        TicketSoporte ticket = buscar(ticketId);
        ticket.atender(estado, respuesta, asignadoA, desasignar, reloj.instant());
        TicketSoporte guardado = tickets.saveAndFlush(ticket);
        Hibernate.initialize(guardado.getContexto());
        return guardado;
    }

    private TicketSoporte buscar(UUID ticketId) {
        return tickets.findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NO_ENCONTRADO));
    }
}
