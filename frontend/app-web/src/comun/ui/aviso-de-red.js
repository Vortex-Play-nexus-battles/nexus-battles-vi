/**
 * El aviso de red de toda la aplicación — UXC-9 (ReconnectBanner transversal).
 *
 * ## Por qué hace falta
 *
 * Cada canal en vivo ya dice su estado en su píldora (chat, sala, subastas,
 * notificaciones), pero cuando lo que se cae es la red del jugador, cada
 * pantalla fallaba a su manera: un formulario que no respondía, un listado
 * con «no pudimos cargar», una puja que no salía. Nadie decía lo único que
 * importa: «no tienes conexión; lo que hagas ahora no llega».
 *
 * El navegador lo sabe (`offline` / `online`). Esto lo dice una sola vez por
 * página, arriba y sin tapar nada, y al volver la red lo dice también durante
 * unos segundos —con texto e icono, no solo con color—. No reintenta nada por
 * su cuenta: de eso se encargan los canales (`canal-reconectable.js`) y cada
 * vista, que son quienes saben qué hay que volver a pedir.
 *
 * @module comun/ui/aviso-de-red
 */

import { h } from './dom.js';
import { icono } from './icono.js';

/** Lo que se dice en cada estado. */
export const TEXTOS_DE_RED = Object.freeze({
  'sin-red': {
    titulo: 'Sin conexión',
    detalle:
      'Lo que hagas ahora no llega al juego. Cuando vuelva la red, las pantallas se ponen al día solas.',
    icono: 'alerta',
  },
  recuperada: {
    titulo: 'Conexión recuperada',
    detalle: 'Ya puedes seguir.',
    icono: 'check',
  },
});

/**
 * Monta el aviso y escucha la red. Una sola vez por documento: montarlo dos
 * veces (dos armazones, una vista que lo pide además) no duplica nada.
 *
 * @param {object} [opciones]
 * @param {Document} [opciones.documento]
 * @param {Window} [opciones.ventana]
 * @param {number} [opciones.esperaAlVolver] ms que se ve «Conexión recuperada»
 * @returns {() => void} para soltar los escuchadores y quitar el aviso
 */
export function vigilarRed({
  documento = globalThis.document,
  ventana = globalThis.window,
  esperaAlVolver = 4000,
} = {}) {
  const cuerpo = documento?.body;
  if (!cuerpo || typeof ventana?.addEventListener !== 'function') {
    return () => {};
  }
  if (cuerpo.querySelector('[data-zona="aviso-red"]')) {
    return () => {};
  }

  const aviso = h('div', {
    clase: 'aviso-red',
    datos: { zona: 'aviso-red', estado: 'en-linea' },
    atributos: { role: 'status', 'aria-live': 'polite' },
  });
  aviso.hidden = true;
  cuerpo.append(aviso);

  let temporizador = null;

  function pintar(estado) {
    clearTimeout(temporizador);
    const texto = TEXTOS_DE_RED[estado];
    aviso.dataset.estado = estado;
    aviso.className = `aviso-red aviso-red--${estado}`;
    aviso.replaceChildren(
      icono(texto.icono, { clase: 'icono aviso-red__icono' }),
      h('span', {
        clase: 'aviso-red__texto',
        hijos: [
          h('strong', { clase: 'aviso-red__titulo', texto: texto.titulo }),
          h('span', { clase: 'aviso-red__detalle', texto: ` ${texto.detalle}` }),
        ],
      }),
    );
    aviso.hidden = false;
    if (estado === 'recuperada') {
      temporizador = setTimeout(() => {
        aviso.hidden = true;
        aviso.dataset.estado = 'en-linea';
      }, esperaAlVolver);
    }
  }

  const alCaer = () => pintar('sin-red');
  const alVolver = () => {
    // Solo se celebra la vuelta si antes se dijo que se había ido.
    if (aviso.dataset.estado === 'sin-red') {
      pintar('recuperada');
    }
  };
  ventana.addEventListener('offline', alCaer);
  ventana.addEventListener('online', alVolver);
  if (ventana.navigator?.onLine === false) {
    pintar('sin-red');
  }

  return () => {
    clearTimeout(temporizador);
    ventana.removeEventListener('offline', alCaer);
    ventana.removeEventListener('online', alVolver);
    aviso.remove();
  };
}
