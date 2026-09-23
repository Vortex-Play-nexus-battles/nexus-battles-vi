/**
 * Encabezado de pagina y de seccion.
 *
 * Cada vista escribia su `<h1>` con la estructura que le parecia: unas con
 * descripcion, otras sin ella, unas con el boton de accion antes del titulo y
 * otras despues. Con un solo componente, el titulo, la descripcion y la
 * accion principal caen siempre en el mismo sitio, que es lo que hace que dos
 * pantallas distintas se sientan la misma aplicacion.
 */

import { h } from './dom.js';

/**
 * @param {{titulo: string, descripcion?: string|null, acciones?: Array<Node>,
 *          nivel?: 1|2}} opciones
 * @returns {HTMLElement}
 */
export function encabezadoDePagina({ titulo, descripcion = null, acciones = [], nivel = 1 }) {
  const caja = h('header', { clase: 'encabezado-pagina' });
  const textos = h('div', { clase: 'pila pila--ajustada' });
  textos.append(h(`h${nivel}`, { texto: titulo }));
  if (descripcion) {
    textos.append(h('p', { clase: 't-cuerpo', texto: descripcion }));
  }
  caja.append(textos);
  if (acciones.length > 0) {
    const zona = h('div', {
      clase: 'encabezado-pagina__acciones',
      datos: { zona: 'acciones-pagina' },
    });
    zona.append(...acciones);
    caja.append(zona);
  }
  return caja;
}

/**
 * Titulo de una seccion dentro de una pagina (el bloque «Equipos», «Arbol»…).
 *
 * @param {{titulo: string, detalle?: string|null, acciones?: Array<Node>}} opciones
 * @returns {HTMLElement}
 */
export function encabezadoDeSeccion({ titulo, detalle = null, acciones = [] }) {
  const caja = h('div', { clase: 'encabezado-seccion' });
  const textos = h('div', { clase: 'pila pila--ajustada' });
  textos.append(h('h2', { clase: 't-subtitulo', texto: titulo }));
  if (detalle) {
    textos.append(h('p', { clase: 't-meta', texto: detalle }));
  }
  caja.append(textos);
  if (acciones.length > 0) {
    const zona = h('div', { clase: 'encabezado-seccion__acciones' });
    zona.append(...acciones);
    caja.append(zona);
  }
  return caja;
}
