/**
 * Creditos: la moneda del juego, con cara de moneda.
 *
 * ## Por que existe
 *
 * `tokens.css` define `--credito-oro` y el sprite trae el simbolo `moneda`.
 * Hoy los creditos se escriben como un numero suelto —«1250»— en el saldo, en
 * la apuesta de la sala, en la puja, en el precio de la tienda y en el premio
 * del torneo: cinco sitios, cinco maneras, ninguna que parezca dinero de un
 * juego.
 *
 * ## El color no lleva informacion
 *
 * El oro es identidad de marca, no significado: la cifra ya dice cuanto es.
 * Por eso el icono va marcado como decorativo y el nombre accesible incluye la
 * palabra «creditos», que es lo que hace falta oir.
 */

import { h, clases } from '../dom.js';
import { icono } from '../icono.js';
import { creditos as formatear } from '../formato.js';

/**
 * Distintivo de creditos.
 *
 * @param {number|string|null|undefined} cantidad
 * @param {object} [opciones]
 * @param {'normal'|'grande'} [opciones.tam] `grande` para el saldo de cabecera
 * @param {boolean} [opciones.conSigno] anteponer `+` a las cantidades positivas
 * @param {string} [opciones.contexto] que son estos creditos («Apuesta», «Premio»)
 * @returns {HTMLElement}
 */
export function distintivoDeCreditos(
  cantidad,
  { tam = 'normal', conSigno = false, contexto } = {},
) {
  const cifra = Number(cantidad);
  const hayDato = cantidad !== null && cantidad !== undefined && !Number.isNaN(cifra);
  const signo = conSigno && hayDato && cifra > 0 ? '+' : '';
  const texto = `${signo}${formatear(cantidad)}`;

  return h('span', {
    clase: clases('distintivo-credito', tam === 'grande' && 'distintivo-credito--grande'),
    datos: { creditos: hayDato ? String(cifra) : '' },
    atributos: {
      'aria-label': [contexto, hayDato ? `${texto} creditos` : 'sin dato de creditos']
        .filter(Boolean)
        .join(': '),
    },
    hijos: [
      icono('moneda', { clase: 'distintivo-credito__icono', etiqueta: null }),
      h('span', { clase: 'distintivo-credito__cifra', texto }),
      contexto
        ? h('span', { clase: 'distintivo-credito__contexto t-meta', texto: contexto })
        : null,
    ],
  });
}
