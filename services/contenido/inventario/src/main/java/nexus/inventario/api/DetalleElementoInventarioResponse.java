package nexus.inventario.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import nexus.inventario.aplicacion.DetalleElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * {@code DetalleElementoInventario} del contrato. Los campos de 1.6.0 (B9) son
 * opcionales: si no hay valor, no aparecen.
 */
public record DetalleElementoInventarioResponse(
        String elementoId,
        UUID productoId,
        UUID propietarioUid,
        boolean enUso,
        boolean disponible,
        UUID subastaId,
        @JsonInclude(JsonInclude.Include.NON_NULL) TipoElementoInventario tipo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nombrePropio,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer nivel,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double experiencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID ejecucionMisionId) {

    static DetalleElementoInventarioResponse de(DetalleElementoInventario detalle) {
        return new DetalleElementoInventarioResponse(
                detalle.elementoId(), detalle.productoId(), detalle.propietarioUid(),
                detalle.enUso(), detalle.disponible(), detalle.subastaId(), detalle.tipo(),
                detalle.nombrePropio(), detalle.nivel(), detalle.experiencia(), detalle.ejecucionMisionId());
    }
}
