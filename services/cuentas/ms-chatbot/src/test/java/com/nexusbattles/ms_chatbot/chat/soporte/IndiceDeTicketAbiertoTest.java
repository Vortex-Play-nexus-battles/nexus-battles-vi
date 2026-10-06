package com.nexusbattles.ms_chatbot.chat.soporte;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

// Revision de plataforma (1.3.9): solo el indice unico de V6 es "ya hay un
// ticket abierto".
class IndiceDeTicketAbiertoTest {

    @Test
    void reconoceElIndiceDeV6EnLaCausa() {
        assertThat(IndiceDeTicketAbierto.loIncumple(ErroresDeLaBase.violacionDelIndiceDeV6())).isTrue();
    }

    @Test
    void reconoceElIndiceEnElMensajeDeSpring() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
            "could not execute statement; constraint \"uk_tickets_soporte_uno_abierto_por_jugador\"");

        assertThat(IndiceDeTicketAbierto.loIncumple(error)).isTrue();
    }

    @Test
    void unTextoMasLargoQueLaColumnaNoEsElIndice() {
        assertThat(IndiceDeTicketAbierto.loIncumple(ErroresDeLaBase.textoMasLargoQueLaColumna())).isFalse();
    }

    @Test
    void otraRestriccionNoEsElIndice() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("could not execute statement",
            new SQLException("ERROR: new row for relation \"tickets_soporte\" violates check constraint "
                + "\"chk_tickets_soporte_resuelto_con_respuesta\"", "23514"));

        assertThat(IndiceDeTicketAbierto.loIncumple(error)).isFalse();
    }

    @Test
    void unErrorSinCausaNiMensajeNoEsElIndice() {
        assertThat(IndiceDeTicketAbierto.loIncumple(new DataIntegrityViolationException(null))).isFalse();
    }
}
