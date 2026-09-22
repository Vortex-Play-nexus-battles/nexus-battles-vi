package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;

/**
 * El controlador no decide nada, solo traslada al servicio el usuario de la
 * conexion y el identificador de sesion estable que envio el cliente. Lo que
 * se verifica es justamente eso: que el usuario sea el del token del CONNECT
 * y no el que diga el mensaje, y que la sesion sea la del mensaje y no la de
 * STOMP, porque si tomara la de STOMP la sesion que vuelve de una caida
 * recibiria repetido lo que ya habia visto.
 */
@ExtendWith(MockitoExtension.class)
class CanalDeSesionesControllerTest {

    private static final UUID UID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private ServicioDeNotificaciones servicio;

    @InjectMocks
    private CanalDeSesionesController controlador;

    private final Map<String, Object> atributos = new HashMap<>();

    /** Usuario tal como lo deja AutenticacionStomp: un JWT real convertido. */
    private static Authentication usuarioConectado(String apodo, UUID uid) {
        EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
        return new ConversorRolesJwt().convert(emisor.decodificador().decode(emisor.tokenDeJugador(apodo, uid)));
    }

    private SimpMessageHeaderAccessor cabeceras() {
        SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.create();
        cabeceras.setSessionAttributes(atributos);
        return cabeceras;
    }

    @Test
    @DisplayName("el alta traslada al servicio el uid del token de la conexion y la sesion estable del mensaje")
    void trasladaElAltaAlServicio() {
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion(null, "movil"),
                usuarioConectado("Ana", UID), cabeceras());

        verify(servicio).registrarSesion(UID.toString(), "movil");
        assertEquals("movil", atributos.get(CanalDeSesionesController.ATRIBUTO_SESION),
                "la sesion queda en la conexion para cerrarla al desconectar");
    }

    @Test
    @DisplayName("el usuarioId del mensaje se ignora: nadie da de alta una sesion a nombre de otro")
    void ignoraElUsuarioDelMensaje() {
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion("jugador-suplantado", "movil"),
                usuarioConectado("Ana", UID), cabeceras());

        verify(servicio).registrarSesion(UID.toString(), "movil");
    }

    @Test
    @DisplayName("dos sesiones del mismo jugador se dan de alta por separado")
    void cadaSesionSeDaDeAltaPorSeparado() {
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion(null, "movil"),
                usuarioConectado("Ana", UID), cabeceras());
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion(null, "escritorio"),
                usuarioConectado("Ana", UID), cabeceras());

        verify(servicio).registrarSesion(UID.toString(), "movil");
        verify(servicio).registrarSesion(UID.toString(), "escritorio");
    }

    @Test
    @DisplayName("sin identificador de sesion o sin usuario en la conexion el alta se rechaza")
    void rechazaAltasIncompletas() {
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion(null, " "),
                usuarioConectado("Ana", UID), cabeceras()));
        assertThrows(IllegalArgumentException.class, () -> controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion(null, "movil"),
                (Principal) null, cabeceras()));

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("un error del canal vuelve como problem details a quien envio")
    void elErrorDelCanalVuelveComoProblemDetails() {
        var problema = controlador.manejarErrorDeCanal(
                new IllegalArgumentException("falta el identificador de la sesion"));

        assertEquals(400, problema.getStatus());
        assertEquals("falta el identificador de la sesion", problema.getDetail());
    }
}
