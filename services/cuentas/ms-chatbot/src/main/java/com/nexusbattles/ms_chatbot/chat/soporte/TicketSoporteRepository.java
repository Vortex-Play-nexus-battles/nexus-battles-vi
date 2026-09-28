package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TicketSoporteRepository extends JpaRepository<TicketSoporte, UUID> {

    /** "Mis tickets" (GET /chat/tickets). */
    List<TicketSoporte> findByUidOrderByCreadoEnDesc(String uid);

    /** Para responder 409 TICKET_ABIERTO antes de chocar con el indice unico. */
    boolean existsByUidAndEstadoIn(String uid, Collection<EstadoTicket> estados);

    /** Bandeja del administrador filtrada por estado. */
    Page<TicketSoporte> findByEstado(EstadoTicket estado, Pageable pagina);
}
