/**
 * Ranuras de equipamiento (`.ranura` del kit).
 *
 * ## Por que existe
 *
 * El kit trae `.ranura` con sus cuatro estados —vacia, ocupada, seleccionada,
 * bloqueada— desde que se derivo de Figma, y **ninguna vista la usaba**. El
 * equipamiento del heroe (dos armas, seis armaduras, dos objetos) se elige hoy
 * con listas desplegables.
 *
 * ## Arrastrar nunca es la unica via
 *
 * La ranura es un `<button>` de verdad. Se llega con Tab, se activa con Enter y
 * con espacio, y funciona con el dedo en un movil, donde arrastrar sobre una
 * cuadricula de diez casillas es un ejercicio de punteria. Arrastrar, si algun
 * dia se anade, sera un atajo encima de esto, no el unico camino.
 *
 * ## Bloqueada dice por que
 *
 * El CSS pone candado y quita el cursor, pero el motivo («necesitas nivel 12»)
 * no se deduce de un icono. Va en el `title` y en el nombre accesible.
 */

import { h, clases } from '../dom.js';
import { icono } from '../icono.js';

/**
 * Una ranura de equipamiento.
 *
 * @param {object} opciones
 * @param {string} opciones.etiqueta que va en esta ranura («Arma 1», «Casco»)
 * @param {{nombre: string, icono?: string, rareza?: string}|null} [opciones.objeto]
 *   lo que hay puesto; `null` deja la ranura vacia
 * @param {string|null} [opciones.bloqueo] motivo por el que no se puede tocar
 * @param {boolean} [opciones.seleccionada]
 * @param {(datos: {etiqueta: string, objeto: object|null}) => void} [opciones.alElegir]
 * @returns {HTMLElement}
 */
export function ranura({
  etiqueta,
  objeto = null,
  bloqueo = null,
  seleccionada = false,
  alElegir,
}) {
  const vacia = objeto === null || objeto === undefined;
  const bloqueada = Boolean(bloqueo);

  const contenido = vacia
    ? icono('mas', { clase: 'ranura__icono', etiqueta: null })
    : icono(objeto.icono ?? 'escudo', { clase: 'ranura__icono', etiqueta: null });

  // Sin esto, una cuadricula de diez ranuras suena en el lector como diez
  // botones llamados «boton». Se arma aparte para no anidar ternarios.
  let nombreAccesible = `${etiqueta}: vacia. Elegir objeto`;
  if (bloqueada) {
    nombreAccesible = `${etiqueta}: bloqueada. ${bloqueo}`;
  } else if (!vacia) {
    nombreAccesible = `${etiqueta}: ${objeto.nombre}. Cambiar`;
  }

  const caja = /** @type {HTMLButtonElement} */ (
    h('button', {
      clase: 'ranura__caja',
      atributos: {
        type: 'button',
        disabled: bloqueada,
        'aria-label': nombreAccesible,
        'aria-pressed': seleccionada ? 'true' : 'false',
        title: bloqueo ?? (vacia ? null : objeto.nombre),
      },
      hijos: [bloqueada ? icono('candado', { clase: 'ranura__icono', etiqueta: null }) : contenido],
    })
  );

  if (typeof alElegir === 'function') {
    caja.addEventListener('click', () => {
      if (!bloqueada) {
        alElegir({ etiqueta, objeto });
      }
    });
  }

  return h('div', {
    clase: clases(
      'ranura',
      vacia && !bloqueada && 'ranura--vacia',
      seleccionada && 'ranura--seleccionada',
      bloqueada && 'ranura--bloqueada',
    ),
    datos: { ranura: etiqueta },
    hijos: [
      caja,
      // El nombre del objeto, escrito. Un icono de espada no distingue una
      // espada comun de una legendaria.
      h('span', { clase: 'ranura__etiqueta', texto: vacia ? etiqueta : objeto.nombre }),
    ],
  });
}

/**
 * Cuadricula de ranuras con un titulo, para un grupo de equipamiento.
 *
 * @param {string} titulo «Armas», «Armadura», «Objetos»
 * @param {Array<Parameters<typeof ranura>[0]>} ranuras
 * @returns {HTMLElement}
 */
export function grupoDeRanuras(titulo, ranuras) {
  return h('section', {
    clase: 'grupo-ranuras',
    hijos: [
      h('h3', { clase: 't-etiqueta', texto: titulo }),
      h('div', {
        clase: 'grupo-ranuras__rejilla',
        // `group` y no `list`: son controles, no elementos de una lista.
        atributos: { role: 'group', 'aria-label': titulo },
        hijos: ranuras.map((r) => ranura(r)),
      }),
    ],
  });
}
