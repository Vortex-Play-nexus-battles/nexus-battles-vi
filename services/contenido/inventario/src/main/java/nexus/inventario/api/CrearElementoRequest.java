package nexus.inventario.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;

public record CrearElementoRequest(
        @NotBlank String productoId,
        @NotNull TipoElementoInventario tipo,
        @NotBlank @Size(max = ModificarElementoRequest.MAXIMO_NOMBRE) String nombrePropio,
        ParteArmadura parteArmadura) {
}
