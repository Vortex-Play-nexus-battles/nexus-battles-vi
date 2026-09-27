package nexus.inventario.aplicacion;

import nexus.inventario.dominio.ParteArmadura;

/**
 * Puerto para resolver la identidad de un producto a partir de su id.
 *
 * <p>Implementacion real: {@link ResolutorDeProductoHttp}, contra
 * {@code GET /api/v1/productos/{id}}. Lo usan el calculo de estadisticas
 * equipadas (nombre, tipo, prototipo), la creacion de elementos y las entregas,
 * que solo admiten productos existentes y no suspendidos, y el equipamiento,
 * que toma del catalogo la parte de una armadura (B4).
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
     * @param parte     solo en ARMADURA: la parte del cuerpo que ocupa segun el
     *                  catalogo (CASCO, PECHO...); null si no la trae (B4)
     */
    record DetalleProducto(String nombre, String tipo, String prototipo, String estado, String parte) {

        public DetalleProducto(String nombre, String tipo, String prototipo) {
            this(nombre, tipo, prototipo, null, null);
        }

        public DetalleProducto(String nombre, String tipo, String prototipo, String estado) {
            this(nombre, tipo, prototipo, estado, null);
        }

        /** La parte del catalogo como ranura del inventario; null si no hay o no se reconoce. */
        public ParteArmadura parteArmadura() {
            if (parte == null || parte.isBlank()) {
                return null;
            }
            try {
                return ParteArmadura.valueOf(parte.trim());
            } catch (IllegalArgumentException parteDesconocida) {
                return null;
            }
        }
    }
}
