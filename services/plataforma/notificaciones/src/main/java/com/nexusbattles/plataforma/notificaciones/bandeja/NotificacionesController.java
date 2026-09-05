package com.nexusbattles.plataforma.notificaciones.bandeja;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * API HTTP de la bandeja, segun contracts/openapi/notificaciones.yaml.
 *
 * <p>Aqui vive lo que se consulta y lo que se marca. La entrega en tiempo real
 * viaja por STOMP y no pasa por este controlador.
 *
 * <p>El identificador del jugador llega en la ruta mientras se acuerda con el
 * modulo de identidad que claim del token lo aporta. Cuando eso se confirme, el
 * resource server entra igual que en moderacion-sanciones y el contrato abre
 * version nueva.
 */
@RestController
@RequestMapping("/api/v1")
public class NotificacionesController {

    private final ServicioDeNotificaciones servicio;

    public NotificacionesController(ServicioDeNotificaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/users/{usuarioId}/notifications")
    public BandejaResponse consultarBandeja(@PathVariable String usuarioId) {
        BandejaDeNotificaciones bandeja = servicio.consultar(usuarioId);
        List<AvisoResponse> avisos = bandeja.historial().stream()
                .map(aviso -> AvisoResponse.desde(aviso, bandeja.estaLeida(aviso.id())))
                .toList();
        return new BandejaResponse(usuarioId, bandeja.noLeidas(), avisos);
    }

    @PostMapping("/users/{usuarioId}/notifications/{notificacionId}/read")
    public ContadorResponse marcarLeida(
            @PathVariable String usuarioId, @PathVariable String notificacionId) {
        return new ContadorResponse(usuarioId, servicio.marcarLeida(usuarioId, notificacionId));
    }

    @PostMapping("/users/{usuarioId}/sessions/{sesionId}/pending")
    public List<AvisoResponse> entregarPendientes(
            @PathVariable String usuarioId, @PathVariable String sesionId) {
        BandejaDeNotificaciones bandeja = servicio.consultar(usuarioId);
        return servicio.registrarSesion(usuarioId, sesionId).stream()
                .map(aviso -> AvisoResponse.desde(aviso, bandeja.estaLeida(aviso.id())))
                .toList();
    }

    @PostMapping("/internal/notifications")
    public ResponseEntity<EntregaResponse> emitir(
            @RequestBody EmitirNotificacionRequest request) {

        Notificacion aviso = new Notificacion(
                request.id(), request.tipo(), request.titulo(),
                request.cuerpo(), request.creadaEn());

        Set<String> notificadas = servicio.emitir(request.usuarioId(), aviso);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new EntregaResponse(
                        AvisoResponse.desde(aviso, false), List.copyOf(notificadas)));
    }

    /** Cuerpo del alta de un evento notificable, segun el contrato. */
    public record EmitirNotificacionRequest(
            String usuarioId, String id, String tipo, String titulo,
            String cuerpo, Instant creadaEn) {
    }

    /** Un aviso tal como lo ve el jugador, con su estado de lectura. */
    public record AvisoResponse(
            String id, String tipo, String titulo, String cuerpo,
            Instant creadaEn, boolean leida) {

        static AvisoResponse desde(Notificacion aviso, boolean leida) {
            return new AvisoResponse(aviso.id(), aviso.tipo(), aviso.titulo(),
                    aviso.cuerpo(), aviso.creadaEn(), leida);
        }
    }

    /** Bandeja completa con la cuenta de no leidos. */
    public record BandejaResponse(String usuarioId, int noLeidas, List<AvisoResponse> avisos) {
    }

    /** Cuenta de no leidos, igual en todas las sesiones del jugador. */
    public record ContadorResponse(String usuarioId, int noLeidas) {
    }

    /** Resultado de emitir: el aviso y a que sesiones alcanzo a llegar. */
    public record EntregaResponse(AvisoResponse aviso, List<String> sesionesNotificadas) {
    }
}
