package nexus.inventario.dominio;

import java.util.List;
import java.util.Optional;

public interface RepositorioDeInventarios {

    Inventario guardar(Inventario inventario);

    Optional<Inventario> buscarPorPropietario(String propietarioId);

    Optional<Inventario> buscarPorElementoId(String elementoId);

    /**
     * TODOS los inventarios que contienen ese elemento — FI-TRANSFER-1.
     *
     * <p>En condiciones normales hay uno o ninguno. Hay dos durante la ventana
     * de una transferencia interrumpida: mover un elemento entre dos duenos son
     * dos escrituras en dos documentos, y este servicio no tiene transacciones
     * de Mongo configuradas. {@code buscarPorElementoId} devuelve uno
     * cualquiera de los dos, y con eso no se puede decidir si la transferencia
     * quedo a medias ni cual de las dos copias sobra.
     */
    List<Inventario> buscarTodosPorElementoId(String elementoId);

    List<ElementoInventario> buscarElementos(String propietarioId, String criterio);
}
