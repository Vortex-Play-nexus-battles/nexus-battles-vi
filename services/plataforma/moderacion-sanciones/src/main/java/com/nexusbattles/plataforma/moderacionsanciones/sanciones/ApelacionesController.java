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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Panel de revision de apelaciones — HU-USR-007 CA-03/CA-04. */
@RestController
@RequestMapping("/api/v1/apelaciones")
public class ApelacionesController {

    private final SancionesService servicio;

    public ApelacionesController(SancionesService servicio) {
        this.servicio = servicio;
    }

    /** Pendientes para el panel ({@code ?mias=true}: las del propio usuario, con su estado). */
    @GetMapping
    public List<SancionesAdminController.ApelacionResponse> listar(
            @AuthenticationPrincipal Jwt actor, @RequestParam(defaultValue = "false") boolean mias) {
        Actor quien = SancionesAdminController.actorDe(actor);
        List<Apelacion> apelaciones = mias ? servicio.misApelaciones(quien) : servicio.apelacionesPendientes(quien);
        return apelaciones.stream().map(SancionesAdminController.ApelacionResponse::desde).toList();
    }

    @PostMapping("/{apelacionId}/resolucion")
    public SancionesAdminController.ApelacionResponse resolver(@AuthenticationPrincipal Jwt actor,
                                                               @PathVariable UUID apelacionId,
                                                               @RequestBody ResolucionRequest request) {
        return SancionesAdminController.ApelacionResponse.desde(servicio.resolver(
                SancionesAdminController.actorDe(actor), apelacionId, request.decision(), request.motivo(),
                request.nuevaVigencia()));
    }

    public record ResolucionRequest(Apelacion.Estado decision, String motivo, OffsetDateTime nuevaVigencia) {
    }
}
