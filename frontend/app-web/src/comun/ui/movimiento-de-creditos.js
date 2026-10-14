/**
 * Un movimiento del libro de créditos, dicho para quien lo lee.
 *
 * `MovimientoDeCredito` de `contracts/openapi/creditos.yaml` (1.3.0, #569):
 * el servicio decide cómo afectó al saldo (`signo`) y con qué concepto. Aquí
 * solo se traduce a palabras; no se recalcula nada.
 *
 * Lo usaban dos vistas: «Mi cuenta» del jugador y, desde SA-3, la ficha
 * administrativa del usuario (§7.3.4, «historial de transacciones»). Por eso
 * sube a `comun/`.
 *
 * @module comun/ui/movimiento-de-creditos
 */

import { creditos as formatoCreditos } from './formato.js';

/**
 * Texto del movimiento según su signo. La regla la decide ms-finanzas
 * (`signo` del contrato 1.3.0); aquí solo se traduce a algo legible.
 *
 * @param {{signo: string, monto: number|string}} movimiento
 * @returns {{texto: string, tono: string}}
 */
export function comoImporte({ signo, monto }) {
  const cantidad = formatoCreditos(monto);
  switch (signo) {
    case 'SUMA':
      return { texto: `+${cantidad}`, tono: 'suma' };
    case 'RESTA':
      return { texto: `−${cantidad}`, tono: 'resta' };
    case 'APARTA':
      return { texto: `${cantidad} apartados`, tono: 'aparta' };
    default:
      return { texto: `${cantidad} devueltos`, tono: 'neutro' };
  }
}

/**
 * El importe con su unidad, para leerlo en una frase («+40 créditos»,
 * «320 créditos apartados»): el de la línea de tiempo de la ficha.
 *
 * @param {{signo: string, monto: number|string}} movimiento
 * @returns {string}
 */
export function importeConUnidad({ signo, monto }) {
  const cantidad = formatoCreditos(monto);
  switch (signo) {
    case 'SUMA':
      return `+${cantidad} créditos`;
    case 'RESTA':
      return `−${cantidad} créditos`;
    case 'APARTA':
      return `${cantidad} créditos apartados`;
    default:
      return `${cantidad} créditos devueltos`;
  }
}

/** Nombre de persona para cada concepto que escribe el backend. */
const CONCEPTOS = Object.freeze({
  'apuesta-sala': 'Apuesta de una batalla',
  'recompensa-victoria': 'Recompensa por ganar',
  'recompensa-participacion': 'Recompensa por jugar',
  'inscripcion-torneo': 'Inscripción a un torneo',
  // R17 — el abono con el que empieza toda cuenta nueva (ms-identidad lo
  // pide a finanzas con este concepto al completar el alta).
  'bono-registro': 'Créditos de bienvenida',
  'DEBITO-DIRECTO': 'Cobro',
  'CREDITO-PARTIDA': 'Créditos de una partida',
});

/**
 * @param {string|null|undefined} concepto
 * @returns {string} el nombre para leer; un concepto nuevo se muestra tal cual
 */
export function nombreDelConcepto(concepto) {
  return CONCEPTOS[concepto] ?? concepto ?? 'Movimiento de créditos';
}
