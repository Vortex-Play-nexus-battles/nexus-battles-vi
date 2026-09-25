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

/**
 * @param {HTMLElement|null} zona la píldora `.conexion`
 * @param {{estado: string, intento?: number, de?: number, alReintentar?: () => void}} datos
 */
export function pintarEstadoDelCanal(zona, { estado, intento, de, alReintentar } = {}) {
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
