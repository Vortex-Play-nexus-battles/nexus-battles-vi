package com.nexusbattles.ms_chatbot.chat.tiemporeal;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.ms_chatbot.chat.identidad.ResolutorDeIdentidad;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionAnonima;
import com.nexusbattles.ms_chatbot.chat.identidad.SesionesAnonimas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 7.4.9 (contracts/websocket/chatbot.yaml 1.0.0): quien entra al canal y que puede hacer.
@DisplayName("Chatbot · politica del canal en tiempo real (7.4.9)")
class PoliticaDelCanalTest {

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final SesionesAnonimas sesiones = mock(SesionesAnonimas.class);
    private final PoliticaDelCanal politica = new PoliticaDelCanal(
        new TokenDelCanal(emisor.decodificador(), new ConversorRolesJwt()), new ResolutorDeIdentidad(sesiones));
    private final MessageChannel canal = mock(MessageChannel.class);

    private static final String DECLARADA = "visitante-0123456789abcdef";

    private record Frame(Message<byte[]> mensaje, StompHeaderAccessor cabeceras, Map<String, Object> atributos) {
    }

    private static Frame frame(StompCommand comando, Map<String, String> nativas, String destino,
                               UsuarioDelCanal usuario) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(comando);
        nativas.forEach(cabeceras::setNativeHeader);
        if (destino != null) {
            cabeceras.setDestination(destino);
        }
        Map<String, Object> atributos = new ConcurrentHashMap<>();
        cabeceras.setSessionAttributes(atributos);
        cabeceras.setSessionId("sesion-1");
        if (usuario != null) {
            cabeceras.setUser(usuario);
        }
        cabeceras.setLeaveMutable(true);
        Message<byte[]> mensaje = MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
        return new Frame(mensaje, MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class), atributos);
    }

    private Frame connect(Map<String, String> nativas) {
        Frame frame = frame(StompCommand.CONNECT, nativas, null, null);
        politica.preSend(frame.mensaje(), canal);
        return frame;
    }

    private static UsuarioDelCanal usuario(Frame frame) {
        return (UsuarioDelCanal) frame.cabeceras().getUser();
    }

    // ------------------------------------------------------------------ CONNECT

    @Test
    void unTokenValidoConUidEsElJugadorYQuedaParaCadaMensaje() {
        UUID uid = UUID.randomUUID();
        String token = emisor.tokenDeJugador("ana", uid);

        Frame frame = connect(Map.of("Authorization", "Bearer " + token, "X-Id-Sesion-Anonima", DECLARADA));

        assertThat(usuario(frame)).isEqualTo(new UsuarioDelCanal(uid.toString(), true));
        assertThat(frame.atributos()).containsEntry(PoliticaDelCanal.TOKEN, token)
            .containsEntry(PoliticaDelCanal.SESION_ANONIMA, DECLARADA);
    }

    @Test
    void unTokenVencidoFirmadoPorOtroOSinUidDegradaAVisitanteComoEnLaApi() {
        UUID uid = UUID.randomUUID();
        when(sesiones.validar(DECLARADA)).thenReturn(Optional.empty());
        for (String token : new String[] {emisor.tokenCaducado("ana", uid), emisor.tokenFirmadoPorOtro("ana", uid),
            emisor.tokenDeServicio("ms-subastas"), "esto-no-es-un-jwt"}) {

            Frame frame = connect(Map.of("Authorization", "Bearer " + token, "X-Id-Sesion-Anonima", DECLARADA));

            assertThat(usuario(frame).jugador()).isFalse();
            assertThat(usuario(frame).getName()).startsWith("anonimo:").doesNotContain(DECLARADA);
            assertThat(frame.atributos()).doesNotContainKey(PoliticaDelCanal.TOKEN);
        }
    }

    @Test
    void unaSesionEmitidaVigenteEntraYUnaVencidaNo() {
        String vigente = "anon_" + "a".repeat(43);
        String vencida = "anon_" + "b".repeat(43);
        when(sesiones.validar(vigente)).thenReturn(Optional.of(
            new SesionAnonima("huella", Instant.parse("2026-10-14T10:00:00Z"), Duration.ofHours(24))));
        when(sesiones.validar(vencida)).thenReturn(Optional.empty());

        assertThat(usuario(connect(Map.of("X-Id-Sesion-Anonima", vigente))).jugador()).isFalse();
        assertThatThrownBy(() -> connect(Map.of("X-Id-Sesion-Anonima", vencida)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sinTokenNiSesionNoHayConexion() {
        when(sesiones.validar(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> connect(Map.of())).isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("X-Id-Sesion-Anonima");
        assertThatThrownBy(() -> connect(Map.of("Authorization", "Basic YWRtaW46YWRtaW4=")))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> connect(Map.of("Authorization", "Bearer   ")))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------- SUBSCRIBE

    @Test
    void soloSeSuscribeALosCuatroDestinosDeUsuarioDelContrato() {
        UsuarioDelCanal visitante = new UsuarioDelCanal("anonimo:huella", false);
        for (String destino : DestinosDelCanal.SUSCRIPCIONES) {
            Frame frame = frame(StompCommand.SUBSCRIBE, Map.of(), destino, visitante);
            assertThatCode(() -> politica.preSend(frame.mensaje(), canal)).as(destino).doesNotThrowAnyException();
        }
        for (String destino : new String[] {"/user/" + UUID.randomUUID() + "/queue/chat/respuestas",
            "/queue/chat/respuestas", "/queue/chat/respuestas-usersesion-1", "/topic/chat", "/user/queue/chat/**",
            "/user/queue/chat/respuestas/", "/app/chat/mensajes", null}) {
            Frame frame = frame(StompCommand.SUBSCRIBE, Map.of(), destino, visitante);
            assertThatThrownBy(() -> politica.preSend(frame.mensaje(), canal)).as(String.valueOf(destino))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Ese destino no existe en el canal del chatbot.");
        }
    }

    @Test
    void sinConexionIdentificadaNoSeSuscribeNiPregunta() {
        Frame suscripcion = frame(StompCommand.SUBSCRIBE, Map.of(), "/user/queue/chat/respuestas", null);
        Frame envio = frame(StompCommand.SEND, Map.of(), DestinosDelCanal.ENVIAR_MENSAJE, null);

        assertThatThrownBy(() -> politica.preSend(suscripcion.mensaje(), canal))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> politica.preSend(envio.mensaje(), canal)).isInstanceOf(AccessDeniedException.class);
    }

    // --------------------------------------------------------------------- SEND

    @Test
    void soloSePreguntaEnAppChatMensajes() {
        UsuarioDelCanal jugador = new UsuarioDelCanal(UUID.randomUUID().toString(), true);
        Frame valido = frame(StompCommand.SEND, Map.of(), DestinosDelCanal.ENVIAR_MENSAJE, jugador);
        assertThatCode(() -> politica.preSend(valido.mensaje(), canal)).doesNotThrowAnyException();

        for (String destino : new String[] {"/app/chat/otra-cosa", "/user/queue/chat/respuestas",
            "/queue/chat/respuestas", "/topic/x"}) {
            Frame frame = frame(StompCommand.SEND, Map.of(), destino, jugador);
            assertThatThrownBy(() -> politica.preSend(frame.mensaje(), canal)).as(destino)
                .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void losDemasFramesPasanSinCambios() {
        Frame desconexion = frame(StompCommand.DISCONNECT, Map.of(), null, null);
        assertThat(politica.preSend(desconexion.mensaje(), canal)).isSameAs(desconexion.mensaje());

        Message<byte[]> sinStomp = MessageBuilder.withPayload(new byte[0]).build();
        assertThat(politica.preSend(sinStomp, canal)).isSameAs(sinStomp);
    }
}
