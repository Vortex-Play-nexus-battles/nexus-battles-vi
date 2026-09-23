package com.nexusbattles.plataforma.torneos.torneo;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** {@code /api/v1/torneos} — contracts/openapi/torneos.yaml 1.0.0. */
@RestController
@RequestMapping("/api/v1/torneos")
public class TorneosController {

    private final TorneosService servicio;

    public TorneosController(TorneosService servicio) {
        this.servicio = servicio;
    }

    static Actor actorDe(Jwt token) {
        String rol = token.getClaimAsString("rol");
        if (Actor.SERVICIO.equals(rol)) {
            String cliente = token.getClaimAsString("azp");
            return Actor.servicio(cliente == null || cliente.isBlank() ? token.getSubject() : cliente);
        }
        return Actor.usuario(IdentidadDelToken.idDe(token), rol == null ? "" : rol);
    }

    @GetMapping
    public List<TorneoResumenResponse> listar() {
        return servicio.listar().stream().map(TorneoResumenResponse::desde).toList();
    }

    @GetMapping("/{torneoId}")
    public TorneoResponse obtener(@PathVariable UUID torneoId) {
        return TorneoResponse.desde(servicio.obtener(torneoId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TorneoResponse crear(@AuthenticationPrincipal Jwt actor, @RequestBody CrearTorneoRequest request) {
        return TorneoResponse.desde(servicio.crear(actorDe(actor), new TorneosService.SolicitudDeTorneo(
                request.nombre(), request.inscripcionesCierranEn(), request.costoInscripcion())));
    }

    @PostMapping("/{torneoId}/cancelacion")
    public TorneoResponse cancelar(@AuthenticationPrincipal Jwt actor, @PathVariable UUID torneoId,
                                   @RequestBody MotivoRequest request) {
        return TorneoResponse.desde(servicio.cancelar(actorDe(actor), torneoId, request.motivo()));
    }

    @PostMapping("/{torneoId}/equipos")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoResponse crearEquipo(@AuthenticationPrincipal Jwt actor, @PathVariable UUID torneoId,
                                      @RequestBody CrearEquipoRequest request) {
        return EquipoResponse.desde(servicio.crearEquipo(actorDe(actor), torneoId,
                new TorneosService.SolicitudDeEquipo(request.nombre(), request.avatar(), request.companeroUid())));
    }

    @PutMapping("/{torneoId}/equipos/{equipoId}/integrantes")
    public EquipoResponse sustituir(@AuthenticationPrincipal Jwt actor, @PathVariable UUID torneoId,
                                    @PathVariable UUID equipoId, @RequestBody CompaneroRequest request) {
        return EquipoResponse.desde(servicio.sustituirCompanero(actorDe(actor), torneoId, equipoId, request.companeroUid()));
    }

    @PostMapping("/{torneoId}/equipos/{equipoId}/inscripcion")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoResponse inscribir(@AuthenticationPrincipal Jwt actor, @PathVariable UUID torneoId,
                                    @PathVariable UUID equipoId) {
        return EquipoResponse.desde(servicio.inscribir(actorDe(actor), torneoId, equipoId));
    }

    @PostMapping("/{torneoId}/inicio")
    public TorneoResponse iniciar(@AuthenticationPrincipal Jwt actor, @PathVariable UUID torneoId) {
        return TorneoResponse.desde(servicio.iniciar(actorDe(actor), torneoId));
    }

    @PostMapping("/{torneoId}/encuentros/{numero}/resultado")
    public TorneoResponse resultado(@AuthenticationPrincipal Jwt actor, @PathVariable UUID torneoId,
                                    @PathVariable int numero, @RequestBody ResultadoRequest request) {
        return TorneoResponse.desde(servicio.registrarResultado(actorDe(actor), torneoId, numero,
                new TorneosService.SolicitudDeResultado(request.ganadorEquipoId(), request.ganadorUid(),
                        request.partidaId(), request.motivo())));
    }

    // ---- formas del contrato ----------------------------------------------

    public record CrearTorneoRequest(String nombre, OffsetDateTime inscripcionesCierranEn, Integer costoInscripcion) { }

    public record MotivoRequest(String motivo) { }

    public record CrearEquipoRequest(String nombre, String avatar, UUID companeroUid) { }

    public record CompaneroRequest(UUID companeroUid) { }

    public record ResultadoRequest(UUID ganadorEquipoId, UUID ganadorUid, UUID partidaId, String motivo) { }

    public record TorneoResumenResponse(UUID id, String nombre, Torneo.Estado estado, OffsetDateTime creadoEn,
                                        OffsetDateTime inscripcionesCierranEn, int costoInscripcion,
                                        long equiposInscritos, int cupos, UUID campeonEquipoId) {
        static TorneoResumenResponse desde(TorneosService.TorneoCompleto t) {
            return new TorneoResumenResponse(t.torneo().id(), t.torneo().nombre(), t.torneo().estado(),
                    t.torneo().creadoEn(), t.torneo().inscripcionesCierranEn(), t.torneo().costoInscripcion(),
                    t.inscritos(), Torneo.CUPOS, t.torneo().campeonEquipoId());
        }
    }

    public record TorneoResponse(UUID id, String nombre, Torneo.Estado estado, OffsetDateTime creadoEn,
                                 OffsetDateTime inscripcionesCierranEn, int costoInscripcion, long equiposInscritos,
                                 int cupos, UUID campeonEquipoId, UUID creadoPor, OffsetDateTime iniciadoEn,
                                 OffsetDateTime finalizadoEn, String motivoCancelacion,
                                 List<EquipoResponse> equipos, List<EncuentroResponse> encuentros) {
        static TorneoResponse desde(TorneosService.TorneoCompleto t) {
            Torneo torneo = t.torneo();
            return new TorneoResponse(torneo.id(), torneo.nombre(), torneo.estado(), torneo.creadoEn(),
                    torneo.inscripcionesCierranEn(), torneo.costoInscripcion(), t.inscritos(), Torneo.CUPOS,
                    torneo.campeonEquipoId(), torneo.creadoPor(), torneo.iniciadoEn(), torneo.finalizadoEn(),
                    torneo.motivoCancelacion(),
                    t.equipos().stream().map(EquipoResponse::desde).toList(),
                    t.encuentros().stream().map(EncuentroResponse::desde).toList());
        }
    }

    public record EquipoResponse(UUID id, UUID torneoId, String nombre, String avatar, boolean ia, UUID capitanUid,
                                 List<UUID> integrantes, boolean inscrito, UUID pagadoPor, UUID reservaId,
                                 Integer posicion, int derrotas, boolean eliminado) {
        static EquipoResponse desde(Equipo e) {
            return new EquipoResponse(e.id(), e.torneoId(), e.nombre(), e.avatar(), e.ia(), e.capitanUid(),
                    e.integrantes(), e.inscrito(), e.pagadoPor(), e.reservaId(), e.posicion(), e.derrotas(),
                    e.eliminado());
        }
    }

    public record EncuentroResponse(int numero, Encuentro.Llave llave, int ronda, UUID equipoA, UUID equipoB,
                                    UUID ganador, UUID partidaId, Encuentro.Estado estado, String registradoPor,
                                    String motivo, OffsetDateTime jugadoEn) {
        static EncuentroResponse desde(Encuentro e) {
            return new EncuentroResponse(e.numero(), e.llave(), e.ronda(), e.equipoA(), e.equipoB(), e.ganador(),
                    e.partidaId(), e.estado(), e.registradoPor(), e.motivo(), e.jugadoEn());
        }
    }
}
