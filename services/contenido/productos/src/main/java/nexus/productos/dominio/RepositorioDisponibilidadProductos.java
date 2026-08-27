package nexus.productos.dominio;

import java.util.Optional;

/** Puerto de persistencia; la reserva debe implementarse como operación atómica. */
public interface RepositorioDisponibilidadProductos {

    void guardar(DisponibilidadProducto producto);

    Optional<DisponibilidadProducto> buscarPorId(String productoId);

    ResultadoAdquisicion adquirirUnaUnidad(String productoId);
}
