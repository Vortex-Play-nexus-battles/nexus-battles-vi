package nexus.productos.dominio;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CatalogoProductos {

    private final RepositorioDisponibilidadProductos repositorio;

    public CatalogoProductos(RepositorioDisponibilidadProductos repositorio) {
        this.repositorio = Objects.requireNonNull(
                repositorio,
                "El repositorio de productos es obligatorio");
    }

    public void registrar(DisponibilidadProducto producto) {
        repositorio.guardar(producto);
    }

    public DisponibilidadProducto consultar(String productoId) {
        return repositorio.buscarPorId(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException(productoId));
    }

    /**
     * Reserva una unidad con una clave de idempotencia: repetir la misma clave
     * no vuelve a descontar (ver {@link RepositorioDisponibilidadProductos}).
     */
    public ResultadoAdquisicion adquirir(String productoId, String clave) {
        return repositorio.adquirirUnaUnidad(productoId, clave);
    }

    /**
     * Reserva una unidad sin idempotencia: cada llamada es una reserva nueva,
     * con una clave que no se repite. La API nunca lo usa —exige
     * {@code Idempotency-Key}—; es la operacion de dominio pura.
     */
    public ResultadoAdquisicion adquirir(String productoId) {
        return adquirir(productoId, UUID.randomUUID().toString());
    }

    /** Suspende; si ya estaba suspendido no escribe nada. */
    public DisponibilidadProducto suspender(String productoId) {
        DisponibilidadProducto actual = consultar(productoId);
        DisponibilidadProducto suspendido = actual.suspender();
        if (suspendido != actual) {
            repositorio.guardar(suspendido);
        }
        return suspendido;
    }

    /** Reactiva al estado anterior; si no estaba suspendido no escribe nada. */
    public DisponibilidadProducto reactivar(String productoId) {
        DisponibilidadProducto actual = consultar(productoId);
        DisponibilidadProducto reactivado = actual.reactivar();
        if (reactivado != actual) {
            repositorio.guardar(reactivado);
        }
        return reactivado;
    }

}
