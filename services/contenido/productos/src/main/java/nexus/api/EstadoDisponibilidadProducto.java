package nexus.api;

import nexus.dominio.EstadoProducto;
import nexus.productos.dominio.DisponibilidadProducto;

/**
 * Respuesta de suspender y reactivar (esquema {@code EstadoDisponibilidadProducto}):
 * el estado en que quedo el producto y su tiraje, que ninguna de las dos
 * operaciones toca (-1 ilimitado, 0 agotado, positivo unidades restantes).
 */
public record EstadoDisponibilidadProducto(String productoId, EstadoProducto estado, int tiraje) {

        public static EstadoDisponibilidadProducto de(DisponibilidadProducto disponibilidad) {
                return new EstadoDisponibilidadProducto(
                        disponibilidad.productoId(),
                        disponibilidad.estado(),
                        disponibilidad.unidadesDisponibles());
        }
}
