package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** 1.3.7: los tickets abiertos en [desde, hasta), para las analiticas; sin datos del jugador. */
    @Query("""
        select new com.nexusbattles.ms_chatbot.chat.soporte.RegistroDeTicket(
            t.estado, t.categoria, t.creadoEn, t.actualizadoEn)
        from TicketSoporte t
        where t.creadoEn >= :desde and t.creadoEn < :hasta
        """)
    List<RegistroDeTicket> buscarCreadosEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
