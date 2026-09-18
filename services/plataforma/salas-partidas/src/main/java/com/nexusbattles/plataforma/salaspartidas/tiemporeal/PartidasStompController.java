package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.comun.error.ErrorDeNegocio;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AvanzarTurno;
import com.nexusbattles.plataforma.salaspartidas.seguridad.IdentidadDelToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Entrada del jugador al combate por STOMP — RF-JUE-017.
 *
 * <p>Atiende el canal {@code accionDelJugador} que ya declara
 * {@code contracts/websocket/salas-partidas.yaml} en
 * {@code /app/partidas/{idPartida}/acciones}. No se abre un destino nuevo: el
 * contrato ya tenia este y su mensaje {@code EjecutarAccion}.
 *
 * <p><b>Hoy solo pasa el turno.</b> El cuerpo se acepta entero —{@code
 * codigoAccion} y {@code idObjetivo}— pero la accion no se resuelve: el dano y
 * los efectos son del motor de combate, fuera de este bloque. Cuando publique
 * su contrato se intercala aqui: resolver, anunciar
 * {@code partida.accion.resuelta}, y despues rotar el turno. Aceptar el cuerpo
 * completo desde ya evita que el cliente tenga que cambiar el dia que eso
 * ocurra.
 *
 * <p>Los rechazos vuelven por la cola privada de quien envio, no al tema de la
 * partida: que a alguien le rechacen una accion no es asunto de sus rivales.
 */
@Controller
public class PartidasStompController {

    private final AvanzarTurno avanzarTurno;

    public PartidasStompController(AvanzarTurno avanzarTurno) {
        this.avanzarTurno = avanzarTurno;
    }

    /**
     * El jugador juega su turno.
     *
     * <p>El identificador sale del token y nunca del cuerpo: si viajara en el
     * mensaje, cualquiera podria jugar el turno de otro escribiendo su UUID.
     */
    @MessageMapping("/partidas/{idPartida}/acciones")
    public void ejecutarAccion(@DestinationVariable UUID idPartida,
                               @Payload(required = false) EjecutarAccionRequest cuerpo,
                               Principal principal) {

        avanzarTurno.ejecutar(idPartida, jugadorDe(principal));
    }

    /**
     * Mismo formato que la API HTTP (regla 4), por la cola privada del jugador:
     * es el mensaje {@code errorDeCanal} del contrato.
     */
    @MessageExceptionHandler(ErrorDeNegocio.class)
    @SendToUser(destinations = "/cola/salas", broadcast = false)
    public ProblemDetail errorDeNegocio(ErrorDeNegocio error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(error.estado()), error.detalle());
        problema.setType(error.tipo());
        problema.setTitle(error.titulo());
        return problema;
    }

    private static UUID jugadorDe(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Jugar un turno necesita un jugador autenticado.");
        }
        return IdentidadDelToken.idDe(token.getToken());
    }
}
