package nexus.productos.dominio;

import java.util.Objects;

public final class CatalogoProductos {

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

    public ResultadoAdquisicion adquirir(String productoId) {
        return repositorio.adquirirUnaUnidad(productoId);
    }

    public DisponibilidadProducto suspender(String productoId) {
        DisponibilidadProducto suspendido = consultar(productoId).suspender();
        repositorio.guardar(suspendido);
        return suspendido;
    }

    public DisponibilidadProducto reactivar(String productoId) {
        DisponibilidadProducto reactivado = consultar(productoId).reactivar();
        repositorio.guardar(reactivado);
        return reactivado;
    }

}
