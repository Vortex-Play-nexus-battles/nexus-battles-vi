/**
 * Final de partida: VICTORIA y DERROTA.
 *
 * ## Por que existe
 *
 * `tokens.css` reserva `--t-display-tam` (3,5rem) con el comentario de que es
 * para los desenlaces de partida, y **nadie la usaba**: el tamano mas grande
 * que se veia en toda la aplicacion era el de un titulo de seccion. Una partida
 * que termina con un parrafo del mismo tamano que el resto no se siente como el
 * final de nada.
 *
 * ## Ganar y perder no son la misma pantalla con otro color
 *
 * Se distinguen por la palabra, por el icono y por lo que se ofrece despues.
 * Quien no distingue verde de rojo lee «VICTORIA» igual de claro.
 *
 * ## Creditos
 *
 * El reparto lo calcula `ms-finanzas` (HU-JUE-012). Aqui se muestra lo que
 * llegue; si no llega nada, no se inventa un cero: se omite la linea.
 */

import { h, clases } from '../dom.js';
import { icono } from '../icono.js';
import { creditos as formatearCreditos } from '../formato.js';

/**
 * Panel de desenlace.
 *
 * @param {object} opciones
 * @param {boolean} opciones.victoria
 * @param {string} [opciones.detalle] una linea de contexto («Ganó Equipo 2»)
 * @param {number|null} [opciones.creditos] variacion de creditos, con signo
 * @param {Array<HTMLElement>} [opciones.acciones] botones de «que hago ahora»
 * @returns {HTMLElement}
 */
export function panelDeResultado({ victoria, detalle, creditos = null, acciones = [] }) {
  const palabra = victoria ? 'VICTORIA' : 'DERROTA';

  return h('div', {
    clase: clases(
      'panel-resultado',
      victoria ? 'panel-resultado--victoria' : 'panel-resultado--derrota',
    ),
    datos: { resultado: victoria ? 'victoria' : 'derrota' },
    // `alertdialog` no: no hay nada que confirmar y no se quiere secuestrar el
    // foco. `status` con `aria-live` lo anuncia sin atrapar a nadie.
    atributos: { role: 'status', 'aria-live': 'polite' },
    hijos: [
      icono(victoria ? 'trofeo' : 'escudo', {
        clase: 'panel-resultado__icono',
        etiqueta: null,
      }),
      h('p', { clase: 'panel-resultado__palabra', texto: palabra }),
      detalle ? h('p', { clase: 'panel-resultado__detalle', texto: detalle }) : null,
      Number.isFinite(creditos) && creditos !== 0
        ? h('p', {
            clase: 'panel-resultado__creditos',
            texto: `${creditos > 0 ? '+' : ''}${formatearCreditos(creditos)}`,
            datos: { signo: creditos > 0 ? 'positivo' : 'negativo' },
          })
        : null,
      acciones.length > 0
        ? h('div', { clase: 'panel-resultado__acciones', hijos: acciones })
        : null,
    ],
  });
}
