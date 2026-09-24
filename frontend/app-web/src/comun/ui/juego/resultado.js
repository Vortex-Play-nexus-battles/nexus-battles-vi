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
 * ## Y el empate tampoco es una derrota — R10
 *
 * Hasta R10 habia dos estados y nada mas, asi que una partida en la que nadie
 * quedo en pie se pintaba con el icono del escudo y la palabra DERROTA mientras
 * el texto de al lado decia «Combate terminado en empate». Se le decia al
 * jugador que habia perdido algo que no perdio. Ahora hay tres.
 *
 * ## Creditos
 *
 * Las cifras las calcula `ms-finanzas` (la apuesta en HU-JUE-014, la
 * recompensa por jugar en HU-JUE-012). Aqui se muestra lo que llegue; si no
 * llega nada, no se inventa un cero: se omite la linea.
 */

import { h, clases } from '../dom.js';
import { icono } from '../icono.js';
import { creditos as formatearCreditos } from '../formato.js';

const DESENLACES = Object.freeze({
  victoria: { palabra: 'VICTORIA', icono: 'trofeo' },
  derrota: { palabra: 'DERROTA', icono: 'escudo' },
  empate: { palabra: 'EMPATE', icono: 'escudo' },
});

/**
 * Panel de desenlace.
 *
 * @param {object} opciones
 * @param {boolean} [opciones.victoria] atajo de `desenlace`: `true` es
 *        victoria, `false` derrota. Se mantiene porque lo usan las llamadas
 *        anteriores a R10; `desenlace` manda si se pasan las dos.
 * @param {'victoria'|'derrota'|'empate'} [opciones.desenlace]
 * @param {string} [opciones.detalle] una linea de contexto («Ganó Equipo 2»)
 * @param {number|null} [opciones.creditos] variacion NETA de creditos, con
 *        signo: la apuesta mas la recompensa. Ver la nota de `combate.js`.
 * @param {Array<HTMLElement>} [opciones.acciones] botones de «que hago ahora»
 * @returns {HTMLElement}
 */
export function panelDeResultado({ victoria, desenlace, detalle, creditos = null, acciones = [] }) {
  const cual = desenlace ?? (victoria ? 'victoria' : 'derrota');
  const { palabra, icono: nombreDelIcono } = DESENLACES[cual] ?? DESENLACES.derrota;

  return h('div', {
    clase: clases('panel-resultado', `panel-resultado--${cual}`),
    datos: { resultado: cual },
    // `alertdialog` no: no hay nada que confirmar y no se quiere secuestrar el
    // foco. `status` con `aria-live` lo anuncia sin atrapar a nadie.
    atributos: { role: 'status', 'aria-live': 'polite' },
    hijos: [
      icono(nombreDelIcono, {
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
