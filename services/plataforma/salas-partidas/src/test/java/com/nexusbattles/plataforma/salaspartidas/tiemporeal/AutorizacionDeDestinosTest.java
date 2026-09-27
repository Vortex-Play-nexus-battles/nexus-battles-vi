package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Turno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Autorizacion del broker STOMP — HU-SAL-002 y B6 (mensajes privados).
 *
 * <p>Casos positivos y negativos sobre el mismo interceptor real, con los dos
 * repositorios como unicos dobles: lo que se prueba es la regla, no la base.
 *
 * <p>Desde B6 el interceptor decide sobre TODO el broker, no solo sobre la
 * suscripcion al canal de una sala: un cliente solo envia a {@code /app/**}
 * (un SEND directo a {@code /tema/**} o {@code /cola/**} lo reenviaba el
 * broker saltandose lista negra, sancion y persistencia), y solo se suscribe a
 * los destinos que el servicio publica.
 */
class AutorizacionDeDestinosTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITADO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INTRUSO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ID_PRIVADA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ID_PUBLICA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID ID_INEXISTENTE = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000ffff");
    private static final UUID ID_PARTIDA = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID ID_PARTIDA_INEXISTENTE = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000ffff");

    private final RepositorioDeSalas salas = mock(RepositorioDeSalas.class);
    private final RepositorioDePartidas partidas = mock(RepositorioDePartidas.class);
    private final AutorizacionDeDestinos autorizacion = new AutorizacionDeDestinos(salas, partidas);

    AutorizacionDeDestinosTest() {
        when(salas.buscarPorId(any())).thenReturn(Optional.empty());
        when(salas.buscarPorId(ID_PRIVADA)).thenReturn(Optional.of(sala(ID_PRIVADA, true)));
        when(salas.buscarPorId(ID_PUBLICA)).thenReturn(Optional.of(sala(ID_PUBLICA, false)));
        when(partidas.buscarPorId(any())).thenReturn(Optional.empty());
        when(partidas.buscarPorId(ID_PARTIDA)).thenReturn(Optional.of(partida()));
    }

    private static Sala sala(UUID id, boolean privada) {
        return Sala.rehidratar(id, privada ? EstadoSala.PRIVADA : EstadoSala.ABIERTA,
                Modalidad.HASTA_SEIS, 4, 0, false, privada, null,
                ANFITRION, Set.of(ANFITRION, INVITADO), Instant.now());
    }

    /** Partida del anfitrion y del invitado, con un cupo de la maquina. */
    private static Partida partida() {
        return Partida.rehidratar(ID_PARTIDA, ID_PUBLICA, EstadoPartida.EN_CURSO,
                List.of(ParticipanteDePartida.humano(ANFITRION, 0),
                        ParticipanteDePartida.humano(INVITADO, 0),
                        ParticipanteDePartida.inteligenciaArtificial(UUID.randomUUID())),
                Turno.primero(ANFITRION), 0, Instant.now());
    }

    private static Message<byte[]> suscripcion(String destino, UUID jugador) {
        return frame(StompCommand.SUBSCRIBE, destino, jugador);
    }

    private static Message<byte[]> envio(String destino, UUID jugador) {
        return frame(StompCommand.SEND, destino, jugador);
    }

    private static Message<byte[]> frame(StompCommand comando, String destino, UUID jugador) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(comando);
        if (destino != null) {
            cabeceras.setDestination(destino);
        }
        if (comando == StompCommand.SUBSCRIBE) {
            cabeceras.setSubscriptionId("sub-1");
        }
        if (jugador != null) {
            Principal usuario = jugador::toString;
            cabeceras.setUser(usuario);
        }
        cabeceras.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
    }

    private void pasa(Message<byte[]> frame) {
        assertSame(frame, autorizacion.preSend(frame, null));
    }

    private void seRechaza(Message<byte[]> frame) {
        assertThrows(AccessDeniedException.class, () -> autorizacion.preSend(frame, null));
    }

    @Nested
    @DisplayName("canal de una sala (HU-SAL-002)")
    class CanalDeSala {

        @Test
        @DisplayName("un participante puede seguir el canal de su sala privada")
        void participanteEnPrivada() {
            pasa(suscripcion("/tema/salas/" + ID_PRIVADA, INVITADO));
        }

        @Test
        @DisplayName("un jugador ajeno NO puede seguir el canal de una sala privada")
        void ajenoEnPrivada() {
            seRechaza(suscripcion("/tema/salas/" + ID_PRIVADA, INTRUSO));
        }

        @Test
        @DisplayName("tampoco al chat de una sala privada, que cuelga del mismo destino")
        void ajenoAlChatDePrivada() {
            seRechaza(suscripcion("/tema/salas/" + ID_PRIVADA + "/chat", INTRUSO));
        }

        @Test
        @DisplayName("cualquier jugador autenticado puede seguir una sala publica, este dentro o no")
        void cualquieraEnPublica() {
            pasa(suscripcion("/tema/salas/" + ID_PUBLICA, ANFITRION));
            pasa(suscripcion("/tema/salas/" + ID_PUBLICA, INTRUSO));
            pasa(suscripcion("/tema/salas/" + ID_PUBLICA + "/chat", INTRUSO));
        }

        @Test
        @DisplayName("una sala que no existe no admite suscripciones")
        void salaInexistente() {
            seRechaza(suscripcion("/tema/salas/" + ID_INEXISTENTE, ANFITRION));
        }

        @Test
        @DisplayName("un identificador de sala que no es un UUID no es un destino")
        void salaMalFormada() {
            seRechaza(suscripcion("/tema/salas/no-es-un-uuid", ANFITRION));
        }

        @Test
        @DisplayName("sin identidad en la sesion no hay suscripcion a ninguna sala")
        void sinUsuario() {
            seRechaza(suscripcion("/tema/salas/" + ID_PUBLICA, null));
        }
    }

    @Nested
    @DisplayName("chat de una sala privada (HU-JUE-015, B6)")
    class ChatDeSalaPrivada {

        @Test
        @DisplayName("el ajeno no escribe en el chat de una sala privada")
        void ajenoNoEscribe() {
            seRechaza(envio("/app/salas/" + ID_PRIVADA + "/chat", INTRUSO));
        }

        @Test
        @DisplayName("el participante si escribe en el chat de su sala privada")
        void participanteEscribe() {
            pasa(envio("/app/salas/" + ID_PRIVADA + "/chat", INVITADO));
        }

        @Test
        @DisplayName("en una sala publica escribe cualquiera: el chat publico no cambia")
        void publicaSinCambios() {
            pasa(envio("/app/salas/" + ID_PUBLICA + "/chat", INTRUSO));
        }

        @Test
        @DisplayName("no se escribe en el chat de una sala que no existe")
        void salaInexistente() {
            seRechaza(envio("/app/salas/" + ID_INEXISTENTE + "/chat", ANFITRION));
        }

        @Test
        @DisplayName("sin identidad no se escribe en el chat de una sala")
        void sinUsuario() {
            seRechaza(envio("/app/salas/" + ID_PUBLICA + "/chat", null));
        }

        @Test
        @DisplayName("el historial de una sala privada solo lo leen sus participantes")
        void historialPrivado() {
            seRechaza(suscripcion("/app/salas/" + ID_PRIVADA + "/chat/historial", INTRUSO));
            pasa(suscripcion("/app/salas/" + ID_PRIVADA + "/chat/historial", ANFITRION));
        }

        @Test
        @DisplayName("el historial de una sala publica lo lee cualquiera, como antes")
        void historialPublico() {
            pasa(suscripcion("/app/salas/" + ID_PUBLICA + "/chat/historial", INTRUSO));
        }
    }

    @Nested
    @DisplayName("canal de una partida (B6)")
    class CanalDePartida {

        @Test
        @DisplayName("un participante sigue el canal de su partida")
        void participante() {
            pasa(suscripcion("/tema/partidas/" + ID_PARTIDA, INVITADO));
        }

        @Test
        @DisplayName("quien no juega esa partida no la sigue")
        void ajeno() {
            seRechaza(suscripcion("/tema/partidas/" + ID_PARTIDA, INTRUSO));
        }

        @Test
        @DisplayName("una partida que no existe no admite suscripciones")
        void inexistente() {
            seRechaza(suscripcion("/tema/partidas/" + ID_PARTIDA_INEXISTENTE, ANFITRION));
        }

        @Test
        @DisplayName("sin identidad no se sigue ninguna partida")
        void sinUsuario() {
            seRechaza(suscripcion("/tema/partidas/" + ID_PARTIDA, null));
        }
    }

    @Nested
    @DisplayName("SEND: solo a destinos de aplicacion (B6)")
    class SoloDestinosDeAplicacion {

        @ParameterizedTest(name = "SEND a {0} se rechaza")
        @ValueSource(strings = {
            "/tema/chat/general",
            "/tema/salas/aaaaaaaa-0000-0000-0000-000000000002/chat",
            "/tema/partidas/bbbbbbbb-0000-0000-0000-000000000001",
            "/cola/mensajes-directos",
            "/cola/mensajes-directos-usersesion1",
            "/usuario/cola/mensajes-directos",
            "/usuario/22222222-2222-2222-2222-222222222222/cola/mensajes-directos",
            "/app",
            "/otra/cosa",
        })
        void fueraDeApp(String destino) {
            seRechaza(envio(destino, ANFITRION));
        }

        @Test
        @DisplayName("un SEND sin destino no llega a ninguna parte")
        void sinDestino() {
            seRechaza(envio(null, ANFITRION));
        }

        @Test
        @DisplayName("los destinos de aplicacion llegan a sus controladores, que deciden con el dominio")
        void destinosDeAplicacion() {
            pasa(envio("/app/chat/general", INTRUSO));
            pasa(envio("/app/partidas/" + ID_PARTIDA + "/acciones", INVITADO));
            pasa(envio("/app/mensajes-directos/" + INVITADO, ANFITRION));
        }
    }

    @Nested
    @DisplayName("SUBSCRIBE: solo a lo que el servicio publica (B6)")
    class SoloDestinosPublicados {

        @Test
        @DisplayName("la cola propia, el chat general y su historial pasan")
        void publicados() {
            pasa(suscripcion("/usuario/cola/salas", INTRUSO));
            pasa(suscripcion("/usuario/cola/mensajes-directos", INTRUSO));
            pasa(suscripcion("/tema/chat/general", INTRUSO));
            pasa(suscripcion("/app/chat/general/historial", INTRUSO));
        }

        @ParameterizedTest(name = "SUBSCRIBE a {0} se rechaza")
        @ValueSource(strings = {
            // La cola ya resuelta de otra sesion: se oiria lo privado de otro.
            "/cola/mensajes-directos-usersesion1",
            "/cola/salas",
            // Patrones del broker simple: se suscribirian a todo lo que casa.
            "/tema/**",
            "/tema/salas/**",
            "/tema/salas/*/chat",
            "/cola/**",
            "/usuario/cola/**",
            // Rutas no canonicas: `//` y `..` casan en el enrutador de Spring.
            "/tema/salas//aaaaaaaa-0000-0000-0000-000000000001",
            "/tema/salas/../chat/general",
            "/usuario/cola/salas/",
            // Destinos que el servicio no publica.
            "/tema/otra-cosa",
            "/usuario/22222222-2222-2222-2222-222222222222/cola/mensajes-directos",
            "/app/mensajes-directos/22222222-2222-2222-2222-222222222222",
        })
        void noPublicados(String destino) {
            seRechaza(suscripcion(destino, ANFITRION));
        }

        @Test
        @DisplayName("un SUBSCRIBE sin destino se rechaza")
        void sinDestino() {
            seRechaza(suscripcion(null, ANFITRION));
        }
    }

    @Test
    @DisplayName("los frames que no son SEND ni SUBSCRIBE pasan intactos")
    void otrosFrames() {
        pasa(frame(StompCommand.CONNECT, null, null));
        pasa(frame(StompCommand.UNSUBSCRIBE, null, ANFITRION));
        pasa(frame(StompCommand.DISCONNECT, null, ANFITRION));
    }

    @Test
    @DisplayName("un mensaje sin cabeceras STOMP no es asunto de este interceptor")
    void sinCabecerasStomp() {
        Message<String> crudo = MessageBuilder.withPayload("hola").build();
        assertSame(crudo, autorizacion.preSend(crudo, null));
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

    private static Message<byte[]> conSesion(StompCommand comando, String destino, Principal usuario) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(comando);
        cabeceras.setDestination(destino);
        if (comando == StompCommand.SUBSCRIBE) {
            cabeceras.setSubscriptionId("sub-1");
        }
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
        pasa(conSesion(StompCommand.SUBSCRIBE, "/tema/salas/" + ID_PRIVADA,
                sesionDeAdr002("invitado_e2e", INVITADO)));
        pasa(conSesion(StompCommand.SUBSCRIBE, "/tema/partidas/" + ID_PARTIDA,
                sesionDeAdr002("invitado_e2e", INVITADO)));
        pasa(conSesion(StompCommand.SEND, "/app/salas/" + ID_PRIVADA + "/chat",
                sesionDeAdr002("invitado_e2e", INVITADO)));
    }

    @Test
    @DisplayName("con el token real, a la sala privada ajena se le sigue diciendo que no")
    void ajenoConTokenReal() {
        seRechaza(conSesion(StompCommand.SUBSCRIBE, "/tema/salas/" + ID_PRIVADA,
                sesionDeAdr002("intrusa", INTRUSO)));
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

        seRechaza(conSesion(StompCommand.SUBSCRIBE, "/tema/salas/" + ID_PRIVADA,
                new JwtAuthenticationToken(sinUid)));
    }

    @Test
    @DisplayName("un principal que no es JWT ni un UUID no es un jugador")
    void principalRaro() {
        Principal raro = () -> "no-soy-un-uuid";
        seRechaza(conSesion(StompCommand.SUBSCRIBE, "/tema/salas/" + ID_PRIVADA, raro));
        Principal sinNombre = () -> null;
        seRechaza(conSesion(StompCommand.SUBSCRIBE, "/tema/salas/" + ID_PRIVADA, sinNombre));
    }
}
