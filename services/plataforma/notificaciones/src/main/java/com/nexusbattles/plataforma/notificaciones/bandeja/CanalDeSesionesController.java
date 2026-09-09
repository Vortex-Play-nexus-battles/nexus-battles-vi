package com.nexusbattles.plataforma.notificaciones.bandeja;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
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

    /**
     * Errores del canal de vuelta a quien envio el mensaje, en el mismo
     * formato problem details de la API HTTP, como declara el mensaje
     * errorDeCanal del contrato. broadcast en false para que llegue solo a
     * la conexion que fallo y no a todas las sesiones del jugador.
     */
    @MessageExceptionHandler(RuntimeException.class)
    @SendToUser(destinations = "/cola/notificaciones", broadcast = false)
    public ProblemDetail manejarErrorDeCanal(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Cuerpo del alta de sesion, segun el contrato AsyncAPI. */
    record RegistrarSesion(String usuarioId, String sesionId) {
    }
}
