package com.nexusbattles.ms_chatbot.chat.soporte;

import java.util.Objects;

/**
 * 7.4.9 — un administrador cambio una solicitud de soporte
 * ({@code PATCH /chatbot/admin/tickets/{ticketId}}). Lo publica
 * {@link TicketSoporteAdminService#atender} dentro de su transaccion; quien
 * avisa al jugador por el canal en tiempo real ({@code AvisosDeSoporte}) lo
 * escucha despues del commit, asi que nunca se anuncia un cambio que luego
 * se deshace.
 *
 * @param ticket la solicitud ya guardada (su {@code uid} es el dueno)
 */
public record TicketActualizado(TicketSoporte ticket) {

    public TicketActualizado {
        Objects.requireNonNull(ticket, "ticket");
    }
}
