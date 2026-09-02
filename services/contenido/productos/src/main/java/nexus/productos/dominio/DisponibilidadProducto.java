package nexus.productos.dominio;

import java.util.Objects;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;

public final class DisponibilidadProducto {

    public static final int TIRAJE_ILIMITADO = -1;

    private final String productoId;
    private final int unidadesDisponibles;
    private final EstadoProducto estado;

    private DisponibilidadProducto(
            String productoId,
            int unidadesDisponibles,
            EstadoProducto estado) {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("El identificador del producto es obligatorio");
        }
        if (unidadesDisponibles < TIRAJE_ILIMITADO) {
            throw new IllegalArgumentException("Las existencias no pueden ser menores que -1");
        }
        this.productoId = productoId;
        this.unidadesDisponibles = unidadesDisponibles;
        this.estado = Objects.requireNonNull(estado, "El estado del producto es obligatorio");
    }

    public static DisponibilidadProducto nueva(
            String productoId,
            int tiraje,
            EstadoProducto estadoInicial) {
        if (tiraje != TIRAJE_ILIMITADO && tiraje <= 0) {
            throw new IllegalArgumentException(
                    "El tiraje debe ser -1 o un número entero positivo");
        }
        return new DisponibilidadProducto(productoId, tiraje, estadoInicial);
    }

    public static DisponibilidadProducto desde(Producto producto) {
        Objects.requireNonNull(producto, "El producto es obligatorio");
        return new DisponibilidadProducto(
                producto.id(),
                producto.tiraje(),
                producto.estado());
    }

    public String productoId() {
        return productoId;
    }

    public int unidadesDisponibles() {
        return unidadesDisponibles;
    }

    public EstadoProducto estado() {
        return estado;
    }

    public boolean esIlimitado() {
        return unidadesDisponibles == TIRAJE_ILIMITADO;
    }

    public boolean estaAgotado() {
        return unidadesDisponibles == 0;
    }

    public DisponibilidadProducto consumirUnidad() {
        if (esIlimitado()) {
            return this;
        }
        if (estaAgotado()) {
            throw new IllegalStateException("El producto está agotado");
        }
        return new DisponibilidadProducto(
                productoId,
                unidadesDisponibles - 1,
                estado);
    }
}
