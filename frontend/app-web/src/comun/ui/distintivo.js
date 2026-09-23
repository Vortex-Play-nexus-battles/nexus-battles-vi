/**
 * Distintivos: estado de una sala, rareza de un objeto, rol de una cuenta
 * (`.distintivo`, `.marco-heroe--<rareza>` en el kit).
 *
 * El kit ya traia las variantes desde las maquetas de Figma —abierta, llena,
 * privada, epica, sancionado, moderador…— y ninguna vista las usaba: cada una
 * pintaba su propio `<span>` gris. Esto las pone al alcance con el nombre del
 * dominio, no con el de la clase CSS.
 */

import { h } from './dom.js';

/** Estados de sala y de partida que el kit sabe pintar. */
const VARIANTES_CONOCIDAS = new Set([
  'abierta',
  'llena',
  'privada',
  'en-juego',
  'bloqueada',
  'completada',
  'en-progreso',
  'en-mision',
  'no-disponible',
  'activo',
  'epica',
  'promocion',
  'reportado',
  'sancionado',
  'suspendido',
  'administrador',
  'moderador',
  'maestro-juego',
]);

/**
 * @param {string} texto lo que se lee
 * @param {string|null} [variante] sufijo de `.distintivo--`; se ignora si el kit no lo conoce
 * @returns {HTMLElement}
 */
export function distintivo(texto, variante = null) {
  const sufijo = variante && VARIANTES_CONOCIDAS.has(variante) ? ` distintivo--${variante}` : '';
  return h('span', { clase: `distintivo${sufijo}`, texto, datos: variante ? { variante } : {} });
}

/**
 * Rareza de un objeto o heroe, con el color que le toca (COMUN, RARA, EPICA,
 * LEGENDARIA del catalogo de productos).
 *
 * @param {string|null|undefined} rareza
 * @returns {HTMLElement|null} null si el producto no declara rareza
 */
export function distintivoDeRareza(rareza) {
  if (!rareza) {
    return null;
  }
  const clave = String(rareza).toLowerCase();
  const etiqueta = clave.charAt(0).toUpperCase() + clave.slice(1);
  return h('span', {
    clase: `distintivo distintivo--rareza`,
    texto: etiqueta,
    datos: { rareza: clave },
    // El color de rareza es decorativo; el texto ya lo dice.
    atributos: { style: `--rareza: var(--rareza-${clave}, var(--borde))` },
  });
}

/**
 * Marco de heroe segun rareza, para las cartas de coleccion.
 *
 * @param {string|null|undefined} rareza
 * @returns {string} las clases que van en el contenedor de la imagen
 */
export function claseDeMarco(rareza) {
  const clave = String(rareza ?? '').toLowerCase();
  return ['rara', 'epica', 'legendaria'].includes(clave)
    ? `marco-heroe marco-heroe--${clave}`
    : 'marco-heroe';
}
