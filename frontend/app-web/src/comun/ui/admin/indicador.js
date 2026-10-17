/**
 * La cifra de la consola de operación — SA-2.
 *
 * Una cifra con su etiqueta, su unidad y, si hace falta, una línea que la
 * explica y un enlace a su detalle. Lo que no se negocia:
 *
 *   - **nunca un cero inventado.** Sin dato se pinta «—» y se dice «sin dato»
 *     al lector de pantalla: no saber cuántos hay y saber que hay cero son dos
 *     respuestas distintas, y quien opera decide con lo que ve;
 *   - el tono (bien, atención, mal) se dice también con un icono y con texto,
 *     no solo con color.
 *
 * @module comun/ui/admin/indicador
 */

import { h } from '../dom.js';
import { numero } from '../formato.js';
import { icono } from '../icono.js';

/** Tonos de una cifra, con el icono que los acompaña. */
const ICONO_DEL_TONO = Object.freeze({
  exito: 'check',
  advertencia: 'reloj',
  error: 'alerta',
  info: null,
});

/**
 * @param {object} opciones
 * @param {string} opciones.etiqueta qué se cuenta
 * @param {number|string|null|undefined} opciones.valor la cifra; null es «sin dato»
 * @param {string|null} [opciones.unidad] lo que va detrás («cuentas», «ms»)
 * @param {string|null} [opciones.detalle] una línea de contexto
 * @param {'exito'|'advertencia'|'error'|'info'|null} [opciones.tono]
 * @param {{texto: string, href: string}|null} [opciones.enlace] ir al detalle
 * @param {number} [opciones.decimales]
 * @returns {HTMLElement}
 */
export function tarjetaDeIndicador({
  etiqueta,
  valor,
  unidad = null,
  detalle = null,
  tono = null,
  enlace = null,
  decimales = 0,
}) {
  const sinDato = valor === null || valor === undefined || valor === '' || Number.isNaN(valor);
  const caja = h('article', {
    clase: 'indicador-admin',
    datos: {
      indicador: etiqueta,
      ...(tono ? { tono } : {}),
      ...(sinDato ? { sinDato: 'si' } : {}),
    },
  });

  const cabeza = h('p', { clase: 'indicador-admin__etiqueta' });
  const simbolo = tono ? ICONO_DEL_TONO[tono] : null;
  if (simbolo) {
    cabeza.append(icono(simbolo, { etiqueta: null, clase: 'icono indicador-admin__icono' }));
  }
  cabeza.append(h('span', { texto: etiqueta }));

  const cifra = h('p', { clase: 'indicador-admin__valor' });
  if (sinDato) {
    cifra.append(
      h('span', { texto: '—', atributos: { 'aria-hidden': 'true' } }),
      h('span', { clase: 'solo-lectores', texto: 'Sin dato' }),
    );
  } else {
    cifra.append(
      h('span', {
        clase: 'indicador-admin__cifra',
        texto: typeof valor === 'number' ? numero(valor, decimales) : String(valor),
      }),
    );
    if (unidad) {
      cifra.append(h('span', { clase: 'indicador-admin__unidad', texto: unidad }));
    }
  }

  caja.append(cabeza, cifra);
  if (detalle) {
    caja.append(h('p', { clase: 'indicador-admin__detalle', texto: detalle }));
  }
  if (enlace?.href && enlace?.texto) {
    caja.append(
      h('a', {
        clase: 'indicador-admin__enlace',
        texto: enlace.texto,
        atributos: { href: enlace.href },
      }),
    );
  }
  return caja;
}

/**
 * Una fila de cifras. Lleva cuántas son (`data-cantidad`) para que el CSS las
 * reparta sin dejar una suelta: seis van en una fila en un escritorio y de
 * tres en tres en una tableta; cuatro, en una fila o de dos en dos.
 *
 * @param {HTMLElement[]} tarjetas
 * @param {{zona?: string|null}} [opciones]
 * @returns {HTMLElement}
 */
export function rejillaDeIndicadores(tarjetas, { zona = null } = {}) {
  return h('div', {
    clase: 'rejilla-indicadores-admin',
    datos: { cantidad: String(tarjetas.length), ...(zona ? { zona } : {}) },
    hijos: tarjetas,
  });
}
