package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class AutenticacionStompTest {

    private static final String SUJETO = "3f2b6f3e-3c2a-4a1e-9f0e-6f1a2b3c4d5e";

    private final JwtDecoder decodificador = mock(JwtDecoder.class);
    private final AutenticacionStomp autenticacion = new AutenticacionStomp(decodificador);

    private static Message<byte[]> frame(StompCommand comando, String autorizacion) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(comando);
        if (autorizacion != null) {
            cabeceras.setNativeHeader(AutenticacionStomp.CABECERA, autorizacion);
        }
        cabeceras.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
    }

    @Test
    @DisplayName("un CONNECT con token valido deja al jugador como usuario de la sesion")
    void connectConTokenValido() {
        when(decodificador.decode("abc")).thenReturn(Jwt.withTokenValue("abc")
                .header("alg", "none").subject(SUJETO).claim("preferred_username", "Ana").build());

        Message<?> resultado = autenticacion.preSend(frame(StompCommand.CONNECT, "Bearer abc"), null);

        assertEquals(SUJETO, StompHeaderAccessor.wrap(resultado).getUser().getName());
    }

    @Test
    @DisplayName("un CONNECT sin token o con token invalido no se conecta")
    void connectSinTokenNoEntra() {
        when(decodificador.decode("malo")).thenThrow(new JwtException("caducado"));

        assertThrows(AccessDeniedException.class,
                () -> autenticacion.preSend(frame(StompCommand.CONNECT, null), null));
        assertThrows(AccessDeniedException.class,
                () -> autenticacion.preSend(frame(StompCommand.CONNECT, "Bearer malo"), null));
    }

    @Test
    @DisplayName("los frames que no son CONNECT pasan intactos")
    void otrosFramesPasan() {
        Message<byte[]> envio = frame(StompCommand.SEND, null);

        Message<?> resultado = autenticacion.preSend(envio, null);

        assertSame(envio, resultado);
        assertNull(StompHeaderAccessor.wrap(resultado).getUser());
    }
}
