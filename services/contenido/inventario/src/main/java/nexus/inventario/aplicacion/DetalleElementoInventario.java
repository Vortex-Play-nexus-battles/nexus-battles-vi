package nexus.inventario.aplicacion;

import java.util.UUID;

/** Datos estables que Inventario publica para integraciones entre servicios. */
public record DetalleElementoInventario(
        String elementoId,
        UUID productoId,
        UUID propietarioUid,
        boolean enUso,
        boolean disponible,
        UUID subastaId) {
}
