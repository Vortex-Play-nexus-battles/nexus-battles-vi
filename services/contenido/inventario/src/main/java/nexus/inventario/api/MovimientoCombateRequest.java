package nexus.inventario.api;

import jakarta.validation.constraints.NotBlank;

public record MovimientoCombateRequest(
        @NotBlank String elementoId,
        @NotBlank String propietarioOrigenId,
        @NotBlank String heroeOrigenId,
        @NotBlank String propietarioDestinoId) {
}
