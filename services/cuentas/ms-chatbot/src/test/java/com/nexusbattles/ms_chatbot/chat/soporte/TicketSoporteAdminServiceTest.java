package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.3.0 (RF-ADM-004): la bandeja del administrador.
@ExtendWith(MockitoExtension.class)
class TicketSoporteAdminServiceTest {

    private static final Instant AL_ABRIR = Instant.parse("2026-09-28T18:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-28T20:00:00Z");

    @Mock
    private TicketSoporteRepository tickets;

    private TicketSoporteAdminService servicio;

    @BeforeEach
    void configurar() {
        servicio = new TicketSoporteAdminService(tickets, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static TicketSoporte ticket() {
        return TicketSoporte.abrir("uid-1", Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando",
            List.of(new MensajeDeContexto("USUARIO", "hola", AL_ABRIR)), AL_ABRIR);
    }

    @Test
    void listarSinEstadoTraeTodosLosMasRecientesPrimero() {
        Page<TicketSoporte> pagina = new PageImpl<>(List.of(ticket()));
        ArgumentCaptor<Pageable> orden = ArgumentCaptor.forClass(Pageable.class);
        when(tickets.findAll(orden.capture())).thenReturn(pagina);

        assertThat(servicio.listar(null, 0, 20)).isSameAs(pagina);
        assertThat(orden.getValue().getPageSize()).isEqualTo(20);
        assertThat(orden.getValue().getSort().getOrderFor("creadoEn").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listarPorEstadoYConLimitesSaneados() {
        ArgumentCaptor<Pageable> orden = ArgumentCaptor.forClass(Pageable.class);
        when(tickets.findByEstado(eq(EstadoTicket.ABIERTO), orden.capture())).thenReturn(Page.empty());

        servicio.listar(EstadoTicket.ABIERTO, -3, 500);

        assertThat(orden.getValue().getPageNumber()).isZero();
        assertThat(orden.getValue().getPageSize()).isEqualTo(TicketSoporteAdminService.TAMANO_MAXIMO);
    }

    @Test
    void obtenerDevuelveElTicketConSuContexto() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.of(ticket()));

        TicketSoporte ticket = servicio.obtener(id);

        assertThat(ticket.getContexto()).hasSize(1);
    }

    @Test
    void unTicketQueNoExisteEs404() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> servicio.atender(id, EstadoTicket.CERRADO, null, null, false))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void atenderAplicaElCambioConLaHoraActualYLoGuarda() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.of(ticket()));
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte atendido = servicio.atender(id, EstadoTicket.RESUELTO, "Ya quedo", "admin-1", false);

        assertThat(atendido.getEstado()).isEqualTo(EstadoTicket.RESUELTO);
        assertThat(atendido.getRespuesta()).isEqualTo("Ya quedo");
        assertThat(atendido.getAsignadoA()).isEqualTo("admin-1");
        assertThat(atendido.getActualizadoEn()).isEqualTo(AHORA);
    }

    // 1.3.9: el jugador solo puede tener un ticket abierto (indice de V6).
    private static TicketSoporte resueltoConId(UUID id) {
        TicketSoporte resuelto = ticket();
        resuelto.atender(EstadoTicket.RESUELTO, "Ya quedo", null, false, AL_ABRIR);
        ReflectionTestUtils.setField(resuelto, "id", id);
        return resuelto;
    }

    @Test
    void reabrirConOtroTicketAbiertoEs409YNoGuardaNada() {
        UUID id = UUID.randomUUID();
        TicketSoporte resuelto = resueltoConId(id);
        when(tickets.findById(id)).thenReturn(Optional.of(resuelto));
        when(tickets.existsByUidAndEstadoInAndIdNot(eq("uid-1"), anyCollection(), eq(id))).thenReturn(true);

        assertThatThrownBy(() -> servicio.atender(id, EstadoTicket.EN_PROCESO, null, null, false))
            .isInstanceOf(OtroTicketAbiertoException.class);

        assertThat(resuelto.getEstado()).isEqualTo(EstadoTicket.RESUELTO);
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void reabrirSinOtroAbiertoLoPasaAEnProceso() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.of(resueltoConId(id)));
        when(tickets.existsByUidAndEstadoInAndIdNot(eq("uid-1"), anyCollection(), eq(id))).thenReturn(false);
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        assertThat(servicio.atender(id, EstadoTicket.EN_PROCESO, null, null, false).getEstado())
            .isEqualTo(EstadoTicket.EN_PROCESO);
    }

    // Si el jugador abre otro justo entre la revision y el guardado, la base
    // lo frena con el indice unico: tambien es 409, nunca un 500.
    @Test
    void siElIndiceUnicoLoFrenaAlGuardarTambienEs409() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.of(resueltoConId(id)));
        when(tickets.existsByUidAndEstadoInAndIdNot(eq("uid-1"), anyCollection(), eq(id))).thenReturn(false);
        when(tickets.saveAndFlush(any())).thenThrow(ErroresDeLaBase.violacionDelIndiceDeV6());

        assertThatThrownBy(() -> servicio.atender(id, EstadoTicket.EN_PROCESO, null, null, false))
            .isInstanceOf(OtroTicketAbiertoException.class);
    }

    // Revision de plataforma: una respuesta mas larga que la columna no sale
    // como "el jugador ya tiene otro ticket abierto".
    @Test
    void otroErrorDeLaBaseAlGuardarSeRelanzaTalCual() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.of(resueltoConId(id)));
        when(tickets.existsByUidAndEstadoInAndIdNot(eq("uid-1"), anyCollection(), eq(id))).thenReturn(false);
        DataIntegrityViolationException otroError = ErroresDeLaBase.textoMasLargoQueLaColumna();
        when(tickets.saveAndFlush(any())).thenThrow(otroError);

        assertThatThrownBy(() -> servicio.atender(id, EstadoTicket.EN_PROCESO, null, null, false))
            .isSameAs(otroError);
    }

    @Test
    void unCambioQueNoReabreNoConsultaOtrosTickets() {
        UUID id = UUID.randomUUID();
        when(tickets.findById(id)).thenReturn(Optional.of(ticket()));
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.atender(id, EstadoTicket.EN_PROCESO, null, null, false);

        verify(tickets, never()).existsByUidAndEstadoInAndIdNot(any(), any(), any());
    }

    @Test
    void unaTransicionInvalidaNoGuardaNada() {
        UUID id = UUID.randomUUID();
        TicketSoporte cerrado = ticket();
        cerrado.atender(EstadoTicket.CERRADO, null, null, false, AL_ABRIR);
        when(tickets.findById(id)).thenReturn(Optional.of(cerrado));

        assertThatThrownBy(() -> servicio.atender(id, EstadoTicket.EN_PROCESO, null, null, false))
            .isInstanceOf(TransicionNoPermitidaException.class);
        verify(tickets, never()).saveAndFlush(any());
    }
}
