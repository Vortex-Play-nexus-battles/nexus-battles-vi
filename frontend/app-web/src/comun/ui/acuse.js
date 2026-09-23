/**
 * Acuse de un cambio de estado — UX-R2.10.
 *
 * ## La regla que este modulo hace cumplir
 *
 * «Si una animacion no ayuda a entender un cambio de estado, no se agrega.»
 *
 * Por eso la API no es `animar(elemento)` sino `acusar(elemento, {…})`: no se
 * puede llamar sin decir QUE cambio y como se lee ese cambio sin ver la
 * animacion. Un modulo que solo supiera mover cosas invitaria a mover cosas.
 *
 * ## Que garantiza
 *
 * 1. **La informacion nunca vive solo en el movimiento.** `texto` es
 *    obligatorio cuando hay cifra: el acuse deja un rastro legible —un
 *    `role="status"` con `aria-live`— que se anuncia igual con
 *    `prefers-reduced-motion` puesto. La animacion es el adorno; el texto es
 *    el dato.
 * 2. **`prefers-reduced-motion` no pierde nada.** Los tokens de duracion del
 *    kit valen `0ms` bajo esa preferencia, asi que la clase se pone y se
 *    quita igual pero sin recorrido. No hay una rama «si reduce, no hagas
 *    nada» que se pueda desincronizar del CSS.
 * 3. **No se apilan.** Un segundo acuse sobre el mismo elemento reinicia el
 *    primero en vez de sumarse: en una sala de seis participantes llegan
 *    varios avisos por segundo y tres animaciones encima de la misma barra
 *    solo producen ruido.
 *
 * ## Donde se usa, y donde no
 *
 * Se usa en: turno, dano, curacion, equipar, puja, notificacion, dialogo,
 * victoria y derrota. **No** en cargas, hover, aparicion de listas, fondos ni
 * transiciones de pagina.
 */

import { h } from './dom.js';

/** Clase que dispara la animacion, por tipo de acuse. */
const CLASES = Object.freeze({
  dano: 'acuse--dano',
  curacion: 'acuse--curacion',
  equipar: 'acuse--equipar',
  puja: 'acuse--puja',
});

/**
 * Cuanto dura cada acuse antes de limpiarse. Son los tokens del kit leidos a
 * mano porque `getComputedStyle` en un `<div>` recien creado no siempre los
 * tiene en las pruebas; el CSS manda igual, esto solo decide cuando quitar la
 * clase.
 */
const MS = Object.freeze({ dano: 320, curacion: 320, equipar: 200, puja: 200 });

/** Temporizadores vivos por elemento, para no apilar acuses. */
const enCurso = new WeakMap();

/**
 * Acusa un cambio de estado sobre un elemento.
 *
 * @param {HTMLElement} elemento el que cambio.
 * @param {{tipo: 'dano'|'curacion'|'equipar'|'puja', texto?: string|null,
 *          temporizador?: {fijar: Function, quitar: Function}}} opciones
 * @returns {HTMLElement|null} el rastro legible, si lo hubo.
 */
export function acusar(elemento, { tipo, texto = null, temporizador } = {}) {
  if (!(elemento instanceof HTMLElement)) {
    throw new TypeError('acusar: se esperaba un HTMLElement');
  }
  const clase = CLASES[tipo];
  if (!clase) {
    // Un tipo nuevo exige pensar que estado comunica y anadirlo arriba.
    throw new RangeError(`acusar: «${tipo}» no es un cambio de estado conocido`);
  }

  const reloj = temporizador ?? {
    fijar: (fn, ms) => setTimeout(fn, ms),
    quitar: (id) => clearTimeout(id),
  };

  const anterior = enCurso.get(elemento);
  if (anterior) {
    reloj.quitar(anterior);
    elemento.classList.remove(clase);
    // Forzar un reflujo para que la animacion vuelva a empezar y no se
    // quede a medias con la clase puesta.
    void elemento.offsetWidth;
  }

  elemento.classList.add(clase);
  enCurso.set(
    elemento,
    reloj.fijar(() => {
      elemento.classList.remove(clase);
      enCurso.delete(elemento);
    }, MS[tipo]),
  );

  if (texto === null || texto === '') {
    return null;
  }

  // El rastro legible. Va dentro del elemento acusado para que se posicione
  // con el, y se anuncia solo: quien no ve la animacion, lo oye.
  const rastro = h('span', {
    clase: `acuse__cifra acuse__cifra--${tipo}`,
    texto,
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  elemento.append(rastro);
  reloj.fijar(() => rastro.remove(), MS[tipo] * 2);
  return rastro;
}

/**
 * Acuse de dano o de curacion a partir de dos valores de vida.
 *
 * Deduce cual de los dos es, arma el texto y no hace nada si la vida no
 * cambio — que es el caso de un mensaje repetido del canal, y animar por un
 * mensaje que no trae noticia es exactamente lo que se quiere evitar.
 *
 * @param {HTMLElement} barra
 * @param {number} antes
 * @param {number} despues
 * @param {{temporizador?: object}} [opciones]
 * @returns {HTMLElement|null}
 */
export function acusarCambioDeVida(barra, antes, despues, opciones = {}) {
  const delta = Math.round(despues) - Math.round(antes);
  if (delta === 0 || !Number.isFinite(delta)) {
    return null;
  }
  return acusar(barra, {
    tipo: delta < 0 ? 'dano' : 'curacion',
    texto: delta < 0 ? `−${Math.abs(delta)}` : `+${delta}`,
    ...opciones,
  });
}
