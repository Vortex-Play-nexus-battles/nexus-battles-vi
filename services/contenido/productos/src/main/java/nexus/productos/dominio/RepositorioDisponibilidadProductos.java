package nexus.productos.dominio;

import java.util.Optional;

public interface RepositorioDisponibilidadProductos {

    void guardar(DisponibilidadProducto producto);

    Optional<DisponibilidadProducto> buscarPorId(String productoId);

    ResultadoAdquisicion adquirirUnaUnidad(String productoId);
}
