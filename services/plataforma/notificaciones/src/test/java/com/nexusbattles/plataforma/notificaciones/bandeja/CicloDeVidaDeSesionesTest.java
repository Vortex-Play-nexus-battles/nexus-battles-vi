package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.Map;

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

/**
 * Pruebas del cierre de sesion al caerse la conexion.
 *
 * Es la mitad del tercer escenario de la historia: si la sesion caida no se
 * cierra, el siguiente aviso queda como entregado a una conexion muerta y la
 * reconexion no recupera nada.
 */
@ExtendWith(MockitoExtension.class)
class CicloDeVidaDeSesionesTest {

    @Mock
    private ServicioDeNotificaciones servicio;

    @InjectMocks
    private CicloDeVidaDeSesiones ciclo;

    private SessionDisconnectEvent desconexion(Map<String, Object> atributos) {
        StompHeaderAccessor cabeceras = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        cabeceras.setSessionId("stomp-1");
        if (atributos != null) {
            cabeceras.setSessionAttributes(atributos);
        }
        Message<byte[]> mensaje =
                MessageBuilder.createMessage(new byte[0], cabeceras.getMessageHeaders());
        return new SessionDisconnectEvent(this, mensaje, "stomp-1", CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("al caerse una conexion con identidad se cierra su sesion estable")
    void cierraLaSesionQueDejoElHandshake() {
        Map<String, Object> atributos = new HashMap<>();
        atributos.put(AsignadorDeIdentidadDelHandshake.ATRIBUTO_USUARIO, "jugador-1");
        atributos.put(AsignadorDeIdentidadDelHandshake.ATRIBUTO_SESION, "movil");

        ciclo.alDesconectar(desconexion(atributos));

        verify(servicio).cerrarSesion("jugador-1", "movil");
    }

    @Test
    @DisplayName("una desconexion sin identidad del handshake no toca la bandeja")
    void sinIdentidadNoHayNadaQueCerrar() {
        ciclo.alDesconectar(desconexion(new HashMap<>()));

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("una desconexion sin atributos de sesion tampoco toca la bandeja")
    void sinAtributosTampocoHayNadaQueCerrar() {
        ciclo.alDesconectar(desconexion(null));

        verifyNoInteractions(servicio);
    }
}
