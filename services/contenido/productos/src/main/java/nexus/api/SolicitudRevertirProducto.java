package nexus.api;

import jakarta.validation.constraints.NotBlank;

public record SolicitudRevertirProducto(
        @NotBlank(message = "respaldoId es obligatorio") String respaldoId) {
}
