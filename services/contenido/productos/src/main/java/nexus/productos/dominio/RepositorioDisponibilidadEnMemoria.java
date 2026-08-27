package nexus.productos.dominio;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adaptador determinista para pruebas y desarrollo local.
 *
 * <p>{@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}
 * serializa la actualización por producto. El adaptador de base de datos que
 * se integre con HU-PRD-001 debe conservar la misma garantía atómica.</p>
 */
public final class RepositorioDisponibilidadEnMemoria
        implements RepositorioDisponibilidadProductos {

    private final ConcurrentHashMap<String, DisponibilidadProducto> productos =
            new ConcurrentHashMap<>();

    @Override
    public void guardar(DisponibilidadProducto producto) {
        DisponibilidadProducto valido = Objects.requireNonNull(
                producto,
                "El producto es obligatorio");
        productos.put(valido.productoId(), valido);
    }

    @Override
    public Optional<DisponibilidadProducto> buscarPorId(String productoId) {
        return Optional.ofNullable(productos.get(productoId));
    }

    @Override
    public ResultadoAdquisicion adquirirUnaUnidad(String productoId) {
        AtomicReference<ResultadoAdquisicion> resultado = new AtomicReference<>();
        productos.compute(productoId, (id, producto) -> {
            if (producto == null) {
                resultado.set(new ResultadoAdquisicion(
                        EstadoAdquisicion.NO_ENCONTRADO,
                        "El producto no existe"));
                return null;
            }
            if (producto.estado() == EstadoProducto.SUSPENDIDO) {
                resultado.set(new ResultadoAdquisicion(
                        EstadoAdquisicion.SUSPENDIDO,
                        "El producto está suspendido y no se puede adquirir"));
                return producto;
            }
            if (producto.estaAgotado()) {
                resultado.set(new ResultadoAdquisicion(
                        EstadoAdquisicion.AGOTADO,
                        "El producto está agotado"));
                return producto;
            }
            resultado.set(new ResultadoAdquisicion(
                    EstadoAdquisicion.ACEPTADA,
                    "Unidad reservada"));
            return producto.consumirUnidad();
        });
        return resultado.get();
    }
}
