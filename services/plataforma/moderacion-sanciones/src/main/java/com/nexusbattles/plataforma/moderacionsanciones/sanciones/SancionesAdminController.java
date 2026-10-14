package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Emision y consulta de sanciones, y sus apelaciones — HU-USR-004/005/006/007.
 * Contrato: {@code contracts/openapi/moderacion-sanciones-admin.yaml}.
 *
 * <p>Quien actua sale del token ({@code uid} y {@code rol}); las reglas de
 * rol las aplica {@link SancionesService}, no este controlador.
 */
@RestController
@RequestMapping("/api/v1/sanciones")
public class SancionesAdminController {

    private final SancionesService servicio;
    private final PlazoDeDecision plazo;
    private final Clock reloj;

    public SancionesAdminController(SancionesService servicio, PlazoDeDecision plazo, Clock reloj) {
        this.servicio = servicio;
        this.plazo = plazo;
        this.reloj = reloj;
    }

    static Actor actorDe(Jwt token) {
        String rol = token.getClaimAsString("rol");
        return new Actor(IdentidadDelToken.idDe(token), rol == null ? "" : rol);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SancionResponse emitir(@AuthenticationPrincipal Jwt actor, @RequestBody EmitirSancionRequest request) {
        Sancion sancion = servicio.emitir(actorDe(actor), new SancionesService.SolicitudDeSancion(
                request.usuarioId(), request.tipo(), request.causal(), request.motivo(), request.politica(),
                request.comentarioId(), request.duracionHoras(), Boolean.TRUE.equals(request.confirmacion())));
        return SancionResponse.desde(sancion);
    }

    /**
     * 1.4.0 (SA-4): el listado global con sus filtros, de la mas reciente a la
     * mas antigua. Un valor fuera de su enumerado es 400 (lo convierte Spring).
     */
    @GetMapping
    public PaginaDeSanciones listar(
            @AuthenticationPrincipal Jwt actor,
            @RequestParam(required = false) Sancion.Tipo tipo,
            @RequestParam(required = false) Causal causal,
            @RequestParam(required = false) FiltroDeSanciones.Estado estado,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "16") int tamano) {
        Page<Sancion> resultado = servicio.buscar(actorDe(actor),
                new FiltroDeSanciones(tipo, causal, estado, usuarioId, desde, hasta), pagina, tamano);
        return new PaginaDeSanciones(resultado.getContent().stream().map(SancionResponse::desde).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/usuarios/{usuarioId}")
    public List<SancionResponse> historial(@AuthenticationPrincipal Jwt actor, @PathVariable UUID usuarioId) {
        return servicio.historialDe(actorDe(actor), usuarioId).stream().map(SancionResponse::desde).toList();
    }

    @GetMapping("/{sancionId}")
    public SancionResponse obtener(@AuthenticationPrincipal Jwt actor, @PathVariable UUID sancionId) {
        return SancionResponse.desde(servicio.obtener(actorDe(actor), sancionId));
    }

    @PostMapping("/{sancionId}/apelaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public ApelacionResponse apelar(@AuthenticationPrincipal Jwt actor, @PathVariable UUID sancionId,
                                    @RequestBody ApelarRequest request) {
        return ApelacionResponse.desde(servicio.apelar(actorDe(actor), sancionId, request.argumento()), plazo,
                OffsetDateTime.now(reloj));
    }

    /** 1.1.0: un administrador levanta una sancion vigente sin apelacion. */
    @PostMapping("/{sancionId}/levantamiento")
    public SancionResponse levantar(@AuthenticationPrincipal Jwt actor, @PathVariable UUID sancionId,
                                    @RequestBody LevantamientoRequest request) {
        return SancionResponse.desde(servicio.levantar(actorDe(actor), sancionId, request.motivo()));
    }

    public record EmitirSancionRequest(UUID usuarioId, Sancion.Tipo tipo, Causal causal, String motivo,
                                       String politica, String comentarioId, Long duracionHoras,
                                       Boolean confirmacion) {
    }

    /** 1.4.0: una pagina del listado global. */
    public record PaginaDeSanciones(List<SancionResponse> contenido, int pagina, int tamano, long total,
                                    int totalPaginas) {
    }

    public record ApelarRequest(String argumento) {
    }

    public record LevantamientoRequest(String motivo) {
    }

    public record SancionResponse(UUID id, UUID usuarioId, Sancion.Tipo tipo, Causal causal, String motivo,
                                  String politica, String comentarioId, UUID emitidaPor, String rolEmisor,
                                  OffsetDateTime emitidaEn, OffsetDateTime vigenteHasta, OffsetDateTime revertidaEn,
                                  String motivoReversion, boolean vigente) {
        static SancionResponse desde(Sancion s) {
            return new SancionResponse(s.id(), s.usuarioId(), s.tipo(), s.causal(), s.motivo(), s.politica(),
                    s.comentarioId(), s.emitidaPor(), s.rolEmisor(), s.emitidaEn(), s.vigenteHasta(),
                    s.revertidaEn(), s.motivoReversion(), s.estaVigenteEn(OffsetDateTime.now()));
        }
    }

    /**
     * Una apelacion. 1.4.0 (SA-4): {@code decisionAntesDe} es el final del
     * decimoquinto dia habil desde que se presento (§7.3.2) y {@code vencida}
     * dice si, seguir pendiente, ya va fuera de ese plazo.
     */
    public record ApelacionResponse(UUID id, UUID sancionId, UUID usuarioId, String argumento, OffsetDateTime creadaEn,
                                    Apelacion.Estado estado, String decisionMotivo, UUID resueltaPor,
                                    OffsetDateTime resueltaEn, OffsetDateTime nuevaVigencia,
                                    OffsetDateTime decisionAntesDe, boolean vencida) {
        static ApelacionResponse desde(Apelacion a, PlazoDeDecision plazo, OffsetDateTime ahora) {
            return new ApelacionResponse(a.id(), a.sancionId(), a.usuarioId(), a.argumento(), a.creadaEn(), a.estado(),
                    a.decisionMotivo(), a.resueltaPor(), a.resueltaEn(), a.nuevaVigencia(),
                    plazo.limitePara(a.creadaEn()),
                    a.estado() == Apelacion.Estado.PENDIENTE && plazo.vencida(a.creadaEn(), ahora));
        }
    }
}
