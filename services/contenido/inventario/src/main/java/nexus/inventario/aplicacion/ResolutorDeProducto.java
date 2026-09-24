package nexus.inventario.aplicacion;

/**
 * Puerto para resolver la identidad de un producto a partir de su id.
 *
 * <p>Implementacion real: {@link ResolutorDeProductoHttp}, contra
 * {@code GET /api/v1/productos/{id}} (publico). Lo usan el calculo de
 * estadisticas equipadas (nombre, tipo, prototipo) y la creacion de
 * elementos, que solo admite productos existentes, no suspendidos y del
 * mismo tipo que se pide.
 */
public interface ResolutorDeProducto {

    DetalleProducto resolver(String productoId);

    /**
     * @param nombre    nombre del producto (ej. "Espada de una mano"); clave
     *                  para buscar su efecto en CatalogoEfectosEquipamiento
     * @param tipo      HEROE, ARMA, ARMADURA, ITEM, HABILIDAD o EPICA
     * @param prototipo solo presente si tipo es HEROE: el prototipo del
     *                  servicio de heroes (ej. "Guerrero Tanque")
     * @param estado    ACTIVO, UNICO o SUSPENDIDO; null si quien lo construye
     *                  no lo conoce (los usos que solo necesitan nombre y tipo)
     */
    record DetalleProducto(String nombre, String tipo, String prototipo, String estado) {

        public DetalleProducto(String nombre, String tipo, String prototipo) {
            this(nombre, tipo, prototipo, null);
        }
    }
}
