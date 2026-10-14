package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.ms_chatbot.chat.api.TicketResponse;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonimaRepository;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.soporte.EstadoTicket;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketActualizado;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 7.4.9 (contracts/websocket/chatbot.yaml 1.0.0): las piezas pequenas del canal.
@DisplayName("Chatbot · piezas del canal en tiempo real (7.4.9)")
class PiezasDelCanalTest {

    // ---------------------------------------------------------- AvisosDeSoporte

    @Test
    @DisplayName("el cambio de una solicitud va solo al uid de su dueno, con lo que ve en GET /chat/tickets")
    void elAvisoDeSoporteVaAlUidDelDueno() {
        SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
        TicketSoporte ticket = TicketSoporte.abrir("11111111-2222-4333-8444-555555555555", Categoria.SOPORTE_TECNICO,
            "No carga", "Se queda cargando", List.of(), Instant.parse("2026-10-14T10:00:00Z"));
        ticket.atender(EstadoTicket.RESUELTO, "Ya quedo", "admin-1", false, Instant.parse("2026-10-14T11:00:00Z"));

        new AvisosDeSoporte(mensajeria, mock(SesionAnonimaRepository.class)).alActualizarTicket(new TicketActualizado(ticket));

        verify(mensajeria).convertAndSendToUser("11111111-2222-4333-8444-555555555555", "/queue/soporte/tickets",
            TicketResponse.desde(ticket));
    }

    @Test
    @DisplayName("si el aviso no se puede mandar, el cambio del administrador no se entera")
    void unFalloAlAvisarNoLlegaAlAdministrador() {
        SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
        doThrow(new MessageDeliveryException("sin broker"))
            .when(mensajeria).convertAndSendToUser(anyString(), anyString(), any(Object.class));
        TicketSoporte ticket = TicketSoporte.abrir("uid-1", Categoria.SOPORTE_TECNICO, "A", "B", List.of(),
            Instant.parse("2026-10-14T10:00:00Z"));

        assertThatCode(() -> new AvisosDeSoporte(mensajeria, mock(SesionAnonimaRepository.class)).alActualizarTicket(new TicketActualizado(ticket)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1.12.0: la solicitud de un visitante va al nombre de sus conexiones, la huella de su sesion")
    void elAvisoDeUnVisitanteVaALaHuellaDeSuSesion() {
        SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
        SesionAnonimaRepository sesiones = mock(SesionAnonimaRepository.class);
        SesionAnonima sesion = new SesionAnonima("a".repeat(64), Instant.parse("2026-10-14T09:00:00Z"),
            Duration.ofHours(2));
        when(sesiones.findById(sesion.getId())).thenReturn(Optional.of(sesion));
        TicketSoporte ticket = TicketSoporte.abrir(sesion.claveDeConversacion(), Categoria.SOPORTE_TECNICO,
            "No carga", "Se queda cargando", List.of(), Instant.parse("2026-10-14T10:00:00Z"));

        new AvisosDeSoporte(mensajeria, sesiones).alActualizarTicket(new TicketActualizado(ticket));

        verify(mensajeria).convertAndSendToUser("anonimo:" + "a".repeat(64), "/queue/soporte/tickets",
            TicketResponse.desde(ticket));
    }

    @Test
    @DisplayName("1.12.0: sin la sesion del visitante (vencida y apartada) o con una clave sin forma, no se avisa a nadie")
    void sinSesionDelVisitanteNoSeAvisaANadie() {
        SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
        SesionAnonimaRepository sesiones = mock(SesionAnonimaRepository.class);
        when(sesiones.findById(any())).thenReturn(Optional.empty());
        AvisosDeSoporte avisos = new AvisosDeSoporte(mensajeria, sesiones);

        avisos.alActualizarTicket(new TicketActualizado(TicketSoporte.abrir("anonimo:" + UUID.randomUUID(),
            Categoria.SOPORTE_TECNICO, "A", "B", List.of(), Instant.parse("2026-10-14T10:00:00Z"))));
        avisos.alActualizarTicket(new TicketActualizado(TicketSoporte.abrir("anonimo:no-es-una-sesion",
            Categoria.SOPORTE_TECNICO, "A", "B", List.of(), Instant.parse("2026-10-14T10:00:00Z"))));

        verifyNoInteractions(mensajeria);
        assertThat(avisos.nombreEnElCanal(null)).isEmpty();
    }

    // ----------------------------------------------------- ErroresDelProtocolo

    @Test
    @DisplayName("el frame ERROR lleva el motivo de la politica y nunca el detalle interno de otra excepcion")
    void elFrameErrorLlevaElMotivoDeLaPolitica() {
        ErroresDelProtocolo errores = new ErroresDelProtocolo();
        StompHeaderAccessor cliente = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        cliente.setReceipt("r-7");
        Message<byte[]> suscripcion = MessageBuilder.createMessage(new byte[0], cliente.getMessageHeaders());

        Message<byte[]> rechazo = errores.handleClientMessageProcessingError(suscripcion,
            new MessageDeliveryException(suscripcion, "Failed to send message",
                new AccessDeniedException("Ese destino no existe en el canal del chatbot.")));
        StompHeaderAccessor frame = MessageHeaderAccessor.getAccessor(rechazo, StompHeaderAccessor.class);
        assertThat(frame.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(frame.getMessage()).isEqualTo("Ese destino no existe en el canal del chatbot.");
        assertThat(frame.getReceiptId()).isEqualTo("r-7");

        assertThat(ErroresDelProtocolo.motivo(new IllegalStateException("java.sql.SQLException: detalle interno")))
            .isEqualTo(ErroresDelProtocolo.GENERICO);
        assertThat(ErroresDelProtocolo.motivo(new AccessDeniedException(null))).isEqualTo(ErroresDelProtocolo.GENERICO);
    }

    // ---------------------------------------------------------- OrigenDelCanal

    @Test
    @DisplayName("el origen de la conexion es X-Real-IP del borde o, sin borde, la del socket")
    void elOrigenEsElDelBordeOElDelSocket() throws Exception {
        OrigenDelCanal origen = new OrigenDelCanal();
        ServerHttpRequest conBorde = mock(ServerHttpRequest.class);
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("X-Real-IP", " 203.0.113.9 ");
        when(conBorde.getHeaders()).thenReturn(cabeceras);
        Map<String, Object> atributos = new HashMap<>();

        assertThat(origen.beforeHandshake(conBorde, null, null, atributos)).isTrue();
        assertThat(atributos).containsEntry(OrigenDelCanal.ORIGEN, "203.0.113.9");
        assertThat(atributos.get(OrigenDelCanal.EN_VUELO)).isInstanceOf(AtomicInteger.class);

        ServerHttpRequest sinBorde = mock(ServerHttpRequest.class);
        when(sinBorde.getHeaders()).thenReturn(new HttpHeaders());
        when(sinBorde.getRemoteAddress()).thenReturn(new InetSocketAddress(InetAddress.getByName("198.51.100.4"), 5555));
        assertThat(OrigenDelCanal.origenDe(sinBorde)).isEqualTo("198.51.100.4");

        ServerHttpRequest sinNada = mock(ServerHttpRequest.class);
        when(sinNada.getHeaders()).thenReturn(new HttpHeaders());
        Map<String, Object> sinOrigen = new HashMap<>();
        origen.beforeHandshake(sinNada, null, null, sinOrigen);
        assertThat(sinOrigen).doesNotContainKey(OrigenDelCanal.ORIGEN).containsKey(OrigenDelCanal.EN_VUELO);
        assertThatCode(() -> origen.afterHandshake(sinNada, null, null, null)).doesNotThrowAnyException();
    }

    // ----------------------------------------------------- ChatEnVivoController

    @Test
    @DisplayName("el controlador entrega la pregunta con su conexion, y sin conexion identificada no hace nada")
    void elControladorEntregaLaPreguntaConSuConexion() {
        ConversacionEnVivo conversacion = mock(ConversacionEnVivo.class);
        ChatEnVivoController controlador = new ChatEnVivoController(conversacion);
        String uid = UUID.randomUUID().toString();
        Map<String, Object> atributos = new HashMap<>();
        SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.create();
        cabeceras.setUser(new UsuarioDelCanal(uid, true));
        cabeceras.setSessionId("sesion-9");
        cabeceras.setSessionAttributes(atributos);
        byte[] cuerpo = "{\"contenido\":\"hola\"}".getBytes();

        controlador.enviarMensaje(cuerpo, cabeceras);

        verify(conversacion).recibir(eq(cuerpo), eq(new ConversacionEnVivo.Destinatario(uid, "sesion-9")),
            eq(atributos));

        ConversacionEnVivo otra = mock(ConversacionEnVivo.class);
        new ChatEnVivoController(otra).enviarMensaje(cuerpo, SimpMessageHeaderAccessor.create());
        verifyNoInteractions(otra);
    }

    @Test
    @DisplayName("solo un Bearer con algo dentro es un token")
    void soloUnBearerConAlgoDentroEsUnToken() {
        assertThat(TokenDelCanal.deLaCabecera("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(TokenDelCanal.deLaCabecera("Bearer    ")).isNull();
        assertThat(TokenDelCanal.deLaCabecera("Basic abc")).isNull();
        assertThat(TokenDelCanal.deLaCabecera(null)).isNull();
    }
}
