package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ms-chatbot.yaml 1.3.0: reglas del ticket de soporte (7.4.3, RF-ADM-004).
class TicketSoporteTest {

    private static final Instant AL_ABRIR = Instant.parse("2026-09-28T18:00:00Z");
    private static final Instant DESPUES = Instant.parse("2026-09-28T19:00:00Z");

    private static TicketSoporte ticket() {
        return TicketSoporte.abrir("uid-1", Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando",
            List.of(new MensajeDeContexto("USUARIO", "hola", AL_ABRIR)), AL_ABRIR);
    }

    @Test
    void naceAbiertoSinRespuestaNiAsignado() {
        TicketSoporte ticket = ticket();

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.ABIERTO);
        assertThat(ticket.getRespuesta()).isNull();
        assertThat(ticket.getAsignadoA()).isNull();
        assertThat(ticket.getCreadoEn()).isEqualTo(AL_ABRIR);
        assertThat(ticket.getActualizadoEn()).isEqualTo(AL_ABRIR);
        assertThat(ticket.getContexto()).singleElement()
            .satisfies(m -> assertThat(m.getContenido()).isEqualTo("hola"));
    }

    @Test
    void guardaSoloLosVeinteMensajesMasRecientes() {
        List<MensajeDeContexto> conversacion = IntStream.rangeClosed(1, 25)
            .mapToObj(i -> new MensajeDeContexto("USUARIO", "m" + i, AL_ABRIR.plusSeconds(i)))
            .toList();

        TicketSoporte ticket = TicketSoporte.abrir("uid-1", Categoria.FAQ_GENERAL, "a", "b", conversacion, AL_ABRIR);

        assertThat(ticket.getContexto()).hasSize(TicketSoporte.MAXIMO_DE_CONTEXTO);
        assertThat(ticket.getContexto().get(0).getContenido()).isEqualTo("m6");
        assertThat(ticket.getContexto().get(19).getContenido()).isEqualTo("m25");
    }

    @Test
    void sinContextoQuedaVacio() {
        TicketSoporte ticket = TicketSoporte.abrir("uid-1", Categoria.FAQ_GENERAL, "a", "b", null, AL_ABRIR);

        assertThat(ticket.getContexto()).isEmpty();
    }

    @Test
    void resolverConRespuestaLaGuardaSinEspaciosSobrantes() {
        TicketSoporte ticket = ticket();

        ticket.atender(EstadoTicket.RESUELTO, "  Ya quedo.  ", null, false, DESPUES);

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.RESUELTO);
        assertThat(ticket.getRespuesta()).isEqualTo("Ya quedo.");
        assertThat(ticket.getActualizadoEn()).isEqualTo(DESPUES);
    }

    @Test
    void resolverSinRespuestaNoSePermite() {
        TicketSoporte ticket = ticket();

        assertThatThrownBy(() -> ticket.atender(EstadoTicket.RESUELTO, "   ", null, false, DESPUES))
            .isInstanceOf(TransicionNoPermitidaException.class)
            .hasMessageContaining("respuesta");
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.ABIERTO);
    }

    @Test
    void unaRespuestaAnteriorBastaParaResolverDespues() {
        TicketSoporte ticket = ticket();
        ticket.atender(EstadoTicket.EN_PROCESO, "Lo estamos revisando.", null, false, DESPUES);

        ticket.atender(EstadoTicket.RESUELTO, null, null, false, DESPUES);

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.RESUELTO);
        assertThat(ticket.getRespuesta()).isEqualTo("Lo estamos revisando.");
    }

    @Test
    void asignarYDesasignar() {
        TicketSoporte ticket = ticket();

        ticket.atender(null, null, " admin-7 ", false, DESPUES);
        assertThat(ticket.getAsignadoA()).isEqualTo("admin-7");
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.ABIERTO);

        ticket.atender(null, null, null, true, DESPUES);
        assertThat(ticket.getAsignadoA()).isNull();
    }

    @Test
    void unTicketCerradoYaNoCambia() {
        TicketSoporte ticket = ticket();
        ticket.atender(EstadoTicket.CERRADO, null, null, false, DESPUES);

        assertThatThrownBy(() -> ticket.atender(null, "algo", null, false, DESPUES))
            .isInstanceOf(TransicionNoPermitidaException.class)
            .hasMessageContaining("cerrado");
    }

    @Test
    void pedirElMismoEstadoNoEsUnaTransicion() {
        TicketSoporte ticket = ticket();

        ticket.atender(EstadoTicket.ABIERTO, null, null, false, DESPUES);

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.ABIERTO);
    }

    @ParameterizedTest
    @CsvSource({
        "ABIERTO, EN_PROCESO, true",
        "ABIERTO, RESUELTO, true",
        "ABIERTO, CERRADO, true",
        "EN_PROCESO, RESUELTO, true",
        "EN_PROCESO, CERRADO, true",
        "EN_PROCESO, ABIERTO, false",
        "RESUELTO, CERRADO, true",
        "RESUELTO, EN_PROCESO, true",
        "RESUELTO, ABIERTO, false",
        "CERRADO, ABIERTO, false",
        "CERRADO, EN_PROCESO, false"
    })
    void transicionesDelContrato(EstadoTicket desde, EstadoTicket hacia, boolean permitida) {
        assertThat(desde.puedePasarA(hacia)).isEqualTo(permitida);
    }

    @Test
    void enProcesoNoVuelveAAbierto() {
        TicketSoporte ticket = ticket();
        ticket.atender(EstadoTicket.EN_PROCESO, null, null, false, DESPUES);

        assertThatThrownBy(() -> ticket.atender(EstadoTicket.ABIERTO, null, null, false, DESPUES))
            .isInstanceOf(TransicionNoPermitidaException.class);
    }

    @Test
    void soloAbiertoYEnProcesoCuentanComoAbiertos() {
        assertThat(EstadoTicket.ABIERTO.abierto()).isTrue();
        assertThat(EstadoTicket.EN_PROCESO.abierto()).isTrue();
        assertThat(EstadoTicket.RESUELTO.abierto()).isFalse();
        assertThat(EstadoTicket.CERRADO.abierto()).isFalse();
    }
}
