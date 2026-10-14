package nexus.inventario.aplicacion;

import java.util.Set;

/**
 * Puerto de la búsqueda sobre la información del catálogo (HU-INV-002, 1.7.0).
 *
 * <p>El inventario guarda de cada elemento su producto y su nombre propio; la
 * descripción, los efectos, las habilidades y el resto de la ficha viven en los
 * servicios de productos y de héroes. Este puerto contesta qué productos del
 * catálogo contienen un texto, para que la búsqueda del inventario encuentre «lo
 * que está presente en el producto» (§7.1) sin copiar el catálogo dentro de cada
 * inventario.
 */
public interface BuscadorEnElCatalogo {

    /** Sin catálogo: la búsqueda se queda en los datos propios del elemento. */
    BuscadorEnElCatalogo NINGUNO = criterio -> Set.of();

    /**
     * @param criterio texto de cuatro o más caracteres
     * @return ids de los productos cuya información lo contiene; vacío si ninguno
     *         o si el catálogo no se pudo leer (la búsqueda nunca falla por eso)
     */
    Set<String> productosQueCoinciden(String criterio);
}
