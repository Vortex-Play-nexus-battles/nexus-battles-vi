package com.nexusbattles.ms_subastas.subastas.realtime;

import java.util.UUID;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La politica del canal del listado de subastas — R9.6b.
 *
 * <p>Los tokens los firma {@link EmisorDeTokensDePrueba} y los valida el
 * decodificador REAL contra su JWKS servido por HTTP: aqui no hay ningun
 * decodificador que acepte cualquier cadena. Lo que se comprueba es lo que
 * comprobaria el servicio desplegado.
 */
@DisplayName("R9.6b: quien entra al canal de subastas y que puede hacer")
class PoliticaDelCanalDeSubastasTest {

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final String PUBLICO = SubastaRealtimePublisher.CANAL_LISTADO;

    private final PoliticaDelCanalDeSubastas politica = new PoliticaDelCanalDeSubastas(
            EMISOR.decodificador(), new ConversorRolesJwt(), PUBLICO);

    // ------------------------------------------------------------ utilidades

    private static StompHeaderAccessor cabeceras(StompCommand comando) {
        StompHeaderAccessor accesor = StompHeaderAccessor.create(comando);
        accesor.setLeaveMutable(true);
        return accesor;
    }

    private static Message<byte[]> frame(StompHeaderAccessor accesor) {
        return MessageBuilder.createMessage(new byte[0], accesor.getMessageHeaders());
    }

    private StompHeaderAccessor conectar(String token) {
        StompHeaderAccessor accesor = cabeceras(StompCommand.CONNECT);
        if (token != null) {
            accesor.setNativeHeader("Authorization", "Bearer " + token);
        }
        politica.preSend(frame(accesor), null);
        return accesor;
    }

    // ----------------------------------------------------------------- CONNECT

    @Nested
    @DisplayName("CONNECT: la credencial rota no es lo mismo que ninguna credencial")
    class Conectar {

        @Test
        @DisplayName("token valido: la sesion queda identificada por el uid (ADR-002)")
        void tokenValido() {
            UUID uid = UUID.randomUUID();

            StompHeaderAccessor accesor = conectar(EMISOR.tokenDeJugador("lyra", uid));

            assertNotNull(accesor.getUser(), "un token valido tiene que identificar la sesion");
            assertEquals(uid.toString(), accesor.getUser().getName(),
                    "el principal es el uid estable, no el apodo: el apodo se puede cambiar");
        }

        @Test
        @DisplayName("token caducado: rechazo")
        void tokenCaducado() {
            StompHeaderAccessor accesor = cabeceras(StompCommand.CONNECT);
            accesor.setNativeHeader("Authorization",
                    "Bearer " + EMISOR.tokenCaducado("lyra", UUID.randomUUID()));

            assertThrows(AccessDeniedException.class, () -> politica.preSend(frame(accesor), null));
        }

        @Test
        @DisplayName("token firmado por otra clave: rechazo, la firma se verifica de verdad")
        void tokenFalsificado() {
            StompHeaderAccessor accesor = cabeceras(StompCommand.CONNECT);
            accesor.setNativeHeader("Authorization",
                    "Bearer " + EMISOR.tokenFirmadoPorOtro("lyra", UUID.randomUUID()));

            assertThrows(AccessDeniedException.class, () -> politica.preSend(frame(accesor), null));
        }

        @Test
        @DisplayName("cadena que ni siquiera es un Bearer: rechazo")
        void credencialQueNoEsBearer() {
            StompHeaderAccessor accesor = cabeceras(StompCommand.CONNECT);
            accesor.setNativeHeader("Authorization", "Basic lyra:1234");

            assertThrows(AccessDeniedException.class, () -> politica.preSend(frame(accesor), null));
        }

        @Test
        @DisplayName("sin credencial: entra como visitante, porque la vista es publica")
        void sinCredencial() {
            // matriz-acceso.js declara `pujas: { acceso: ACCESO.PUBLICA }`. Quien
            // no ha entrado puede ver el listado de subastas, y quitarle la
            // actualizacion en vivo seria degradarle la pantalla sin motivo.
            StompHeaderAccessor accesor = conectar(null);

            assertNull(accesor.getUser(), "un visitante no tiene identidad, y no pasa nada");
        }
    }

    // --------------------------------------------------------------- SUBSCRIBE

    @Nested
    @DisplayName("SUBSCRIBE: el listado es de todos; lo demas, no")
    class Suscribirse {

        private Message<byte[]> suscripcionA(String destino, boolean identificado) {
            StompHeaderAccessor accesor = cabeceras(StompCommand.SUBSCRIBE);
            accesor.setDestination(destino);
            if (identificado) {
                accesor.setUser(new ConversorRolesJwt().convert(
                        EMISOR.decodificador().decode(
                                EMISOR.tokenDeJugador("lyra", UUID.randomUUID()))));
            }
            return frame(accesor);
        }

        @Test
        @DisplayName("un visitante puede seguir el listado publico")
        void visitanteAlListado() {
            assertDoesNotThrow(() -> politica.preSend(suscripcionA(PUBLICO, false), null));
        }

        @Test
        @DisplayName("un visitante NO puede seguir ningun otro destino")
        void visitanteAOtroDestino() {
            // Hoy no existe ningun otro destino. La regla esta puesta para que
            // el dia que alguien anada /user/** o un topico por subasta, nazca
            // cerrado en vez de abierto.
            assertThrows(AccessDeniedException.class,
                    () -> politica.preSend(suscripcionA("/user/queue/mis-pujas", false), null));
        }

        @Test
        @DisplayName("identificado, si")
        void identificadoAOtroDestino() {
            assertDoesNotThrow(
                    () -> politica.preSend(suscripcionA("/user/queue/mis-pujas", true), null));
        }
    }

    // -------------------------------------------------------------------- SEND

    @Nested
    @DisplayName("Anti-suplantacion: nadie puede actuar por otro, porque nadie puede actuar")
    class Enviar {

        @Test
        @DisplayName("un SEND del cliente se rechaza, venga de quien venga")
        void ningunEnvioSeAcepta() {
            // ms-subastas no tiene ni un @MessageMapping: nada del cliente llega
            // al dominio por el canal. Un SEND solo puede ser un tanteo.
            StompHeaderAccessor accesor = cabeceras(StompCommand.SEND);
            accesor.setDestination("/app/pujar");

            assertThrows(AccessDeniedException.class, () -> politica.preSend(frame(accesor), null));
        }

        @Test
        @DisplayName("el usuario A no puede actuar como B ni con sesion valida")
        void aNoPuedeActuarComoB() {
            // El caso clasico: A conecta con su token y manda un cuerpo que dice
            // ser B. Aqui ni siquiera llega a plantearse quien es el actor,
            // porque el frame no pasa. Es la garantia mas fuerte disponible: no
            // hay ningun camino por el que un dato del payload se convierta en
            // identidad.
            StompHeaderAccessor accesor = cabeceras(StompCommand.SEND);
            accesor.setDestination("/app/pujar");
            accesor.setUser(new ConversorRolesJwt().convert(
                    EMISOR.decodificador().decode(
                            EMISOR.tokenDeJugador("usuarioA", UUID.randomUUID()))));

            AccessDeniedException fallo = assertThrows(AccessDeniedException.class,
                    () -> politica.preSend(frame(accesor), null));
            assertEquals("Este canal no acepta mensajes del cliente.", fallo.getMessage());
        }
    }
}
