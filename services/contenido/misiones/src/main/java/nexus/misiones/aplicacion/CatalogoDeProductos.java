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

    /**
     * El nombre de un producto (un arma, una armadura, un item, una epica), que es
     * como lo conoce el motor de combate para aplicar sus efectos.
     *
     * @return el nombre, o nulo si el producto ya no existe en el catalogo
     */
    String nombreDe(String productoId);
}
