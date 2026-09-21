package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Autorizacion por destino del canal — HU-SAL-002.
 *
 * <p>Casos positivos y negativos sobre el mismo interceptor real, con el
 * repositorio como unico doble: lo que se prueba es la regla, no la base.
 */
class AutorizacionDeDestinosTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITADO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INTRUSO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ID_PRIVADA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ID_PUBLICA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID ID_INEXISTENTE = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000ffff");

    private final RepositorioDeSalas salas = mock(RepositorioDeSalas.class);
    private final AutorizacionDeDestinos autorizacion = new AutorizacionDeDestinos(salas);

    AutorizacionDeDestinosTest() {
        when(salas.buscarPorId(any())).thenReturn(Optional.empty());
        when(salas.buscarPorId(ID_PRIVADA)).thenReturn(Optional.of(sala(ID_PRIVADA, true)));
        when(salas.buscarPorId(ID_PUBLICA)).thenReturn(Optional.of(sala(ID_PUBLICA, false)));
    }

    private static Sala sala(UUID id, boolean privada) {
        return Sala.rehidratar(id, privada ? EstadoSala.PRIVADA : EstadoSala.ABIERTA,
                Modalidad.HASTA_SEIS, 4, 0, false, privada, null,
                ANFITRION, Set.of(ANFITRION, INVITADO), Instant.now());
    }

    private static Message<byte[]> suscripcion(String destino, UUID jugador) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        cabeceras.setDestination(destino);
        cabeceras.setSubscriptionId("sub-1");
        if (jugador != null) {
            Principal usuario = jugador::toString;
            cabeceras.setUser(usuario);
        }
        cabeceras.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
    }

    private static Message<byte[]> frame(StompCommand comando, String destino) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(comando);
        if (destino != null) {
            cabeceras.setDestination(destino);
        }
        cabeceras.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
    }

    @Test
    @DisplayName("un participante puede seguir el canal de su sala privada")
    void participanteEnPrivada() {
        Message<byte[]> frame = suscripcion("/tema/salas/" + ID_PRIVADA, INVITADO);
        assertSame(frame, autorizacion.preSend(frame, null));
    }

    @Test
    @DisplayName("un jugador ajeno NO puede seguir el canal de una sala privada")
    void ajenoEnPrivada() {
        assertThrows(AccessDeniedException.class,
                () -> autorizacion.preSend(suscripcion("/tema/salas/" + ID_PRIVADA, INTRUSO), null));
    }

    @Test
    @DisplayName("tampoco al chat de una sala privada, que cuelga del mismo destino")
    void ajenoAlChatDePrivada() {
        assertThrows(AccessDeniedException.class,
                () -> autorizacion.preSend(
                        suscripcion("/tema/salas/" + ID_PRIVADA + "/chat", INTRUSO), null));
    }

    @Test
    @DisplayName("cualquier jugador autenticado puede seguir una sala publica, este dentro o no")
    void cualquieraEnPublica() {
        Message<byte[]> dentro = suscripcion("/tema/salas/" + ID_PUBLICA, ANFITRION);
        Message<byte[]> fuera = suscripcion("/tema/salas/" + ID_PUBLICA, INTRUSO);
        assertSame(dentro, autorizacion.preSend(dentro, null));
        assertSame(fuera, autorizacion.preSend(fuera, null));
    }

    @Test
    @DisplayName("una sala que no existe no admite suscripciones")
    void salaInexistente() {
        assertThrows(AccessDeniedException.class,
                () -> autorizacion.preSend(suscripcion("/tema/salas/" + ID_INEXISTENTE, ANFITRION), null));
    }

    @Test
    @DisplayName("sin identidad en la sesion no hay suscripcion a ninguna sala")
    void sinUsuario() {
        assertThrows(AccessDeniedException.class,
                () -> autorizacion.preSend(suscripcion("/tema/salas/" + ID_PUBLICA, null), null));
    }

    /**
     * Sesion tal como la deja {@code AutenticacionStomp}: un
     * {@link JwtAuthenticationToken} con el JWT de verdad que emite
     * {@code ms-identidad}. Es lo que faltaba: los demas casos falsifican el
     * principal como un UUID en texto, y con esa forma el defecto de identidad
     * era invisible.
     *
     * @param apodo lo que va en {@code sub} tras ADR-002: un apodo, no un UUID
     * @param uid   identificador estable, en su claim
     */
    private static Principal sesionDeAdr002(String apodo, UUID uid) {
        Jwt jwt = Jwt.withTokenValue("no-importa")
                .header("alg", "RS256")
                .subject(apodo)
                .claim("uid", uid.toString())
                .claim("rol", "JUGADOR")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private static Message<byte[]> suscripcionDe(String destino, Principal usuario) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        cabeceras.setDestination(destino);
        cabeceras.setSubscriptionId("sub-1");
        cabeceras.setUser(usuario);
        cabeceras.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
    }

    @Test
    @DisplayName("con el token real de ADR-002 el participante entra: el apodo del sub no es su id")
    void participanteConTokenReal() {
        // El defecto que cierra: `getName()` de un JwtAuthenticationToken es el
        // sujeto, y tras ADR-002 el sujeto es el apodo. Leerlo como UUID
        // rechazaba a TODO jugador real, Spring mandaba ERROR y cerraba la
        // conexion, llevandose tambien el canal de la partida.
        Message<byte[]> frame = suscripcionDe(
                "/tema/salas/" + ID_PRIVADA, sesionDeAdr002("invitado_e2e", INVITADO));

        assertSame(frame, autorizacion.preSend(frame, null));
    }

    @Test
    @DisplayName("con el token real, a la sala privada ajena se le sigue diciendo que no")
    void ajenoConTokenReal() {
        assertThrows(AccessDeniedException.class, () -> autorizacion.preSend(
                suscripcionDe("/tema/salas/" + ID_PRIVADA, sesionDeAdr002("intrusa", INTRUSO)),
                null));
    }

    @Test
    @DisplayName("un token sin uid ni sujeto utilizable no autoriza nada")
    void tokenSinIdentificador() {
        Jwt sinUid = Jwt.withTokenValue("no-importa")
                .header("alg", "RS256")
                .subject("solo_un_apodo")
                .claim("rol", "JUGADOR")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertThrows(AccessDeniedException.class, () -> autorizacion.preSend(
                suscripcionDe("/tema/salas/" + ID_PRIVADA, new JwtAuthenticationToken(sinUid)),
                null));
    }

    @Test
    @DisplayName("los destinos que no son de una sala, y los frames que no son SUBSCRIBE, pasan intactos")
    void loDemasPasa() {
        Message<byte[]> chatGeneral = suscripcion("/tema/chat/general", INTRUSO);
        Message<byte[]> colaPropia = suscripcion("/usuario/cola/salas", INTRUSO);
        Message<byte[]> envio = frame(StompCommand.SEND, "/app/salas/" + ID_PRIVADA + "/chat");
        Message<byte[]> conexion = frame(StompCommand.CONNECT, null);

        assertSame(chatGeneral, autorizacion.preSend(chatGeneral, null));
        assertSame(colaPropia, autorizacion.preSend(colaPropia, null));
        assertSame(envio, autorizacion.preSend(envio, null));
        assertSame(conexion, autorizacion.preSend(conexion, null));
    }
}
