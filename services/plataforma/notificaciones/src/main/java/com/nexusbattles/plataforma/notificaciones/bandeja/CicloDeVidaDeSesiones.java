package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Cierra la sesion en la bandeja cuando la conexion WebSocket se cae.
 *
 * <p>Sin esto una sesion caida sigue figurando como abierta: el siguiente
 * aviso queda marcado como entregado a una conexion que ya no existe y la
 * reconexion no recupera nada, que es exactamente el tercer escenario de la
 * historia. El identificador estable sale de los atributos que dejo el
 * handshake. Si el cliente no los mando no hay nada que cerrar y se deja
 * constancia en la bitacora, nunca en silencio.
 */
@Component
class CicloDeVidaDeSesiones {

    private static final Logger log = LoggerFactory.getLogger(CicloDeVidaDeSesiones.class);

    private final ServicioDeNotificaciones servicio;

    CicloDeVidaDeSesiones(ServicioDeNotificaciones servicio) {
        this.servicio = servicio;
    }

    @EventListener
    public void alDesconectar(SessionDisconnectEvent evento) {
        Map<String, Object> atributos = StompHeaderAccessor.wrap(evento.getMessage())
                .getSessionAttributes();
        if (atributos == null) {
            return;
        }
        Object usuario = atributos.get(AsignadorDeIdentidadDelHandshake.ATRIBUTO_USUARIO);
        Object sesion = atributos.get(AsignadorDeIdentidadDelHandshake.ATRIBUTO_SESION);
        if (usuario == null || sesion == null) {
            log.debug("Desconexion sin identidad del handshake, no hay sesion que cerrar");
            return;
        }
        servicio.cerrarSesion(usuario.toString(), sesion.toString());
    }
}
