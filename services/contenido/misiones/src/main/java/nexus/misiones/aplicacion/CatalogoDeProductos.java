package nexus.misiones.aplicacion;

/**
 * El catalogo de productos (productos.yaml): el prototipo de heroe de un
 * producto HEROE («Guerrero Armas»), que es lo que entienden heroes y el motor.
 * El inventario guarda el producto, no el prototipo.
 */
public interface CatalogoDeProductos {

    /**
     * @return el prototipo, o nulo si el producto no declara ninguno
     * @throws HeroeNoEncontrado si el producto no existe
     */
    String prototipoDe(String productoId);
}
