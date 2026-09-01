package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Empuja los avisos por la cola privada del jugador.
 *
 * <p>El destino es /usuario/cola/notificaciones, tal como lo declara el
 * contrato. Spring lo resuelve por usuario, asi que todas las sesiones abiertas
 * de esa persona lo reciben a la vez, que es el primer escenario de la historia.
 */
@Component
class CanalStomp implements CanalDeNotificaciones {

    private static final String DESTINO = "/cola/notificaciones";

    private final SimpMessagingTemplate mensajeria;

    CanalStomp(SimpMessagingTemplate mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Override
    public void avisar(String usuarioId, Notificacion aviso, int noLeidas) {
        mensajeria.convertAndSendToUser(usuarioId, DESTINO, aviso);
        actualizarContador(usuarioId, noLeidas);
    }

    @Override
    public void entregarPendientes(String usuarioId, List<Notificacion> pendientes, int noLeidas) {
        for (Notificacion aviso : pendientes) {
            mensajeria.convertAndSendToUser(usuarioId, DESTINO, aviso);
        }
        actualizarContador(usuarioId, noLeidas);
    }

    @Override
    public void actualizarContador(String usuarioId, int noLeidas) {
        mensajeria.convertAndSendToUser(usuarioId, DESTINO, Map.of("noLeidas", noLeidas));
    }
}
