package com.nexusbattles.ms_identidad.admin.inventario;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /admin/usuarios/{usuarioId}/inventario/entregas} (SA-10): que
 * producto, cuantas unidades y por que. Los topes son los de la entrega del
 * inventario (hasta 20 unidades por linea); el motivo, el de las demas
 * acciones administrativas que quedan en la auditoria.
 */
public record EntregaAdministrativaRequest(
        @NotBlank @Size(max = 100) String productoId,
        @NotNull @Min(1) @Max(20) Integer cantidad,
        @NotBlank @Size(min = 3, max = 500) String motivo) {
}
