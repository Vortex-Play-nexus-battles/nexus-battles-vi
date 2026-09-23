/**
 * Aviso — el componente de retroalimentacion de una sola linea de la
 * aplicacion (`shared/ui-kit/css/componentes.css`, `.aviso`).
 *
 * Seis vistas tenian su propia version de esto, en dos familias que no se
 * parecian: unas convertian la propia zona en el aviso
 * (`zona.className = 'aviso aviso--error'`) y otras metian un hijo. Las
 * primeras ademas no ponian `role`, asi que un lector de pantalla no se
 * enteraba de que acababa de fallar algo.
 *
 * Aqui hay una sola forma: un hijo `.aviso` dentro de la zona, con `role`
 * segun el tono. La zona se queda como contenedor, que es lo que permite
 * ocultarla sin borrar la clase.
 *
 * El tono NO se decide leyendo el texto del error, sino por su estado HTTP
 * (`shared/ui-kit/MAPEO-ERRORES.md`, tabla 4).
 */

import { h } from './dom.js';

/** @typedef {'error'|'advertencia'|'exito'|'info'} Tono */

/**
 * @param {{tono: Tono, titulo: string, detalle?: string|null,
 *          accion?: {texto: string, nombre?: string, alPulsar: Function}|null}} opciones
 * @returns {HTMLElement} el `.aviso`, sin insertar
 */
export function aviso({ tono, titulo, detalle = null, accion = null }) {
  const caja = h('div', {
    clase: `aviso aviso--${tono}`,
    // Un fallo interrumpe: `alert`. Un exito o una nota informan sin
    // interrumpir: `status`.
    atributos: { role: tono === 'error' || tono === 'advertencia' ? 'alert' : 'status' },
  });
  const cuerpo = h('div', { clase: 'aviso__cuerpo' });
  cuerpo.append(h('p', { clase: 'aviso__titulo', texto: titulo }));
  if (detalle) {
    cuerpo.append(h('p', { clase: 'aviso__detalle', texto: detalle }));
  }
  caja.append(cuerpo);
  if (accion) {
    const boton = h('button', {
      clase: 'boton boton--secundario boton--pequeno',
      texto: accion.texto,
      atributos: { type: 'button' },
      datos: accion.nombre ? { accion: accion.nombre } : {},
    });
    boton.addEventListener('click', accion.alPulsar);
    caja.append(boton);
  }
  return caja;
}

/**
 * Pinta el aviso dentro de una zona y la hace visible.
 *
 * @param {HTMLElement} zona contenedor `[data-zona="aviso"]`
 * @param {Parameters<typeof aviso>[0]} opciones
 * @returns {HTMLElement} el aviso insertado
 */
export function pintarAviso(zona, opciones) {
  const caja = aviso(opciones);
  zona.replaceChildren(caja);
  zona.hidden = false;
  return caja;
}

/**
 * Deja la zona vacia y oculta. Se llama al empezar cada intento, para que no
 * quede en pantalla el fallo del intento anterior.
 *
 * @param {HTMLElement|null} zona
 */
export function limpiarAviso(zona) {
  if (!zona) {
    return;
  }
  zona.replaceChildren();
  zona.hidden = true;
}

/**
 * Tono que corresponde a un estado HTTP (MAPEO-ERRORES, tabla 4).
 *
 * Un 4xx es algo que quien mira puede corregir: advertencia. Un 5xx, un 0 o
 * un fallo de red no dependen de el: error.
 *
 * @param {number|null|undefined} estado
 * @returns {Tono}
 */
export function tonoPorEstado(estado) {
  if (typeof estado !== 'number' || estado >= 500 || estado === 0) {
    return 'error';
  }
  return estado >= 400 ? 'advertencia' : 'info';
}
