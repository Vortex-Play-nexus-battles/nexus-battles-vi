package nexus.inventario.dominio;

import java.util.List;
import java.util.Optional;

public interface RepositorioDeInventarios {

    Inventario guardar(Inventario inventario);

    Optional<Inventario> buscarPorPropietario(String propietarioId);

    Optional<Inventario> buscarPorElementoId(String elementoId);

    List<ElementoInventario> buscarElementos(String propietarioId, String criterio);
}
