package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    public SancionesAdminController(SancionesService servicio) {
        this.servicio = servicio;
    }

    static Actor actorDe(Jwt token) {
        String rol = token.getClaimAsString("rol");
        return new Actor(IdentidadDelToken.idDe(token), rol == null ? "" : rol);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SancionResponse emitir(@AuthenticationPrincipal Jwt actor, @RequestBody EmitirSancionRequest request) {
        Sancion sancion = servicio.emitir(actorDe(actor), new SancionesService.SolicitudDeSancion(
                request.usuarioId(), request.tipo(), request.motivo(), request.politica(), request.comentarioId(),
                request.duracionHoras(), Boolean.TRUE.equals(request.confirmacion())));
        return SancionResponse.desde(sancion);
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
        return ApelacionResponse.desde(servicio.apelar(actorDe(actor), sancionId, request.argumento()));
    }

    public record EmitirSancionRequest(UUID usuarioId, Sancion.Tipo tipo, String motivo, String politica,
                                       String comentarioId, Long duracionHoras, Boolean confirmacion) {
    }

    public record ApelarRequest(String argumento) {
    }

    public record SancionResponse(UUID id, UUID usuarioId, Sancion.Tipo tipo, String motivo, String politica,
                                  String comentarioId, UUID emitidaPor, String rolEmisor, OffsetDateTime emitidaEn,
                                  OffsetDateTime vigenteHasta, OffsetDateTime revertidaEn, String motivoReversion,
                                  boolean vigente) {
        static SancionResponse desde(Sancion s) {
            return new SancionResponse(s.id(), s.usuarioId(), s.tipo(), s.motivo(), s.politica(), s.comentarioId(),
                    s.emitidaPor(), s.rolEmisor(), s.emitidaEn(), s.vigenteHasta(), s.revertidaEn(),
                    s.motivoReversion(), s.estaVigenteEn(OffsetDateTime.now()));
        }
    }

    public record ApelacionResponse(UUID id, UUID sancionId, UUID usuarioId, String argumento, OffsetDateTime creadaEn,
                                    Apelacion.Estado estado, String decisionMotivo, UUID resueltaPor,
                                    OffsetDateTime resueltaEn, OffsetDateTime nuevaVigencia) {
        static ApelacionResponse desde(Apelacion a) {
            return new ApelacionResponse(a.id(), a.sancionId(), a.usuarioId(), a.argumento(), a.creadaEn(), a.estado(),
                    a.decisionMotivo(), a.resueltaPor(), a.resueltaEn(), a.nuevaVigencia());
        }
    }
}
