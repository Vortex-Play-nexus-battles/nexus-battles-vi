package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;

import java.time.Instant;

// ms-chatbot.yaml 1.3.7: un ticket de soporte, reducido a lo que las
// analiticas usan (AnaliticaDeTickets). Sin uid, asunto ni mensaje.
public record RegistroDeTicket(EstadoTicket estado, Categoria categoria, Instant creadoEn, Instant actualizadoEn) {
}
