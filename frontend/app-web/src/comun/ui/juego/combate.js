/**
 * Combate: barra de vida, accion y turno.
 *
 * ## Por que existe
 *
 * `.accion-combate` lleva en el kit desde que se derivo de Figma —con su
 * icono, su etiqueta, su variante «sin poder» y su variante «fuera de turno»—
 * y **ninguna vista lo usaba**. La pantalla de batalla, que es el corazon del
 * producto, pinta botones grises con texto.
 *
 * La barra de vida si tenia consumidor (`panel-vidas.js`), pero cada vista que
 * la queria tenia que construir a mano sus cinco nodos. Aqui se construyen una
 * vez, con el marcado exacto que `shared/ui-kit/js/barra-vida.js` espera.
 *
 * ## Lo que NO se decide aqui
 *
 * Ni el dano, ni los umbrales de color, ni quien tiene el turno. El motor de
 * combate esta en las exclusiones del Project Charter y los umbrales del 60 %
 * y el 40 % viven en `tokens.css`. Este modulo dibuja lo que le dicen.
 */

import { h, clases } from '../dom.js';
import { icono } from '../icono.js';

/**
 * Construye el marcado de una barra de vida, listo para `actualizar()` del kit.
 *
 * Devuelve el elemento SIN pintar el valor: el porcentaje y los atributos aria
 * los pone `shared/ui-kit/js/barra-vida.js`, que es quien conoce los umbrales.
 * Separarlo evita tener dos sitios que decidan el color.
 *
 * @param {object} participante
 * @param {string} participante.nombre nombre del heroe
 * @param {string} [participante.idJugador]
 * @param {boolean} [participante.esIA]
 * @param {number} [participante.equipo]
 * @returns {HTMLElement}
 */
export function barraDeVida({ nombre, idJugador, esIA = false, equipo }) {
  const etiquetas = [];
  if (esIA) {
    etiquetas.push('IA');
  }
  if (Number.isInteger(equipo) && equipo > 0) {
    etiquetas.push(`Equipo ${equipo}`);
  }

  return h('div', {
    clase: 'barra-vida',
    datos: {
      barraVida: '',
      ...(idJugador ? { jugador: idJugador } : {}),
      ...(esIA ? { ia: 'true' } : {}),
      ...(Number.isInteger(equipo) && equipo > 0 ? { equipo: String(equipo) } : {}),
    },
    hijos: [
      h('span', { clase: 'barra-vida__nombre', texto: nombre }),
      // Quien es la maquina y de que equipo, al lado del nombre. Sin esto, en
      // una partida de seis no se sabe a quien se puede atacar.
      etiquetas.length > 0
        ? h('span', {
            clase: 'barra-vida__etiqueta t-meta',
            texto: etiquetas.join(' · '),
            datos: { etiqueta: '' },
          })
        : null,
      h('div', {
        clase: 'barra-vida__pista',
        hijos: [h('div', { clase: 'barra-vida__relleno' })],
      }),
      h('span', { clase: 'barra-vida__valor' }),
    ],
  });
}

/**
 * Boton de accion de combate: icono, nombre y, si no se puede usar, el motivo.
 *
 * ## Por que el motivo va en el texto
 *
 * El kit tiene dos variantes de «no se puede»: `--sin-poder` y
 * `--fuera-de-turno`, y las dos se ven igual de atenuadas. Un boton apagado
 * sin explicacion es el defecto clasico del juego por turnos: el jugador pulsa,
 * no pasa nada, y no sabe si le falta poder, si no es su turno o si esta rota
 * la pantalla. El motivo va en `title` y en el nombre accesible.
 *
 * @param {object} accion
 * @param {string} accion.nombre lo que se lee bajo el icono
 * @param {string} accion.icono nombre del simbolo del sprite (espada, escudo…)
 * @param {number} [accion.coste] poder que consume, si lo consume
 * @param {string|null} [accion.impedimento]
 *   por que no se puede usar ahora mismo; `null` si se puede
 * @param {'turno'|'poder'} [accion.causa] que variante del kit pintar
 * @param {boolean} [accion.secundaria] acciones de apoyo, menos destacadas
 * @param {(accion: object) => void} [alUsar]
 * @returns {HTMLButtonElement}
 */
export function accionDeCombate(accion, alUsar) {
  const { nombre, icono: simbolo, coste, impedimento = null, causa, secundaria = false } = accion;
  const bloqueada = Boolean(impedimento);
  const variante = causa === 'poder' ? 'sin-poder' : 'fuera-de-turno';

  const boton = /** @type {HTMLButtonElement} */ (
    h('button', {
      clase: clases(
        'accion-combate',
        secundaria && 'accion-combate--secundaria',
        bloqueada && `accion-combate--${variante}`,
      ),
      atributos: {
        type: 'button',
        disabled: bloqueada,
        // El motivo, no solo el gris.
        title: impedimento ?? (Number.isFinite(coste) ? `Cuesta ${coste} de poder` : null),
        'aria-label': [nombre, Number.isFinite(coste) ? `cuesta ${coste}` : null, impedimento]
          .filter(Boolean)
          .join('. '),
      },
      datos: { accion: nombre },
      hijos: [
        icono(simbolo, { clase: 'accion-combate__icono', etiqueta: null }),
        h('span', { clase: 'accion-combate__etiqueta', texto: nombre }),
        Number.isFinite(coste)
          ? h('span', { clase: 'accion-combate__coste t-meta', texto: `${coste}` })
          : null,
      ],
    })
  );

  if (typeof alUsar === 'function') {
    boton.addEventListener('click', () => {
      if (!bloqueada) {
        alUsar(accion);
      }
    });
  }
  return boton;
}

/**
 * Indicador de turno, sobre el `.turno-actual` que ya trae el kit.
 *
 * ## Por que es una region `aria-live`
 *
 * De quien es el turno cambia solo, sin que el jugador toque nada: llega por el
 * canal en tiempo real. Un cambio de color que nadie anuncia deja fuera a quien
 * usa lector de pantalla y, con `prefers-reduced-motion`, tambien a quien
 * desactivo las animaciones — el latido del punto esta detras de
 * `no-preference`. El texto es la fuente de verdad; color y latido refuerzan.
 *
 * @param {{texto?: string, propio?: boolean, ronda?: number}} [estado]
 * @returns {HTMLElement}
 */
export function indicadorDeTurno({ texto = 'Esperando…', propio = false, ronda } = {}) {
  return h('p', {
    clase: 'turno-actual',
    // `data-mio` es el atributo que ya usa el CSS del kit: relleno de acento y
    // latido del punto. No se inventa uno nuevo.
    datos: { mio: propio ? 'true' : 'false' },
    atributos: { role: 'status', 'aria-live': 'polite', 'aria-atomic': 'true' },
    hijos: [
      Number.isFinite(ronda)
        ? h('span', { clase: 'turno-actual__ronda', texto: `Ronda ${ronda}` })
        : null,
      h('span', { clase: 'turno-actual__texto', texto }),
    ],
  });
}

/**
 * Cambia a quien le toca sin rehacer el nodo, para no perder el foco ni
 * disparar dos anuncios seguidos del lector.
 *
 * @param {HTMLElement} indicador nodo devuelto por `indicadorDeTurno`
 * @param {{texto: string, propio?: boolean, ronda?: number}} estado
 */
export function actualizarTurno(indicador, { texto, propio = false, ronda }) {
  if (!(indicador instanceof HTMLElement)) {
    throw new TypeError('indicadorDeTurno: se esperaba un HTMLElement.');
  }
  indicador.dataset.mio = propio ? 'true' : 'false';

  const cuerpo = indicador.querySelector('.turno-actual__texto');
  if (cuerpo) {
    cuerpo.textContent = texto;
  }
  const marcaRonda = indicador.querySelector('.turno-actual__ronda');
  if (marcaRonda && Number.isFinite(ronda)) {
    marcaRonda.textContent = `Ronda ${ronda}`;
  }
}
