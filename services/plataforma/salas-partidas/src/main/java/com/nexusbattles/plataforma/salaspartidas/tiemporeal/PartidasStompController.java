package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.comun.error.ErrorDeNegocio;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import com.nexusbattles.plataforma.resiliencia.ErroresDeDegradacion;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.beans.factory.annotation.Value;
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
 * <p><b>Resuelve la accion de verdad.</b> El cuerpo llega entero —{@code
 * codigoAccion} y {@code idObjetivo}— y el caso de uso pide al motor de combate
 * cuanto dano hace el golpe, lo aplica a la vida que este servicio persiste,
 * anuncia {@code partida.accion.resuelta} y despues pasa el turno.
 *
 * <p>Los rechazos vuelven por la cola privada de quien envio, no al tema de la
 * partida: que a alguien le rechacen una accion no es asunto de sus rivales.
 */
@Controller
public class PartidasStompController {

    private final EjecutarAccion ejecutarAccion;
    private final long reintentarEnSegundos;

    public PartidasStompController(EjecutarAccion ejecutarAccion,
                                   @Value("${resiliencia.reintentar-en-segundos:30}") long reintentarEnSegundos) {
        this.ejecutarAccion = ejecutarAccion;
        this.reintentarEnSegundos = reintentarEnSegundos;
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

        ejecutarAccion.ejecutar(idPartida, jugadorDe(principal),
                cuerpo == null ? null : cuerpo.idObjetivo(),
                cuerpo == null ? null : cuerpo.codigoAccion());
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

    /**
     * HU-DIS-003: el motor de combate no responde. Mismo problem detail que
     * produce {@code ManejadorDeDegradacion} en la API HTTP —{@code type}
     * {@code seccion-no-disponible}, {@code seccion}, {@code reintentarEnSegundos}—
     * para que el navegador lo pinte con el mismo componente ({@code Seccion
     * degradada}) llegue por donde llegue. Por la cola privada: el resto de la
     * partida no tiene por que enterarse de que a este jugador no le salio el
     * golpe.
     */
    @MessageExceptionHandler(DependenciaDegradada.class)
    @SendToUser(destinations = "/cola/salas", broadcast = false)
    public ProblemDetail seccionNoDisponible(DependenciaDegradada degradada) {
        return ErroresDeDegradacion.problema(degradada, reintentarEnSegundos);
    }

    private static UUID jugadorDe(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Jugar un turno necesita un jugador autenticado.");
        }
        return IdentidadDelToken.idDe(token.getToken());
    }
}
