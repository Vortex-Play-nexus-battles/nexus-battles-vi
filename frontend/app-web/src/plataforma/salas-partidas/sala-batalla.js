/**
 * Vista de sala en batalla — HU-SAL-005 (RF-JUE-009).
 *
 * Su unico trabajo es montar el panel de vidas sobre el marcado de la pagina y
 * decir la verdad sobre el estado del canal en tiempo real.
 *
 * De donde salen los participantes: de `montarSalaBatalla`, o del bloque JSON
 * que el servidor deje incrustado en la pagina. No se piden por HTTP porque
 * todavia no existe endpoint de partida, y no se inventan datos de ejemplo:
 * sin partida cargada la vista muestra su estado vacio, que es lo honesto.
 *
 * El indicador de conexion refleja lo que hay de verdad. Mientras no se le
 * pase un `suscribir`, la vista dice «sin conexion» en vez de aparentar que
 * el canal esta vivo.
 *
 * @module sala-batalla
 */

import { montarPanelVidas } from './panel-vidas.js';

/**
 * Lee el estado que el servidor haya incrustado en la pagina.
 *
 * Este es el punto por el que entrara la partida cuando exista el endpoint:
 * un `<script type="application/json" data-estado-inicial>` con la forma del
 * esquema del contrato. Si no hay bloque, no hay partida.
 *
 * @param {ParentNode} [raiz=document]
 * @returns {{idPartida: string, participantes: Array<object>} | null}
 */
export function leerEstadoInicial(raiz = document) {
  const bloque = raiz.querySelector('script[data-estado-inicial]');
  if (!bloque || !bloque.textContent.trim()) return null;

  return JSON.parse(bloque.textContent);
}

/**
 * Pinta el indicador de estado del canal.
 *
 * @param {HTMLElement | null} zona
 * @param {boolean} hayCanal
 */
function pintarConexion(zona, hayCanal) {
  if (!zona) return;

  zona.className = hayCanal ? 'conexion conexion--estable' : 'conexion conexion--sin-conexion';
  zona.textContent = hayCanal
    ? 'Canal en tiempo real conectado'
    : 'Canal en tiempo real no conectado';
}

/**
 * Monta la vista.
 *
 * @param {ParentNode} raiz
 * @param {object} [opciones]
 * @param {string} [opciones.idPartida]
 * @param {Array<object>} [opciones.participantes]
 * @param {(alRecibir: (evento: object) => void) => void} [opciones.suscribir]
 *   Transporte del canal de la partida. Se inyecta desde fuera para que el dia
 *   que exista STOMP no haya que rehacer nada de aqui.
 */
export function montarSalaBatalla(raiz, { idPartida, participantes, suscribir } = {}) {
  const zonaConexion = raiz.querySelector('[data-zona="conexion"]');
  const zonaSinPartida = raiz.querySelector('[data-zona="sin-partida"]');
  const panel = raiz.querySelector('[data-zona="panel"]');
  const vidas = raiz.querySelector('[data-zona="vidas"]');

  pintarConexion(zonaConexion, typeof suscribir === 'function');

  const hayPartida = Array.isArray(participantes) && participantes.length > 0;

  if (zonaSinPartida) zonaSinPartida.hidden = hayPartida;
  if (panel) panel.hidden = !hayPartida;

  if (!hayPartida || !vidas) return;

  montarPanelVidas(vidas, { idPartida, participantes, suscribir });
}
