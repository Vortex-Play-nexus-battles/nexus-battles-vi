package nexus.productos.dominio;

import java.util.Objects;

/**
 * Proyección del producto que gobierna su tiraje y su ciclo de disponibilidad.
 *
 * <p>Se mantiene separada del modelo completo de HU-PRD-001 para que pueda
 * integrarse con ese contrato sin duplicar nombre, precios ni atributos por
 * tipo. El valor {@code -1} representa existencias ilimitadas.</p>
 */
public final class DisponibilidadProducto {

    public static final int TIRAJE_ILIMITADO = -1;

    private final String productoId;
    private final int unidadesDisponibles;
    private final EstadoProducto estado;
    private final EstadoProducto estadoAlReactivar;

    private DisponibilidadProducto(
            String productoId,
            int unidadesDisponibles,
            EstadoProducto estado,
            EstadoProducto estadoAlReactivar) {
        if (productoId == null || productoId.isBlank()) {
            throw new IllegalArgumentException("El identificador del producto es obligatorio");
        }
        if (unidadesDisponibles < TIRAJE_ILIMITADO) {
            throw new IllegalArgumentException("Las existencias no pueden ser menores que -1");
        }
        this.productoId = productoId;
        this.unidadesDisponibles = unidadesDisponibles;
        this.estado = Objects.requireNonNull(estado, "El estado del producto es obligatorio");
        this.estadoAlReactivar = Objects.requireNonNull(
                estadoAlReactivar,
                "El estado de reactivación es obligatorio");
    }

    /** Registra el tiraje inicial exigido por HU-PRD-002. */
    public static DisponibilidadProducto nueva(
            String productoId,
            int tiraje,
            EstadoProducto estadoInicial) {
        if (tiraje != TIRAJE_ILIMITADO && tiraje <= 0) {
            throw new IllegalArgumentException(
                    "El tiraje debe ser -1 o un número entero positivo");
        }
        if (estadoInicial == EstadoProducto.SUSPENDIDO) {
            throw new IllegalArgumentException("Un producto nuevo no puede iniciar suspendido");
        }
        return new DisponibilidadProducto(productoId, tiraje, estadoInicial, estadoInicial);
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

    public EstadoProducto estadoAlReactivar() {
        return estadoAlReactivar;
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
                estado,
                estadoAlReactivar);
    }

    public DisponibilidadProducto suspender() {
        if (estado == EstadoProducto.SUSPENDIDO) {
            return this;
        }
        return new DisponibilidadProducto(
                productoId,
                unidadesDisponibles,
                EstadoProducto.SUSPENDIDO,
                estado);
    }

    public DisponibilidadProducto reactivar() {
        if (estado != EstadoProducto.SUSPENDIDO) {
            return this;
        }
        return new DisponibilidadProducto(
                productoId,
                unidadesDisponibles,
                estadoAlReactivar,
                estadoAlReactivar);
    }
}
