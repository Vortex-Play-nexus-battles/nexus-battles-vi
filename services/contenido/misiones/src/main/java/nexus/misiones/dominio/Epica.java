package nexus.misiones.dominio;

/**
 * Habilidad epica de un Master (seccion 6.1.2 y Tabla 20; seccion 7.8.4).
 *
 * @param productoId el producto EPICA del catalogo oficial con que se entrega
 *                   al inventario, o nulo si la epica no esta en el catalogo
 *                   (la del ejemplo de 7.8.14, «Velo de Sombras»): entonces se
 *                   registra en la coleccion de epicas del jugador (7.8.12) y
 *                   no se inventa un producto
 */
public record Epica(String nombre, String efectoGeneral, String efectoPotenciado, String productoId) {

    public Epica {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una epica necesita nombre.");
        }
    }

    public boolean entregable() {
        return productoId != null && !productoId.isBlank();
    }
}
