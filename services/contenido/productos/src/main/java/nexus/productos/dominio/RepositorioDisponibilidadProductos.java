package nexus.productos.dominio;

import java.util.Optional;

public interface RepositorioDisponibilidadProductos {

    void guardar(DisponibilidadProducto producto);

    Optional<DisponibilidadProducto> buscarPorId(String productoId);

    /**
     * Reserva una unidad del tiraje, una sola vez por clave — B4.
     *
     * <p>Descontar la unidad y recordar la clave tienen que ser la MISMA
     * escritura atomica: si fueran dos, un fallo entre ambas dejaria una unidad
     * reservada sin rastro de por quien, y el reintento con la misma clave
     * reservaria otra. Si la clave ya reservo, responde ACEPTADA sin volver a
     * descontar, aunque el producto se haya agotado o suspendido despues.
     *
     * @param clave clave de idempotencia de quien reserva
     */
    ResultadoAdquisicion adquirirUnaUnidad(String productoId, String clave);
}
