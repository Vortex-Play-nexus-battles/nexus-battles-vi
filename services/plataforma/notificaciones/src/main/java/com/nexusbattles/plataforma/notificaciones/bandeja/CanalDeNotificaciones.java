package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.util.List;

import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Puerta al canal en tiempo real descrito en contracts/websocket/notificaciones.yaml.
 *
 * <p>El servicio no sabe si detras hay STOMP, otro transporte o nada. Eso deja
 * el servicio probable sin levantar un servidor y permite cambiar el canal sin
 * tocar las reglas.
 */
public interface CanalDeNotificaciones {

    /** Empuja un aviso a la cola privada del jugador. */
    void avisar(String usuarioId, Notificacion aviso, int noLeidas);

    /** Empuja a la cola privada lo que una sesion se habia perdido. */
    void entregarPendientes(String usuarioId, List<Notificacion> pendientes, int noLeidas);

    /** Avisa que cambio la cuenta de no leidos del jugador. */
    void actualizarContador(String usuarioId, int noLeidas);
}
