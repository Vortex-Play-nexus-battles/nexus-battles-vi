/**
 * Botones y su estado de espera (`.boton` en el kit).
 *
 * Lo que este modulo resuelve no es el color: es que, mientras una peticion
 * esta en vuelo, el boton quede bloqueado, lo diga (`aria-busy`) y recupere
 * su texto exacto al terminar. Dos vistas lo hacian bien, las demas dejaban
 * el boton pulsable y se podian crear dos salas con dos clics seguidos.
 */

import { h } from './dom.js';

/** @typedef {'primario'|'secundario'|'acento'|'peligro'} Variante */

/**
 * @param {{texto: string, variante?: Variante, tamano?: 'pequeno'|'grande'|null,
 *          tipo?: 'button'|'submit', nombre?: string|null, alPulsar?: Function|null,
 *          href?: string|null, deshabilitado?: boolean}} opciones
 * @returns {HTMLElement} `<button>`, o `<a>` con aspecto de boton si hay `href`
 */
export function boton({
  texto,
  variante = 'primario',
  tamano = null,
  tipo = 'button',
  nombre = null,
  alPulsar = null,
  href = null,
  deshabilitado = false,
}) {
  const clase = ['boton', `boton--${variante}`, tamano ? `boton--${tamano}` : null]
    .filter(Boolean)
    .join(' ');
  const datos = nombre ? { accion: nombre } : {};

  if (href) {
    return h('a', { clase, texto, datos, atributos: { href } });
  }
  const elemento = h('button', {
    clase,
    texto,
    datos,
    atributos: { type: tipo, disabled: deshabilitado },
  });
  if (alPulsar) {
    elemento.addEventListener('click', alPulsar);
  }
  return elemento;
}

/**
 * Pone o quita el estado de espera conservando el texto original.
 *
 * @param {HTMLButtonElement} elemento
 * @param {boolean} activo
 * @param {string} [textoDeEspera]
 */
export function conCarga(elemento, activo, textoDeEspera = 'Un momento…') {
  if (!elemento) {
    return;
  }
  if (elemento.dataset.textoReposo === undefined) {
    elemento.dataset.textoReposo = elemento.textContent ?? '';
  }
  elemento.disabled = activo;
  elemento.setAttribute('aria-busy', String(activo));
  elemento.textContent = activo ? textoDeEspera : elemento.dataset.textoReposo;
}
