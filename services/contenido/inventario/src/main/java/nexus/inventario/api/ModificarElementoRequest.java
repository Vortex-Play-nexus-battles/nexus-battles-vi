package nexus.inventario.api;

import jakarta.validation.constraints.NotBlank;

public record ModificarElementoRequest(@NotBlank String nombrePropio) {
}
