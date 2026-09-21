package com.nexusbattles.plataforma.notificaciones.bandeja;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * Recibe el alta de sesion que llega por STOMP a /app/notificaciones/sesion.
 *
 * <p>El cliente envia su identificador de sesion estable, el que sobrevive a
 * la reconexion, y el servicio le responde por la cola privada con lo que se
 * perdio. El identificador de sesion de STOMP no sirve para esto porque se
 * renueva en cada reconexion.
 *
 * <p><b>El usuario es el de la conexion</b>, el que dejo {@code AutenticacionStomp}
 * al validar el JWT del CONNECT; el {@code usuarioId} del cuerpo queda
 * obsoleto y se ignora, porque nadie puede dar de alta una sesion a nombre
 * de otro. El identificador de sesion se guarda en los atributos de la
 * conexion para que {@link CicloDeVidaDeSesiones} sepa cual cerrar cuando la
 * conexion se caiga.
 */
@Controller
class CanalDeSesionesController {

    /** Atributo de la conexion STOMP con el identificador de sesion del cliente. */
    static final String ATRIBUTO_SESION = "sesionId";

    private final ServicioDeNotificaciones servicio;

    CanalDeSesionesController(ServicioDeNotificaciones servicio) {
        this.servicio = servicio;
    }

    @MessageMapping("/notificaciones/sesion")
    public void registrarSesion(RegistrarSesion mensaje, Principal usuario, SimpMessageHeaderAccessor cabeceras) {
        if (mensaje == null || mensaje.sesionId() == null || mensaje.sesionId().isBlank()) {
            throw new IllegalArgumentException("El alta de sesion necesita un identificador de sesion.");
        }
        String usuarioId = usuarioDe(usuario);
        Map<String, Object> atributos = cabeceras.getSessionAttributes();
        if (atributos != null) {
            atributos.put(ATRIBUTO_SESION, mensaje.sesionId());
        }
        servicio.registrarSesion(usuarioId, mensaje.sesionId());
    }

    /**
     * Identificador estable del usuario de la conexion. La cadena del canal
     * ya rechazo el CONNECT sin token, asi que aqui siempre hay usuario; si
     * no lo hubiera, se dice, no se inventa.
     */
    static String usuarioDe(Principal usuario) {
        if (usuario instanceof Authentication autenticacion) {
            return IdentidadDelToken.idDe(autenticacion).toString();
        }
        if (usuario == null || usuario.getName() == null || usuario.getName().isBlank()) {
            throw new IllegalArgumentException("La conexion no tiene usuario autenticado.");
        }
        return usuario.getName();
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

    /**
     * Cuerpo del alta de sesion, segun el contrato AsyncAPI.
     *
     * @param usuarioId obsoleto desde la 1.1.0: se ignora, el usuario es el de la conexion
     * @param sesionId  identificador de sesion del cliente, estable entre reconexiones
     */
    record RegistrarSesion(@Deprecated String usuarioId, String sesionId) {
    }
}
