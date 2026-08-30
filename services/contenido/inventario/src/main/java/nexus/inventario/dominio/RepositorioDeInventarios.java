package nexus.inventario.dominio;

import java.util.Optional;

public interface RepositorioDeInventarios {

    Inventario guardar(Inventario inventario);

    Optional<Inventario> buscarPorPropietario(String propietarioId);

    Optional<Inventario> buscarPorElementoId(String elementoId);
}
