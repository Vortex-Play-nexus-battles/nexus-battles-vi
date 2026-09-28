package nexus.inventario.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import nexus.inventario.aplicacion.GestionarBloqueoMision;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El heroe en mision (inventario.yaml 1.6.0, B9). Solo el servicio de misiones
 * llega aqui: {@code SeguridadConfig} lo comprueba por su {@code azp}.
 */
@RestController
@RequestMapping("/api/v1/inventario/elementos/{elementoId}/bloqueo-mision")
public class BloqueoMisionController {

    private final GestionarBloqueoMision gestion;

    public BloqueoMisionController(GestionarBloqueoMision gestion) {
        this.gestion = gestion;
    }

    @PutMapping
    public ElementoInventarioResponse bloquear(
            @RequestHeader(name = "Idempotency-Key", required = false) String claveIdempotencia,
            @PathVariable String elementoId,
            @Valid @RequestBody BloquearEnMisionRequest solicitud) {
        return ElementoInventarioResponse.de(gestion.bloquear(
                solicitud.propietarioUid(), elementoId, solicitud.ejecucionId(), claveIdempotencia));
    }

    @PostMapping("/{ejecucionId}/liberacion")
    public ElementoInventarioResponse liberar(
            @RequestHeader(name = "Idempotency-Key", required = false) String claveIdempotencia,
            @PathVariable String elementoId,
            @PathVariable UUID ejecucionId,
            @Valid @RequestBody LiberarDeMisionRequest solicitud) {
        return ElementoInventarioResponse.de(gestion.liberar(solicitud.propietarioUid(), elementoId, ejecucionId,
                solicitud.experiencia() == null ? 0 : solicitud.experiencia(), claveIdempotencia));
    }

    /** {@code BloquearEnMision} del contrato. */
    public record BloquearEnMisionRequest(@NotNull UUID propietarioUid, @NotNull UUID ejecucionId) {
    }

    /** {@code LiberarDeMision} del contrato; sin experiencia, 0 (cancelar). */
    public record LiberarDeMisionRequest(@NotNull UUID propietarioUid, @PositiveOrZero Double experiencia) {
    }
}
