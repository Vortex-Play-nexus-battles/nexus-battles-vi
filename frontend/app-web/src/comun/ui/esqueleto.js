/**
 * Esqueletos de carga (`.esqueleto`).
 *
 * Un esqueleto con la forma de lo que viene hace la espera mas corta que un
 * girador, y evita el salto de la pagina cuando por fin llega el contenido.
 * `aria-hidden`: no hay nada que leer todavia; quien anuncia la espera es el
 * contenedor de `estado-vista.js` con su `aria-busy`.
 */

import { h } from './dom.js';

/**
 * Lineas sueltas, para bloques de texto.
 *
 * @param {number} [cuantas]
 * @returns {HTMLElement}
 */
export function esqueletoDeLista(cuantas = 3) {
  const caja = h('div', { clase: 'pila pila--ajustada', atributos: { 'aria-hidden': 'true' } });
  for (let i = 0; i < cuantas; i += 1) {
    caja.append(
      h('span', {
        clase: i % 2 === 0 ? 'esqueleto esqueleto--largo' : 'esqueleto esqueleto--corto',
      }),
    );
  }
  return caja;
}

/**
 * Rejilla de tarjetas fantasma, para listados de subastas, salas o heroes.
 *
 * @param {number} [cuantas]
 * @returns {HTMLElement}
 */
export function esqueletoDeTarjetas(cuantas = 6) {
  const rejilla = h('div', {
    clase: 'rejilla-salas',
    datos: { esqueleto: 'tarjetas' },
    atributos: { 'aria-hidden': 'true' },
  });
  for (let i = 0; i < cuantas; i += 1) {
    const tarjeta = h('div', { clase: 'tarjeta pila pila--ajustada' });
    tarjeta.append(
      h('span', { clase: 'esqueleto esqueleto--largo' }),
      h('span', { clase: 'esqueleto esqueleto--corto' }),
      h('span', { clase: 'esqueleto' }),
    );
    rejilla.append(tarjeta);
  }
  return rejilla;
}

/**
 * Filas fantasma para una tabla ya montada: se insertan en su `<tbody>` para
 * que la cabecera no se mueva cuando lleguen los datos.
 *
 * @param {number} columnas
 * @param {number} [filas]
 * @returns {DocumentFragment}
 */
export function esqueletoDeFilas(columnas, filas = 5) {
  const trozo = document.createDocumentFragment();
  for (let f = 0; f < filas; f += 1) {
    const fila = h('tr', { atributos: { 'aria-hidden': 'true' }, datos: { esqueleto: 'fila' } });
    for (let c = 0; c < columnas; c += 1) {
      fila.append(h('td', { hijos: [h('span', { clase: 'esqueleto' })] }));
    }
    trozo.append(fila);
  }
  return trozo;
}
