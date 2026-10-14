package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.entrada.InyeccionDetectada;
import com.nexusbattles.ms_chatbot.chat.entrada.RevisionDeEntrada;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.limite.LimitadorDeFrecuencia;
import com.nexusbattles.ms_chatbot.chat.limite.LimiteDeFrecuenciaExcedido;
import com.nexusbattles.ms_chatbot.chat.model.Conversacion;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.moderacion.ContenidoBloqueado;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.privacidad.RedaccionDeDatosSensibles;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import com.nexusbattles.ms_chatbot.chat.service.PreguntaParaSoporte;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
            new RevisionDeEntrada(redaccion, new SimpleMeterRegistry()), Clock.fixed(AHORA, ZoneOffset.UTC),
            SIN_TRANSACCION);
    }

    // Ejecuta el bloque tal cual: aqui no hay base.
    private static final TransactionTemplate SIN_TRANSACCION = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> accion) {
            return accion.doInTransaction(null);
        }
    };

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

    // 1.12.0 (RF-CHA-007, actores Visitante y Jugador): un visitante tambien
    // abre solicitudes. Son de la clave de su conversacion ('anonimo:<sesion>'),
    // que ningun uid puede ocupar (V5).
    @Test
    void unVisitanteAbreSuSolicitudConLaClaveDeSuConversacion() {
        IdentidadDelChat visitante = IdentidadDelChat.visitante(
            new SesionAnonima("huella", AHORA, Duration.ofHours(24)));
        when(conversaciones.findByIdentificadorSesion(visitante.claveDeConversacion())).thenReturn(Optional.empty());
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrir(visitante, Categoria.FAQ_GENERAL, "Duda", "Una duda");

        assertThat(ticket.getUid()).isEqualTo(visitante.claveDeConversacion()).startsWith("anonimo:");
        verify(tickets).existsByUidAndEstadoIn(eq(visitante.claveDeConversacion()), anyCollection());
        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, visitante.claveDeLimite());
    }

    @Test
    void unJugadorSinUidNoEsDuenoDeNada() {
        IdentidadDelChat sinUid = new IdentidadDelChat(true, "clave", null, "token", null);

        assertThatThrownBy(() -> TicketSoporteService.propietario(sinUid))
            .isInstanceOf(SesionRequeridaException.class);
    }

    @Test
    void sinIdentidadNoSeAbreNada() {
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
    void sinIdentidadNoHaySolicitudesYLasDelVisitanteSonLasDeSuSesion() {
        assertThatThrownBy(() -> servicio.misTickets(null)).isInstanceOf(SesionRequeridaException.class);
        IdentidadDelChat visitante = IdentidadDelChat.visitante(
            new SesionAnonima("huella", AHORA, Duration.ofHours(24)));
        when(tickets.findByUidOrderByCreadoEnDesc(visitante.claveDeConversacion())).thenReturn(List.of());

        assertThat(servicio.misTickets(visitante)).isEmpty();
        verify(tickets).findByUidOrderByCreadoEnDesc(visitante.claveDeConversacion());
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

    // --- ms-chatbot.yaml 1.8.0: la solicitud desde la conversacion ----------

    private Conversacion conLaConversacion(Mensaje... conversacion) {
        Conversacion propia = new Conversacion(UID.toString(), true);
        when(conversaciones.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.of(propia));
        when(mensajes.findByConversacionIdOrderByFechaEnvioAsc(any())).thenReturn(List.of(conversacion));
        return propia;
    }

    private static Mensaje delJugador(String texto, int segundo) {
        return new Mensaje(null, Remitente.USUARIO, texto, null, AHORA.minusSeconds(100L - segundo));
    }

    private static Mensaje delBot(String texto, int segundo, ResultadoDeRespuesta resultado, Categoria categoria) {
        Mensaje bot = new Mensaje(null, Remitente.BOT, texto, null, AHORA.minusSeconds(100L - segundo));
        bot.registrarDatosDeRespuesta(null, categoria, resultado == ResultadoDeRespuesta.ESCALADO
            || resultado == ResultadoDeRespuesta.NO_SIRVIO, 10, resultado);
        return bot;
    }

    @Test
    void desdeLaConversacionTomaLaPreguntaQueNoEntendioYDeSuPropiaConversacion() {
        conLaConversacion(
            delJugador("como pujo", 1),
            delBot("Para pujar...", 2, ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO),
            delJugador("mi clave123 no sirve para el gremio", 3),
            delBot("No entendi tu pregunta.", 4, ResultadoDeRespuesta.ESCALADO, null));
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrirDesdeConversacion(jugador, VistaDelChat.INVENTARIO, null);

        // Sin tema que la respondiera: la categoria sale de la vista.
        assertThat(ticket.getCategoria()).isEqualTo(Categoria.PRODUCTO);
        assertThat(ticket.getAsunto())
            .isEqualTo("Sin respuesta del asistente: mi [REDACTADO] no sirve para el gremio");
        assertThat(ticket.getMensaje())
            .isEqualTo("Le pregunté al asistente: «mi [REDACTADO] no sirve para el gremio» y no la entendió.");
        assertThat(ticket.getUid()).isEqualTo(UID.toString());
        assertThat(ticket.getContexto()).hasSize(4);
        // La conversacion es la del uid del token: ningun dato del cliente la elige.
        verify(conversaciones).findByIdentificadorSesion(UID.toString());
        verify(limitador).exigir(LimitadorDeFrecuencia.Regla.MENSAJES, "usuario:" + UID);
        verify(moderacion).verificar(ticket.getAsunto() + System.lineSeparator() + ticket.getMensaje());
    }

    @Test
    void siNoLeSirvioLaPreguntaEsLaAnteriorYLaCategoriaLaDelTema() {
        conLaConversacion(
            delJugador("como recupero mi cuenta", 1),
            delBot("Para recuperarla...", 2, ResultadoDeRespuesta.TEMA, Categoria.CUENTA_Y_REGISTRO),
            delJugador("no me sirvio", 3),
            delBot("Siento que no sirviera.", 4, ResultadoDeRespuesta.NO_SIRVIO, Categoria.CUENTA_Y_REGISTRO));
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrirDesdeConversacion(jugador, VistaDelChat.SUBASTAS, "  Ya probe dos veces.  ");

        assertThat(ticket.getCategoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
        assertThat(ticket.getAsunto()).isEqualTo("Sin respuesta del asistente: como recupero mi cuenta");
        assertThat(ticket.getMensaje()).isEqualTo(
            "Le pregunté al asistente: «como recupero mi cuenta» y su respuesta no me sirvió.\n\nYa probe dos veces.");
    }

    @Test
    void sinNadaSinResolverEsLaUltimaPreguntaYSinVistaLaGeneral() {
        conLaConversacion(
            delJugador("hola", 1),
            delBot("Hola.", 2, ResultadoDeRespuesta.TEMA, null),
            delJugador("mas detalle", 3),
            delBot("Hola, con detalle.", 4, ResultadoDeRespuesta.TEMA, null));
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporte ticket = servicio.abrirDesdeConversacion(jugador, null, null);

        assertThat(ticket.getCategoria()).isEqualTo(Categoria.FAQ_GENERAL);
        assertThat(ticket.getAsunto()).isEqualTo("Sin respuesta del asistente: hola");
        assertThat(ticket.getMensaje()).endsWith("y necesito que me ayude una persona del equipo.");
    }

    @Test
    void sinPreguntasEnLaConversacionNoAbreNada() {
        when(conversaciones.findByIdentificadorSesion(UID.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.abrirDesdeConversacion(jugador, null, "algo"))
            .isInstanceOf(SinConversacionException.class);
        verifyNoInteractions(moderacion, tickets);
    }

    @Test
    void desdeLaConversacionSinIdentidadNoAbreNiLeeNada() {
        assertThatThrownBy(() -> servicio.abrirDesdeConversacion(null, null, null))
            .isInstanceOf(SesionRequeridaException.class);
        verifyNoInteractions(limitador, conversaciones, mensajes, moderacion, tickets);
    }

    // --- 1.12.0 (RF-CHA-007): la solicitud se abre sola ----------------------

    @Test
    void alNoResolverAbreLaSolicitudConLaPreguntaSinElLimiteDeFrecuencia() {
        conLaConversacion(
            delJugador("mi clave123 no sirve para el gremio", 1),
            delBot("No estoy seguro de haber entendido tu consulta.", 2, ResultadoDeRespuesta.ESCALADO, null));
        when(tickets.findFirstByUidAndEstadoInOrderByCreadoEnDesc(eq(UID.toString()), anyCollection()))
            .thenReturn(Optional.empty());
        when(tickets.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        TicketSoporteService.SolicitudAutomatica solicitud =
            servicio.alNoResolver(jugador, VistaDelChat.INVENTARIO).orElseThrow();

        assertThat(solicitud.nueva()).isTrue();
        assertThat(solicitud.ticket().getAsunto())
            .isEqualTo("Sin respuesta del asistente: mi [REDACTADO] no sirve para el gremio");
        assertThat(solicitud.ticket().getUid()).isEqualTo(UID.toString());
        assertThat(solicitud.ticket().getContexto()).hasSize(2);
        // El mensaje que la provoco ya paso el limite: no se cobra dos veces.
        verifyNoInteractions(limitador);
        verify(moderacion).verificar(anyString());
    }

    @Test
    void alNoResolverConUnaAbiertaSumaLaPreguntaASuContextoYNoAbreOtra() {
        conLaConversacion(
            delJugador("como pujo", 1),
            delBot("Para pujar...", 2, ResultadoDeRespuesta.TEMA, Categoria.SUBASTA_Y_COMERCIO),
            delJugador("y si mi clave123 falla", 3),
            delBot("No estoy seguro de haber entendido tu consulta.", 4, ResultadoDeRespuesta.ESCALADO, null));
        TicketSoporte abierta = TicketSoporte.abrir(UID.toString(), Categoria.FAQ_GENERAL, "Antes", "Antes",
            List.of(new MensajeDeContexto("USUARIO", "algo de antes", AHORA.minusSeconds(500))), AHORA.minusSeconds(500));
        when(tickets.findFirstByUidAndEstadoInOrderByCreadoEnDesc(eq(UID.toString()), anyCollection()))
            .thenReturn(Optional.of(abierta));
        when(tickets.save(abierta)).thenReturn(abierta);

        TicketSoporteService.SolicitudAutomatica solicitud = servicio.alNoResolver(jugador, null).orElseThrow();

        assertThat(solicitud.nueva()).isFalse();
        assertThat(solicitud.ticket()).isSameAs(abierta);
        assertThat(abierta.getContexto()).extracting(MensajeDeContexto::getContenido).containsExactly(
            "algo de antes", "y si mi [REDACTADO] falla", "No estoy seguro de haber entendido tu consulta.");
        assertThat(abierta.getActualizadoEn()).isEqualTo(AHORA);
        verify(tickets, never()).saveAndFlush(any());
        verifyNoInteractions(moderacion, limitador);
    }

    @Test
    void alNoResolverSiOtraLaAbreALaVezSeSumaAElla() {
        conLaConversacion(
            delJugador("una pregunta rara", 1),
            delBot("No estoy seguro de haber entendido tu consulta.", 2, ResultadoDeRespuesta.ESCALADO, null));
        TicketSoporte laOtra = TicketSoporte.abrir(UID.toString(), Categoria.FAQ_GENERAL, "Otra", "Otra", List.of(),
            AHORA);
        when(tickets.findFirstByUidAndEstadoInOrderByCreadoEnDesc(eq(UID.toString()), anyCollection()))
            .thenReturn(Optional.empty(), Optional.of(laOtra));
        when(tickets.existsByUidAndEstadoIn(eq(UID.toString()), anyCollection())).thenReturn(true);
        when(tickets.save(laOtra)).thenReturn(laOtra);

        TicketSoporteService.SolicitudAutomatica solicitud = servicio.alNoResolver(jugador, null).orElseThrow();

        assertThat(solicitud.nueva()).isFalse();
        assertThat(solicitud.ticket()).isSameAs(laOtra);
    }

    @Test
    void alNoResolverSinPreguntasNoAbreNada() {
        conLaConversacion();
        when(tickets.findFirstByUidAndEstadoInOrderByCreadoEnDesc(eq(UID.toString()), anyCollection()))
            .thenReturn(Optional.empty());

        assertThat(servicio.alNoResolver(jugador, null)).isEmpty();
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void alNoResolverSiLaListaNegraNoRespondeLoDiceAQuienLlama() {
        conLaConversacion(
            delJugador("una pregunta rara", 1),
            delBot("No estoy seguro de haber entendido tu consulta.", 2, ResultadoDeRespuesta.ESCALADO, null));
        when(tickets.findFirstByUidAndEstadoInOrderByCreadoEnDesc(eq(UID.toString()), anyCollection()))
            .thenReturn(Optional.empty());
        doThrow(new IllegalStateException("lista negra caida")).when(moderacion).verificar(anyString());

        assertThatThrownBy(() -> servicio.alNoResolver(jugador, null)).isInstanceOf(IllegalStateException.class);
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void desdeLaConversacionConUnTicketAbiertoEs409YPasaPorLaListaNegra() {
        conLaConversacion(delJugador("como pujo", 1),
            delBot("No entendi.", 2, ResultadoDeRespuesta.ESCALADO, null));
        when(tickets.existsByUidAndEstadoIn(eq(UID.toString()), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> servicio.abrirDesdeConversacion(jugador, null, null))
            .isInstanceOf(TicketAbiertoException.class);
        verify(moderacion).verificar(anyString());
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void elComentarioPasaPorLaRevisionYLaListaNegra() {
        conLaConversacion(delJugador("como pujo", 1),
            delBot("No entendi.", 2, ResultadoDeRespuesta.ESCALADO, null));
        doThrow(new ContenidoBloqueado()).when(moderacion).verificar(anyString());

        assertThatThrownBy(() -> servicio.abrirDesdeConversacion(jugador, null, "un insulto"))
            .isInstanceOf(ContenidoBloqueado.class);
        assertThatThrownBy(() -> servicio.abrirDesdeConversacion(jugador, null, "<script>alert(1)</script>"))
            .isInstanceOf(InyeccionDetectada.class);
        verifyNoInteractions(tickets);
    }

    @Test
    void laCategoriaSaleDelTemaDeLaVistaODeLaGeneral() {
        PreguntaParaSoporte conTema = new PreguntaParaSoporte("x", Categoria.MECANICA_JUEGO,
            PreguntaParaSoporte.Motivo.NO_LE_SIRVIO);
        PreguntaParaSoporte sinTema = new PreguntaParaSoporte("x", null, PreguntaParaSoporte.Motivo.NO_LA_ENTENDIO);

        assertThat(TicketSoporteService.categoriaDe(conTema, VistaDelChat.SUBASTAS)).isEqualTo(Categoria.MECANICA_JUEGO);
        assertThat(TicketSoporteService.categoriaDe(sinTema, VistaDelChat.SUBASTAS))
            .isEqualTo(Categoria.SUBASTA_Y_COMERCIO);
        assertThat(TicketSoporteService.categoriaDe(sinTema, VistaDelChat.INICIO)).isEqualTo(Categoria.FAQ_GENERAL);
        assertThat(TicketSoporteService.categoriaDe(sinTema, null)).isEqualTo(Categoria.FAQ_GENERAL);
    }
}
