package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Panel de revision de apelaciones — HU-USR-007 CA-03/CA-04. */
@RestController
@RequestMapping("/api/v1/apelaciones")
public class ApelacionesController {

    private final SancionesService servicio;
    private final PlazoDeDecision plazo;
    private final Clock reloj;

    public ApelacionesController(SancionesService servicio, PlazoDeDecision plazo, Clock reloj) {
        this.servicio = servicio;
        this.plazo = plazo;
        this.reloj = reloj;
    }

    /**
     * El panel ({@code ?mias=true}: las del propio usuario, con su estado).
     * 1.4.0 (SA-4): {@code estado} elige las pendientes (por omision), las
     * resueltas o todas; cada una con su fecha limite de decision.
     */
    @GetMapping
    public List<SancionesAdminController.ApelacionResponse> listar(
            @AuthenticationPrincipal Jwt actor, @RequestParam(defaultValue = "false") boolean mias,
            @RequestParam(required = false) SancionesService.EstadoDeLaLista estado) {
        Actor quien = SancionesAdminController.actorDe(actor);
        List<Apelacion> apelaciones = mias ? servicio.misApelaciones(quien)
                : servicio.apelacionesDelPanel(quien, estado);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        return apelaciones.stream()
                .map(a -> SancionesAdminController.ApelacionResponse.desde(a, plazo, ahora))
                .toList();
    }

    @PostMapping("/{apelacionId}/resolucion")
    public SancionesAdminController.ApelacionResponse resolver(@AuthenticationPrincipal Jwt actor,
                                                               @PathVariable UUID apelacionId,
                                                               @RequestBody ResolucionRequest request) {
        return SancionesAdminController.ApelacionResponse.desde(servicio.resolver(
                SancionesAdminController.actorDe(actor), apelacionId, request.decision(), request.motivo(),
                request.nuevaVigencia()), plazo, OffsetDateTime.now(reloj));
    }

    public record ResolucionRequest(Apelacion.Estado decision, String motivo, OffsetDateTime nuevaVigencia) {
    }
}
