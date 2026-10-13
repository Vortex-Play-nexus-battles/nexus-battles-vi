/**
 * El estado del canal en tiempo real, dicho donde se ve — UXC-2 / UXC-9
 * (ReconnectBanner).
 *
 * Pinta sobre la píldora `.conexion` del kit (estable, reconectando, sin
 * conexión) lo que diga `canal-reconectable.js`, y cuando se agotan los
 * intentos pone al lado un «Reintentar» de verdad. La píldora es una región
 * `status`: el cambio se anuncia solo, sin robar el foco.
 *
 * No nombra servicios ni códigos (§8 → UX: «decir qué está degradado sin
 * nombrar servicios internos»).
 *
 * @module comun/ui/reconexion
 */

import { h } from './dom.js';

/** Texto y variante del kit de cada estado. */
const PRESENTACION = Object.freeze({
  // UXC-6 — el primer intento, antes de saber nada: el chat lo pinta mientras
  // abre el canal por primera vez.
  conectando: { clase: 'reconectando', texto: 'Conectando…' },
  conectado: { clase: 'estable', texto: 'Conectado' },
  reconectado: { clase: 'estable', texto: 'Conexión recuperada' },
  reconectando: { clase: 'reconectando', texto: 'Reconectando…' },
  'sin-conexion': { clase: 'sin-conexion', texto: 'Sin conexión' },
});

/**
 * El prefijo que da contexto a la palabra. Se lee siempre; se ve salvo en el
 * combate a pantalla completa, donde el HUD necesita el ancho (la pildora
 * sola, con su color y su palabra, basta a la vista).
 */
const PREFIJO = 'Canal en tiempo real: ';

/** Estados que, en modo discreto, sí se enseñan: los que piden algo a quien juega. */
const A_LA_VISTA_EN_DISCRETO = new Set(['reconectando', 'sin-conexion', 'reconectado']);

/** Cuánto se ve «Conexión recuperada» en modo discreto antes de irse. */
export const RECUPERADA_VISIBLE_MS = 4000;

/**
 * Modo discreto — revisión del modo jugador del 6-oct (puntos 9, 13 y 18):
 * «Canal en tiempo real: Conectado» a la vista todo el rato es texto técnico
 * que no le dice nada a quien juega. En discreto la píldora solo aparece
 * cuando hay algo que contar —reconectando, sin conexión— y «Conexión
 * recuperada» un momento al volver. Sigue pintándose siempre (estado,
 * `data-estado-canal`, texto): lo que cambia es si se ve.
 *
 * @param {HTMLElement} zona
 * @param {string} estado
 */
function aplicarDiscrecion(zona, estado) {
  clearTimeout(zona._ocultarRecuperada);
  zona.hidden = !A_LA_VISTA_EN_DISCRETO.has(estado);
  if (estado === 'reconectado') {
    zona._ocultarRecuperada = setTimeout(() => {
      if (zona.dataset.estadoCanal === 'reconectado') {
        zona.hidden = true;
      }
    }, RECUPERADA_VISIBLE_MS);
  }
}

/**
 * @param {HTMLElement|null} zona la píldora `.conexion`
 * @param {{estado: string, intento?: number, de?: number, alReintentar?: () => void,
 *   discreto?: boolean}} datos `discreto`: solo se ve cuando hay un problema
 */
export function pintarEstadoDelCanal(
  zona,
  { estado, intento, de, alReintentar, discreto = false } = {},
) {
  if (!zona) {
    return;
  }
  const presentacion = PRESENTACION[estado] ?? PRESENTACION['sin-conexion'];
  zona.className = `conexion conexion--${presentacion.clase}`;
  zona.dataset.estadoCanal = estado;
  let texto = presentacion.texto;
  if (estado === 'reconectando' && Number.isInteger(intento) && Number.isInteger(de)) {
    texto = `Reconectando… (intento ${intento} de ${de})`;
  }
  zona.replaceChildren(h('span', { clase: 'conexion__prefijo', texto: PREFIJO }), texto);
  if (!zona.hasAttribute('role')) {
    zona.setAttribute('role', 'status');
  }
  if (discreto) {
    aplicarDiscrecion(zona, estado);
  }

  const siguiente = zona.nextElementSibling;
  const botonPrevio = siguiente?.dataset?.accion === 'reintentar-canal' ? siguiente : null;
  if (estado === 'sin-conexion' && typeof alReintentar === 'function') {
    if (!botonPrevio) {
      const boton = h('button', {
        clase: 'boton boton--secundario boton--pequeno conexion__reintentar',
        texto: 'Reintentar',
        atributos: { type: 'button' },
        datos: { accion: 'reintentar-canal' },
      });
      boton.addEventListener('click', () => alReintentar());
      zona.after(boton);
    }
  } else {
    botonPrevio?.remove();
  }
}
