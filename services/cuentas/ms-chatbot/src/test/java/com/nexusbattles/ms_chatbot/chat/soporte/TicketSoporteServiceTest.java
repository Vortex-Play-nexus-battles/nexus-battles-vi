package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedaccionDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.3.0 (7.4.3, RF-CHA-007): abrir y consultar tickets.
@ExtendWith(MockitoExtension.class)
class TicketSoporteServiceTest {

    private static final UUID UID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final Instant AHORA = Instant.parse("2026-09-28T18:00:00Z");

    @Mock
    private TicketSoporteRepository tickets;
    @Mock
    private ConversacionRepository conversaciones;
    @Mock
    private MensajeRepository mensajes;
    @Mock
    private LimitadorDeFrecuencia limitador;
    @Mock
    private ModeracionDeContenido moderacion;

    private TicketSoporteService servicio;
    private final IdentidadDelChat jugador = IdentidadDelChat.usuario(UID, "token");

    @BeforeEach
    void configurar() {
        // Redaccion de prueba que se nota: asi se comprueba que TODO lo que se
        // guarda paso por ella.
        RedaccionDeDatosSensibles redaccion = texto -> texto.replace("clave123", "[REDACTADO]");
        servicio = new TicketSoporteService(tickets, conversaciones, mensajes, limitador, moderacion, redaccion,
            Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void abreElTicketRedactadoYConElContextoDeLaConversacion() {
        Conversacion conversacion = new Conversacion(UID.toString(), true);
        when(conversaciones.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.of(conversacion));
        when(mensajes.findByConversacionIdOrderByFechaEnvioAsc(any())).thenReturn(List.of(
            new Mensaje(conversacion, Remitente.USUARIO, "mi clave es clave123", null, AHORA.minusSeconds(60)),
            new Mensaje(conversacion, Remitente.BOT, "No entendi tu pregunta.", null, AHORA.minusSeconds(59))));
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrir(jugador, Categoria.SOPORTE_TECNICO, "  No entro  ",
            "Probe con clave123 y nada");

        assertThat(ticket.getUid()).isEqualTo(UID.toString());
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicket.ABIERTO);
        assertThat(ticket.getAsunto()).isEqualTo("No entro");
        assertThat(ticket.getMensaje()).isEqualTo("Probe con [REDACTADO] y nada");
        assertThat(ticket.getCreadoEn()).isEqualTo(AHORA);
        assertThat(ticket.getContexto()).extracting(MensajeDeContexto::getContenido)
            .containsExactly("mi clave es [REDACTADO]", "No entendi tu pregunta.");
        assertThat(ticket.getContexto()).extracting(MensajeDeContexto::getRemitente)
            .containsExactly("USUARIO", "BOT");
        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:" + UID);
        // La lista negra (otro servicio) recibe el texto YA redactado.
        verify(moderacion).verificar("No entro" + System.lineSeparator() + "Probe con [REDACTADO] y nada");
    }

    @Test
    void sinConversacionPreviaAbreConElContextoVacio() {
        when(conversaciones.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.empty());
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrir(jugador, Categoria.FAQ_GENERAL, "Duda", "Una duda");

        assertThat(ticket.getContexto()).isEmpty();
    }

    @Test
    void unVisitanteNoPuedeAbrirTickets() {
        IdentidadDelChat visitante = IdentidadDelChat.visitante(
            new SesionAnonima("huella", AHORA, Duration.ofHours(24)));

        assertThatThrownBy(() -> servicio.abrir(visitante, Categoria.FAQ_GENERAL, "a", "b"))
            .isInstanceOf(SesionRequeridaException.class);
        assertThatThrownBy(() -> servicio.abrir(null, Categoria.FAQ_GENERAL, "a", "b"))
            .isInstanceOf(SesionRequeridaException.class);
        verifyNoInteractions(limitador, moderacion, tickets);
    }

    @Test
    void conUnTicketAbiertoNoDejaAbrirOtro() {
        when(tickets.existsByUidAndEstadoIn(eq(UID.toString()), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> servicio.abrir(jugador, Categoria.FAQ_GENERAL, "a", "b"))
            .isInstanceOf(TicketAbiertoException.class);
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void siDosLleganALaVezElIndiceUnicoDejaPasarUno() {
        when(conversaciones.findByIdentificadorSesion(anyString())).thenReturn(Optional.empty());
        when(tickets.saveAndFlush(any())).thenThrow(ErroresDeLaBase.violacionDelIndiceDeV6());

        assertThatThrownBy(() -> servicio.abrir(jugador, Categoria.FAQ_GENERAL, "a", "b"))
            .isInstanceOf(TicketAbiertoException.class);
    }

    // Revision de plataforma: solo el indice de V6 es 409. Un texto que no cabe
    // en su columna no es "ya tienes un ticket abierto".
    @Test
    void otroErrorDeLaBaseNoSeConvierteEnTicketAbierto() {
        when(conversaciones.findByIdentificadorSesion(anyString())).thenReturn(Optional.empty());
        DataIntegrityViolationException otroError = ErroresDeLaBase.textoMasLargoQueLaColumna();
        when(tickets.saveAndFlush(any())).thenThrow(otroError);

        assertThatThrownBy(() -> servicio.abrir(jugador, Categoria.FAQ_GENERAL, "a", "b")).isSameAs(otroError);
    }

    @Test
    void unTextoDeLaListaNegraNoGuardaNada() {
        doThrow(new ContenidoBloqueado()).when(moderacion).verificar(anyString());

        assertThatThrownBy(() -> servicio.abrir(jugador, Categoria.FAQ_GENERAL, "a", "b"))
            .isInstanceOf(ContenidoBloqueado.class);
        verifyNoInteractions(tickets);
    }

    @Test
    void elLimiteDeFrecuenciaSeRevisaAntesQueTodo() {
        doThrow(new LimiteDeFrecuenciaExcedido("espera", 30)).when(limitador).exigir(any(), anyString());

        assertThatThrownBy(() -> servicio.abrir(jugador, Categoria.FAQ_GENERAL, "a", "b"))
            .isInstanceOf(LimiteDeFrecuenciaExcedido.class);
        verifyNoInteractions(moderacion, tickets);
    }

    @Test
    void misTicketsSonLosDelUidDelToken() {
        TicketSoporte mio = TicketSoporte.abrir(UID.toString(), Categoria.FAQ_GENERAL, "a", "b", List.of(), AHORA);
        when(tickets.findByUidOrderByCreadoEnDesc(UID.toString())).thenReturn(List.of(mio));

        assertThat(servicio.misTickets(jugador)).containsExactly(mio);
    }

    @Test
    void unVisitanteNoTieneTickets() {
        assertThatThrownBy(() -> servicio.misTickets(null)).isInstanceOf(SesionRequeridaException.class);
        verifyNoInteractions(tickets);
    }

    @Test
    void guardaLoQueDevuelveElRepositorio() {
        when(conversaciones.findByIdentificadorSesion(anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<TicketSoporte> guardado = ArgumentCaptor.forClass(TicketSoporte.class);
        when(tickets.saveAndFlush(guardado.capture())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrir(jugador, Categoria.CUENTA_Y_REGISTRO, "a", "b");

        assertThat(ticket).isSameAs(guardado.getValue());
        assertThat(ticket.getCategoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
    }
}
