package nexus.inventario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nexus.inventario.dominio.TipoElementoInventario;

public record CrearElementoRequest(
        @NotBlank String productoId,
        @NotNull TipoElementoInventario tipo,
        @NotBlank String nombrePropio) {
}
