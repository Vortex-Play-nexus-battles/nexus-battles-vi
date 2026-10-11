package nexus.api;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Formato portable del diseño actualmente soportado por Productos.
 *
 * <p>La versión del formato permite que HU-PRD-009 añada recursos gráficos
 * sin romper las plantillas ya exportadas. La plantilla no contiene identidad,
 * precios, tiraje ni reglas de combate del producto destino.
 */
public record PlantillaDisenoProducto(
                @NotBlank String formato,
                @NotNull @Min(1) Integer versionFormato,
                @NotBlank String productoOrigenId,
                @NotNull @Min(1) Integer versionProducto,
                @NotBlank String imagen,
                @NotBlank String descripcion,
                @NotNull Instant exportadaEn) {

        public static final String FORMATO = "nexus-producto-diseno";
        public static final int VERSION_FORMATO = 1;

        public static PlantillaDisenoProducto de(nexus.dominio.Producto producto, Instant exportadaEn) {
                return new PlantillaDisenoProducto(
                                FORMATO,
                                VERSION_FORMATO,
                                producto.id(),
                                producto.version(),
                                producto.imagen(),
                                producto.descripcion(),
                                exportadaEn);
        }

        @AssertTrue(message = "El formato de la plantilla de diseño no es compatible")
        public boolean isFormatoCompatible() {
                return FORMATO.equals(formato) && Integer.valueOf(VERSION_FORMATO).equals(versionFormato);
        }
}
