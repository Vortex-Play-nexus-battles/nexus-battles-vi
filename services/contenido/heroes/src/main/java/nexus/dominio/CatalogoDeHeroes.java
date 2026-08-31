package nexus.dominio;

import java.util.List;

/**
 * Puerto del catalogo de prototipos. La implementacion en memoria sirve para
 * la demo; la de MongoDB cumple la seccion 8 del documento (persistencia no
 * relacional para personajes e items) cuando plataforma aprovisione el motor.
 */
public interface CatalogoDeHeroes {

    void registrar(Prototipo prototipo);

    List<Prototipo> listar();

    Prototipo fichaDe(String nombre);
}
