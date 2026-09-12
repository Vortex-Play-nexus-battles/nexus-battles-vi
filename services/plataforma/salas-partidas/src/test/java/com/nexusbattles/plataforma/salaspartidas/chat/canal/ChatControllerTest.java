package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.ContenidoBloqueado;
import com.nexusbattles.plataforma.salaspartidas.chat.EnviarMensaje;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class ChatControllerTest {

    private static final UUID JUGADOR = UUID.randomUUID();
    private static final UUID SALA = UUID.randomUUID();

    private final EnviarMensaje enviar = mock(EnviarMensaje.class);
    private final HistorialDeChat historial = mock(HistorialDeChat.class);
    private final ChatController controlador = new ChatController(enviar, historial, 2);

    private static Principal jugador() {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none")
                .subject(JUGADOR.toString()).claim("preferred_username", "Ana").build());
    }

    @Test
    @DisplayName("el envio a una sala lleva el canal de la sala y el autor sacado del token")
    void envioASala() {
        controlador.enviarASala(SALA, new EnviarMensajeRequest("hola", null), jugador());

        verify(enviar).enviar(Canal.deSala(SALA), new Autor(JUGADOR, "Ana"), "hola", null);
    }

    @Test
    @DisplayName("el envio al chat general lleva el canal general y el logro si viene")
    void envioAlGeneral() {
        LogroCompartido logro = new LogroCompartido("m1", "Primera victoria");

        controlador.enviarAlGeneral(new EnviarMensajeRequest("miren", logro), jugador());

        verify(enviar).enviar(Canal.general(), new Autor(JUGADOR, "Ana"), "miren", logro);
    }

    @Test
    @DisplayName("sin un jugador autenticado no se envia nada")
    void sinJugadorNoEnvia() {
        Principal anonimo = () -> "nadie";

        assertThrows(AccessDeniedException.class,
                () -> controlador.enviarAlGeneral(new EnviarMensajeRequest("hola", null), anonimo));
        verifyNoInteractions(enviar);
    }

    @Test
    @DisplayName("un error de negocio vuelve como problem details con su tipo y titulo")
    void errorComoProblemDetails() {
        ProblemDetail problema = controlador.errorDeNegocio(new ContenidoBloqueado());

        assertEquals(422, problema.getStatus());
        assertEquals(ContenidoBloqueado.TIPO, problema.getType());
        assertEquals("Mensaje bloqueado", problema.getTitle());
    }

    @Test
    @DisplayName("el historial de la sala se entrega con el tamano configurado y en el formato del contrato")
    void historialDeSala() {
        MensajeDeChat m = new MensajeDeChat(UUID.randomUUID(), Canal.deSala(SALA),
                new Autor(JUGADOR, "Ana"), Tipo.MENSAJE, "hola", null, Instant.parse("2026-09-02T10:00:00Z"));
        when(historial.ultimos(Canal.deSala(SALA), 2)).thenReturn(List.of(m));

        List<MensajeDeChatResponse> respuesta = controlador.historialDeSala(SALA);

        assertEquals(1, respuesta.size());
        assertEquals("chat.mensaje", respuesta.get(0).tipo());
        assertEquals(SALA, respuesta.get(0).idSala());
        assertEquals("Ana", respuesta.get(0).autor().apodo());
    }
}
