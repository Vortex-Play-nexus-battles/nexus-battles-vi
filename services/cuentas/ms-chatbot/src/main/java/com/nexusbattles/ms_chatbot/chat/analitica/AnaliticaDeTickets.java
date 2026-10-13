package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.ResumenDeTickets;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TicketsPorCategoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.EstadoTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.RegistroDeTicket;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ms-chatbot.yaml 1.3.7 (7.4.7, RF-CHA-007): resumen de los tickets de
// soporte abiertos en el periodo. Sin estado.
//
//   por estado      cuantos siguen abiertos, en revision, respondidos o cerrados;
//   por categoria   de que temas piden ayuda humana, la mas pedida primero;
//   horas promedio  entre que se abre un ticket y su ultima actualizacion,
//                   solo de los respondidos o cerrados; null si no hay ninguno
//                   ("sin datos" no es 0 horas).
final class AnaliticaDeTickets {

    private static final double SEGUNDOS_POR_HORA = 3600.0;

    private AnaliticaDeTickets() {
    }

    static ResumenDeTickets resumir(List<RegistroDeTicket> tickets) {
        Map<EstadoTicket, Long> porEstado = new EnumMap<>(EstadoTicket.class);
        for (RegistroDeTicket ticket : tickets) {
            porEstado.merge(ticket.estado(), 1L, Long::sum);
        }

        Map<Categoria, Long> porCategoria = tickets.stream()
            .collect(Collectors.groupingBy(RegistroDeTicket::categoria, () -> new EnumMap<>(Categoria.class),
                Collectors.counting()));
        List<TicketsPorCategoria> categorias = porCategoria.entrySet().stream()
            .map(e -> new TicketsPorCategoria(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(TicketsPorCategoria::tickets).reversed()
                .thenComparing(t -> t.categoria().name()))
            .toList();

        return new ResumenDeTickets(
            tickets.size(),
            porEstado.getOrDefault(EstadoTicket.ABIERTO, 0L),
            porEstado.getOrDefault(EstadoTicket.EN_PROCESO, 0L),
            porEstado.getOrDefault(EstadoTicket.RESUELTO, 0L),
            porEstado.getOrDefault(EstadoTicket.CERRADO, 0L),
            horasPromedioDeAtencion(tickets),
            categorias);
    }

    private static Double horasPromedioDeAtencion(List<RegistroDeTicket> tickets) {
        return tickets.stream()
            .filter(t -> t.estado() == EstadoTicket.RESUELTO || t.estado() == EstadoTicket.CERRADO)
            .filter(t -> t.creadoEn() != null && t.actualizadoEn() != null)
            .map(t -> Duration.between(t.creadoEn(), t.actualizadoEn()))
            .mapToDouble(d -> Math.max(0, d.getSeconds()) / SEGUNDOS_POR_HORA)
            .average()
            .stream().boxed().findFirst().orElse(null);
    }
}
