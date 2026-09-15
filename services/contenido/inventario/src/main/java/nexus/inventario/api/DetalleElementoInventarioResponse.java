package nexus.inventario.api;

import java.util.UUID;
import nexus.inventario.aplicacion.DetalleElementoInventario;

public record DetalleElementoInventarioResponse(
        String elementoId,
        UUID productoId,
        UUID propietarioUid,
        boolean enUso,
        boolean disponible,
        UUID subastaId) {

    static DetalleElementoInventarioResponse de(DetalleElementoInventario detalle) {
        return new DetalleElementoInventarioResponse(
                detalle.elementoId(), detalle.productoId(), detalle.propietarioUid(),
                detalle.enUso(), detalle.disponible(), detalle.subastaId());
    }
}
