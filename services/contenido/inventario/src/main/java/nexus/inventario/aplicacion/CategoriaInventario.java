package nexus.inventario.aplicacion;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.TipoElementoInventario;

/**
 * Las dos vitrinas de «Mi inventario» (inventario.yaml 1.7.0): los héroes por un
 * lado y los objetos —armas, armaduras, ítems, habilidades y épicas— por otro.
 *
 * <p>§7.1 pide dieciséis productos por página con un control de paginación, y
 * la vista los enseña en dos pestañas. Sin este filtro el servidor devolvía
 * páginas mezcladas y el navegador tenía que reunir el inventario entero para
 * partirlo (`coleccion-inventario.js`): con él, cada pestaña pide al servidor
 * solo la página que enseña.
 */
public enum CategoriaInventario {

    /** Solo los elementos de tipo {@code HEROE}. */
    HEROES,

    /** Todo lo que no es un héroe. */
    OBJETOS;

    public boolean incluye(ElementoInventario elemento) {
        boolean esHeroe = elemento.tipo() == TipoElementoInventario.HEROE;
        return this == HEROES ? esHeroe : !esHeroe;
    }
}
