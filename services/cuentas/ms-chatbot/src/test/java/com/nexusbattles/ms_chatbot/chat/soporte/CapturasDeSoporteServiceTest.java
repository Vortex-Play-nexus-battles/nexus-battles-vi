package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.14.0 (§7.4.6): quien adjunta y quien ve las capturas.
class CapturasDeSoporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID TICKET = UUID.fromString("6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f");

    private TicketSoporteRepository tickets;
    private CapturaDeSoporteRepository capturas;
    private LimitadorDeFrecuencia limitador;
    private CapturasDeSoporteService servicio;
    private IdentidadDelChat jugador;

    // Ejecuta el callback sin transaccion de verdad.
    private static final TransactionTemplate SIN_TRANSACCION = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> accion) {
            return accion.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @BeforeEach
    void preparar() {
        tickets = mock(TicketSoporteRepository.class);
        capturas = mock(CapturaDeSoporteRepository.class);
        limitador = mock(LimitadorDeFrecuencia.class);
        servicio = new CapturasDeSoporteService(tickets, capturas, limitador, Clock.fixed(AHORA, ZoneOffset.UTC),
            SIN_TRANSACCION);
        jugador = IdentidadDelChat.usuario(UID, "token");
        when(capturas.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TicketSoporte solicitud(String duena, EstadoTicket estado) {
        TicketSoporte ticket = TicketSoporte.abrir(duena, Categoria.SOPORTE_TECNICO, "No carga", "Se queda cargando",
            List.of(), AHORA);
        ReflectionTestUtils.setField(ticket, "id", TICKET);
        ReflectionTestUtils.setField(ticket, "estado", estado);
        return ticket;
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
    }

    // La solicitud tal como la ve la consulta y, despues, ya bloqueada.
    private void existe(TicketSoporte ticket) {
        when(tickets.findById(TICKET)).thenReturn(Optional.of(ticket));
        when(tickets.bloquear(TICKET)).thenReturn(Optional.of(ticket));
    }

    @Test
    void laDuenaAdjuntaUnaCapturaASuSolicitudAbierta() throws IOException {
        existe(solicitud(UID.toString(), EstadoTicket.EN_PROCESO));
        when(capturas.countByTicketId(TICKET)).thenReturn(2L);

        CapturaDeSoporte guardada = servicio.adjuntar(jugador, TICKET, png());

        assertThat(guardada.getTicketId()).isEqualTo(TICKET);
        assertThat(guardada.getTipo()).isEqualTo("image/png");
        assertThat(guardada.getAncho()).isEqualTo(20);
        assertThat(guardada.getAlto()).isEqualTo(10);
        assertThat(guardada.getSubidaEn()).isEqualTo(AHORA);
        assertThat(guardada.getTamano()).isEqualTo(guardada.getContenido().length);
        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, jugador.claveDeLimite());
        verify(tickets).bloquear(TICKET);
    }

    @Test
    void unVisitanteAdjuntaALaSuyaPorLaClaveDeSuConversacion() throws IOException {
        IdentidadDelChat visitante = IdentidadDelChat.visitante(new SesionAnonima("huella", AHORA, Duration.ofHours(24)));
        existe(solicitud(visitante.claveDeConversacion(), EstadoTicket.ABIERTO));

        assertThat(servicio.adjuntar(visitante, TICKET, png()).getTicketId()).isEqualTo(TICKET);
    }

    @Test
    void unaSolicitudAjenaOQueNoExisteResponde404Igual() throws IOException {
        byte[] captura = png();
        when(tickets.findById(TICKET)).thenReturn(Optional.of(solicitud("otra-persona", EstadoTicket.ABIERTO)));
        assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, captura))
            .isInstanceOf(TicketNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.propiaDe(jugador, TICKET, UUID.randomUUID()))
            .isInstanceOf(TicketNoEncontradoException.class);

        when(tickets.findById(TICKET)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, captura))
            .isInstanceOf(TicketNoEncontradoException.class);
        verify(capturas, never()).save(any());
    }

    @Test
    void aUnaSolicitudRespondidaOCerradaYaNoSeLeAdjuntaNada() throws IOException {
        byte[] captura = png();
        for (EstadoTicket cerrada : List.of(EstadoTicket.RESUELTO, EstadoTicket.CERRADO)) {
            when(tickets.findById(TICKET)).thenReturn(Optional.of(solicitud(UID.toString(), cerrada)));
            assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, captura))
                .isInstanceOf(TicketCerradoException.class);
        }
        verify(capturas, never()).save(any());
    }

    @Test
    void conTresYaNoCabeOtra() throws IOException {
        existe(solicitud(UID.toString(), EstadoTicket.ABIERTO));
        when(capturas.countByTicketId(TICKET)).thenReturn((long) CapturasDeSoporteService.MAXIMO_POR_SOLICITUD);

        assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, png()))
            .isInstanceOf(CapturasCompletasException.class).hasMessageContaining("3 capturas");
        verify(capturas, never()).save(any());
    }

    @Test
    void siLaRespondenOLaBorranMientrasSeExaminaNoSeGuardaNada() throws IOException {
        byte[] captura = png();
        when(tickets.findById(TICKET)).thenReturn(Optional.of(solicitud(UID.toString(), EstadoTicket.ABIERTO)));

        when(tickets.bloquear(TICKET)).thenReturn(Optional.of(solicitud(UID.toString(), EstadoTicket.RESUELTO)));
        assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, captura))
            .isInstanceOf(TicketCerradoException.class);

        when(tickets.bloquear(TICKET)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, captura))
            .isInstanceOf(TicketNoEncontradoException.class);
        verify(capturas, never()).save(any());
    }

    @Test
    void loQueNoEsUnaImagenNoLlegaALaBase() {
        when(tickets.findById(TICKET)).thenReturn(Optional.of(solicitud(UID.toString(), EstadoTicket.ABIERTO)));

        assertThatThrownBy(() -> servicio.adjuntar(jugador, TICKET, "<html>".getBytes()))
            .isInstanceOf(CapturaNoAdmitidaException.class);
        verify(capturas, never()).save(any());
    }

    @Test
    void sinIdentidadNiSiquieraSeBuscaLaSolicitud() {
        assertThatThrownBy(() -> servicio.adjuntar(null, TICKET, new byte[] {1}))
            .isInstanceOf(SesionRequeridaException.class);
        verifyNoInteractions(tickets, capturas, limitador);
    }

    @Test
    void laDuenaLeeLaSuyaYElAdministradorCualquiera() throws IOException {
        CapturaDeSoporte captura = new CapturaDeSoporte(TICKET,
            new ExaminadorDeCapturas().examinar(png()), AHORA);
        ReflectionTestUtils.setField(captura, "id", UUID.fromString("7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d"));
        when(tickets.findById(TICKET)).thenReturn(Optional.of(solicitud(UID.toString(), EstadoTicket.RESUELTO)));
        when(capturas.findByIdAndTicketId(captura.getId(), TICKET)).thenReturn(Optional.of(captura));

        assertThat(servicio.propiaDe(jugador, TICKET, captura.getId())).isSameAs(captura);
        assertThat(servicio.deCualquiera(TICKET, captura.getId())).isSameAs(captura);
        assertThatThrownBy(() -> servicio.deCualquiera(TICKET, UUID.randomUUID()))
            .isInstanceOf(TicketNoEncontradoException.class);
    }

    @Test
    void lasDeVariasSolicitudesSeAgrupanSinPedirSuContenido() {
        UUID otra = UUID.randomUUID();
        CapturaDeSoporte.Resumen a = resumen(TICKET);
        CapturaDeSoporte.Resumen b = resumen(otra);
        CapturaDeSoporte.Resumen c = resumen(TICKET);
        when(capturas.findByTicketIdInOrderBySubidaEnAsc(List.of(TICKET, otra))).thenReturn(List.of(a, b, c));

        Map<UUID, List<CapturaDeSoporte.Resumen>> porSolicitud = servicio.deLasSolicitudes(List.of(TICKET, otra));

        assertThat(porSolicitud.get(TICKET)).containsExactly(a, c);
        assertThat(porSolicitud.get(otra)).containsExactly(b);
        assertThat(servicio.deLasSolicitudes(List.of())).isEmpty();
        verify(capturas, never()).findByTicketIdInOrderBySubidaEnAsc(List.of());
    }

    private static CapturaDeSoporte.Resumen resumen(UUID ticket) {
        CapturaDeSoporte.Resumen resumen = mock(CapturaDeSoporte.Resumen.class);
        when(resumen.getTicketId()).thenReturn(ticket);
        return resumen;
    }
}
