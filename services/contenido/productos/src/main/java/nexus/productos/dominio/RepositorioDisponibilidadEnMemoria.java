package nexus.productos.dominio;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import nexus.dominio.EstadoProducto;

public final class RepositorioDisponibilidadEnMemoria
        implements RepositorioDisponibilidadProductos {

    private final ConcurrentHashMap<String, DisponibilidadProducto> productos =
            new ConcurrentHashMap<>();

    /** Claves que ya reservaron, por producto; se leen y escriben dentro del mismo compute. */
    private final ConcurrentHashMap<String, Set<String>> claves = new ConcurrentHashMap<>();

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
    public ResultadoAdquisicion adquirirUnaUnidad(String productoId, String clave) {
        AtomicReference<ResultadoAdquisicion> resultado = new AtomicReference<>();
        productos.compute(productoId, (id, producto) -> {
            if (producto == null) {
                resultado.set(new ResultadoAdquisicion(
                        EstadoAdquisicion.NO_ENCONTRADO,
                        "El producto no existe"));
                return null;
            }
            Set<String> reservadas = claves.computeIfAbsent(id, sinClaves -> new HashSet<>());
            if (reservadas.contains(clave)) {
                resultado.set(new ResultadoAdquisicion(
                        EstadoAdquisicion.ACEPTADA,
                        "Unidad reservada"));
                return producto;
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
            reservadas.add(clave);
            resultado.set(new ResultadoAdquisicion(
                    EstadoAdquisicion.ACEPTADA,
                    "Unidad reservada"));
            return producto.consumirUnidad();
        });
        return resultado.get();
    }
}
