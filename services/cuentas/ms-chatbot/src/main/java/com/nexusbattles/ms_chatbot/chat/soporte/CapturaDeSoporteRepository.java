package com.nexusbattles.ms_chatbot.chat.soporte;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapturaDeSoporteRepository extends JpaRepository<CapturaDeSoporte, UUID> {

    // Las de varias solicitudes de una vez, sin su contenido (una consulta,
    // no una por solicitud).
    List<CapturaDeSoporte.Resumen> findByTicketIdInOrderBySubidaEnAsc(Collection<UUID> ticketIds);

    long countByTicketId(UUID ticketId);

    Optional<CapturaDeSoporte> findByIdAndTicketId(UUID id, UUID ticketId);
}
