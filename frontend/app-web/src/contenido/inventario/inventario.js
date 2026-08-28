/**
 * HU-INV-001 - Orquesta la vitrina y sus cuatro estados.
 *
 * Separa la decision de que mostrar (este archivo) de como se dibuja la
 * rejilla (`vitrina.js`) y de como se pide el dato (`cliente-inventario.js`).
 */

import { consultarPagina } from './cliente-inventario.js';
import { construirVitrina } from './vitrina.js';
import { construirCarga, construirVacio, construirError } from './estados-vista.js';

/**
 * Pinta el inventario de un jugador dentro de `contenedor`.
 *
 * @param {HTMLElement} contenedor donde se monta la vista.
 * @param {string} propietarioId jugador cuyo inventario se consulta.
 * @param {number} numeroPagina pagina pedida, desde cero.
 * @param {{consultar?: Function}} opciones inyeccion para las pruebas.
 * @returns {Promise<void>} resuelve cuando la vista quedo en su estado final.
 */
export async function montarVitrina(
  contenedor,
  propietarioId,
  numeroPagina = 0,
  { consultar = consultarPagina } = {},
) {
  contenedor.replaceChildren(construirCarga());

  let pagina;
  try {
    pagina = await consultar(propietarioId, numeroPagina);
  } catch (fallo) {
    // El detalle tecnico es para el equipo; al jugador se le habla en su idioma.
    console.error('No se pudo cargar la vitrina del inventario', fallo);
    contenedor.replaceChildren(construirError());
    return;
  }

  if (!pagina || pagina.elementos.length === 0) {
    contenedor.replaceChildren(construirVacio());
    return;
  }

  contenedor.replaceChildren(construirVitrina(pagina));
}
