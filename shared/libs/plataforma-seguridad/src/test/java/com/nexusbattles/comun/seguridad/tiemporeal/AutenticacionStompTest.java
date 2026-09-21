package com.nexusbattles.comun.seguridad.tiemporeal;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * El CONNECT de STOMP se autentica con el mismo token y el mismo decodificador
 * que la API HTTP: tokens reales, verificados contra un JWKS real.
 */
@DisplayName("AutenticacionStomp · el CONNECT exige el mismo JWT que la API HTTP")
class AutenticacionStompTest {

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final AutenticacionStomp autenticacion =
            new AutenticacionStomp(emisor.decodificador(), new ConversorRolesJwt());

    private static Message<byte[]> frame(StompCommand comando, String autorizacion) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(comando);
        if (autorizacion != null) {
            cabeceras.setNativeHeader(AutenticacionStomp.CABECERA, autorizacion);
        }
        cabeceras.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
    }

    @Test
    @DisplayName("un CONNECT con token valido deja al usuario en la sesion: nombre = uid, autoridades = rol")
    void connectConTokenValido() {
        UUID uid = UUID.randomUUID();
        String token = emisor.tokenDeJugador("Ana", uid);

        Message<?> resultado = autenticacion.preSend(frame(StompCommand.CONNECT, "Bearer " + token), null);

        Authentication usuario = (Authentication) StompHeaderAccessor.wrap(resultado).getUser();
        assertThat(usuario).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(usuario.getName()).isEqualTo(uid.toString());
        assertThat(usuario.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_JUGADOR");
    }

    @Test
    @DisplayName("un CONNECT sin token no se conecta")
    void connectSinToken() {
        assertThatThrownBy(() -> autenticacion.preSend(frame(StompCommand.CONNECT, null), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> autenticacion.preSend(frame(StompCommand.CONNECT, "Basic abc"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("un token caducado o firmado por otro no se conecta")
    void connectConTokenInvalido() {
        UUID uid = UUID.randomUUID();

        assertThatThrownBy(() -> autenticacion.preSend(
                frame(StompCommand.CONNECT, "Bearer " + emisor.tokenCaducado("Ana", uid)), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> autenticacion.preSend(
                frame(StompCommand.CONNECT, "Bearer " + emisor.tokenFirmadoPorOtro("Ana", uid)), null))
                .isInstanceOf(AccessDeniedException.class);
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
