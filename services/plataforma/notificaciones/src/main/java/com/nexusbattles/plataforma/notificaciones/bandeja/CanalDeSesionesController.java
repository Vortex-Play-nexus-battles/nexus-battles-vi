package com.nexusbattles.plataforma.notificaciones.bandeja;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Recibe el alta de sesion que llega por STOMP a /app/notificaciones/sesion.
 *
 * <p>El cliente envia su identificador estable, el que sobrevive a la
 * reconexion, y el servicio le responde por la cola privada con lo que se
 * perdio. El identificador de sesion de STOMP no sirve para esto porque se
 * renueva en cada reconexion.
 */
@Controller
class CanalDeSesionesController {

    private final ServicioDeNotificaciones servicio;

    CanalDeSesionesController(ServicioDeNotificaciones servicio) {
        this.servicio = servicio;
    }

    @MessageMapping("/notificaciones/sesion")
    public void registrarSesion(RegistrarSesion mensaje) {
        servicio.registrarSesion(mensaje.usuarioId(), mensaje.sesionId());
    }

    /** Cuerpo del alta de sesion, segun el contrato AsyncAPI. */
    record RegistrarSesion(String usuarioId, String sesionId) {
    }
}
