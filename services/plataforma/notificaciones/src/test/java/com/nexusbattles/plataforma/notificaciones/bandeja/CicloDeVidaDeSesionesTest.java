package com.nexusbattles.plataforma.notificaciones.bandeja;

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
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;

/**
 * Pruebas del cierre de sesion al caerse la conexion.
 *
 * Es la mitad del tercer escenario de la historia: si la sesion caida no se
 * cierra, el siguiente aviso queda como entregado a una conexion muerta y la
 * reconexion no recupera nada. El usuario sale del token del CONNECT y la
 * sesion, del alta que hizo el cliente; nada de eso viene ya en la URL.
 */
@ExtendWith(MockitoExtension.class)
class CicloDeVidaDeSesionesTest {

    private static final UUID UID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private ServicioDeNotificaciones servicio;

    @InjectMocks
    private CicloDeVidaDeSesiones ciclo;

    private static Principal usuarioConectado() {
        EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
        return new ConversorRolesJwt().convert(emisor.decodificador().decode(emisor.tokenDeJugador("Ana", UID)));
    }

    private SessionDisconnectEvent desconexion(Principal usuario, Map<String, Object> atributos) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        cabeceras.setSessionId("stomp-1");
        if (atributos != null) {
            cabeceras.setSessionAttributes(atributos);
        }
        Message<byte[]> mensaje =
                MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
        return new SessionDisconnectEvent(this, mensaje, "stomp-1", CloseStatus.NORMAL, usuario);
    }

    @Test
    @DisplayName("al caerse una conexion autenticada con sesion dada de alta se cierra esa sesion")
    void cierraLaSesionDadaDeAlta() {
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(CanalDeSesionesController.ATRIBUTO_SESION, "movil");

        ciclo.alDesconectar(desconexion(usuarioConectado(), atributos));

        verify(servicio).cerrarSesion(UID.toString(), "movil");
    }

    @Test
    @DisplayName("una desconexion antes del alta de sesion no toca la bandeja")
    void sinAltaNoHayNadaQueCerrar() {
        ciclo.alDesconectar(desconexion(usuarioConectado(), new HashMap<>()));

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("una desconexion sin usuario o sin atributos tampoco toca la bandeja")
    void sinUsuarioTampocoHayNadaQueCerrar() {
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(CanalDeSesionesController.ATRIBUTO_SESION, "movil");

        ciclo.alDesconectar(desconexion(null, atributos));
        ciclo.alDesconectar(desconexion(usuarioConectado(), null));

        verifyNoInteractions(servicio);
    }
}
