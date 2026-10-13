package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.ResumenDeTickets;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.EstadoTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.RegistroDeTicket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.3.7 (7.4.7): resumen de tickets de soporte del periodo.
class AnaliticaDeTicketsTest {

    private static final Instant ABIERTO_EN = Instant.parse("2026-09-09T10:00:00Z");

    private static RegistroDeTicket ticket(EstadoTicket estado, Categoria categoria, int horasHastaActualizar) {
        return new RegistroDeTicket(estado, categoria, ABIERTO_EN, ABIERTO_EN.plusSeconds(horasHastaActualizar * 3600L));
    }

    @Test
    void cuentaPorEstadoYPorCategoria_laMasPedidaPrimero() {
        ResumenDeTickets resumen = AnaliticaDeTickets.resumir(List.of(
            ticket(EstadoTicket.ABIERTO, Categoria.SOPORTE_TECNICO, 0),
            ticket(EstadoTicket.EN_PROCESO, Categoria.SOPORTE_TECNICO, 1),
            ticket(EstadoTicket.RESUELTO, Categoria.SUBASTA_Y_COMERCIO, 2),
            ticket(EstadoTicket.CERRADO, Categoria.SOPORTE_TECNICO, 4)));

        assertThat(resumen.total()).isEqualTo(4);
        assertThat(resumen.abiertos()).isEqualTo(1);
        assertThat(resumen.enProceso()).isEqualTo(1);
        assertThat(resumen.resueltos()).isEqualTo(1);
        assertThat(resumen.cerrados()).isEqualTo(1);
        assertThat(resumen.porCategoria()).extracting(t -> t.categoria())
            .containsExactly(Categoria.SOPORTE_TECNICO, Categoria.SUBASTA_Y_COMERCIO);
        assertThat(resumen.porCategoria().get(0).tickets()).isEqualTo(3);
    }

    // Solo los respondidos o cerrados cuentan para el tiempo de atencion.
    @Test
    void horasPromedio_soloDeLosRespondidosOCerrados() {
        ResumenDeTickets resumen = AnaliticaDeTickets.resumir(List.of(
            ticket(EstadoTicket.ABIERTO, Categoria.FAQ_GENERAL, 100),
            ticket(EstadoTicket.RESUELTO, Categoria.FAQ_GENERAL, 2),
            ticket(EstadoTicket.CERRADO, Categoria.FAQ_GENERAL, 4)));

        assertThat(resumen.horasPromedioDeAtencion()).isEqualTo(3.0);
    }

    @Test
    void sinTickets_todoEnCeroYSinHorasPromedio() {
        ResumenDeTickets resumen = AnaliticaDeTickets.resumir(List.of());

        assertThat(resumen).isEqualTo(ResumenDeTickets.VACIO);
        assertThat(resumen.horasPromedioDeAtencion()).isNull();
    }
}
