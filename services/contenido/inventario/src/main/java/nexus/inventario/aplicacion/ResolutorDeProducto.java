package nexus.inventario.aplicacion;

/**
 * Puerto para resolver la identidad de un producto a partir de su id.
 *
 * <p><b>Sin implementacion HTTP real todavia.</b> El contrato
 * {@code productos.yaml} (PR #185) solo define {@code POST /api/v1/productos};
 * no existe {@code GET /api/v1/productos/{id}} en develop ni en ningun PR
 * abierto (mismo hueco que ya levanto HU-INV-007, PR #203). Mientras no
 * exista, esta interfaz se implementa con un doble en memoria para pruebas;
 * cuando el endpoint exista, se agrega un adaptador HTTP real, igual que
 * ClienteHeroesHttp en motor-combate.
 */
public interface ResolutorDeProducto {

    DetalleProducto resolver(String productoId);

    /**
     * @param nombre    nombre del producto (ej. "Espada de una mano"); clave
     *                  para buscar su efecto en CatalogoEfectosEquipamiento
     * @param tipo      HEROE, ARMA, ARMADURA, ITEM, HABILIDAD o EPICA
     * @param prototipo solo presente si tipo es HEROE: el prototipo del
     *                  servicio de heroes (ej. "Guerrero Tanque")
     */
    record DetalleProducto(String nombre, String tipo, String prototipo) {
    }
}
