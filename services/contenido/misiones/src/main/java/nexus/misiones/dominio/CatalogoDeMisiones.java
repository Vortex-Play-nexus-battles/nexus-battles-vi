package nexus.misiones.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Las misiones publicadas y la Tabla 20. Salen de la semilla versionada del
 * repositorio (contenido del documento), no de una base que alguien edite a
 * mano en caliente.
 */
public interface CatalogoDeMisiones {

    List<Mision> todas();

    Optional<Mision> buscar(String id);

    /** Tabla 20: la epica y la probabilidad de Master de cada tipo de heroe. */
    List<EpicaDeTabla20> tabla20();
}
