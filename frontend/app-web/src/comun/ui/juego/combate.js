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
      // `title`: en el HUD compacto de una partida de seis el nombre se corta.
      h('span', { clase: 'barra-vida__nombre', texto: nombre, atributos: { title: nombre } }),
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
  const {
    nombre,
    icono: simbolo,
    coste,
    impedimento = null,
    causa,
    secundaria = false,
    // UXC-2 — la accion especial: su linea de datos visible (coste y carga),
    // su efecto (descripcion accesible) y el id del motivo comun del grupo.
    detalle = null,
    efecto = null,
    describidaPor = null,
    especial = false,
    // Datos cortos con icono (coste, carga): «⚡2  ⟳1». Cada uno lleva su
    // etiqueta accesible; el conjunto va tambien en el nombre accesible.
    insignias = [],
  } = accion;
  const bloqueada = Boolean(impedimento);
  const variante = causa === 'poder' ? 'sin-poder' : 'fuera-de-turno';

  const boton = /** @type {HTMLButtonElement} */ (
    h('button', {
      clase: clases(
        'accion-combate',
        secundaria && 'accion-combate--secundaria',
        especial && 'accion-combate--especial',
        bloqueada && `accion-combate--${variante}`,
      ),
      atributos: {
        type: 'button',
        disabled: bloqueada,
        // El motivo, no solo el gris.
        title:
          [impedimento, efecto].filter(Boolean).join(' · ') ||
          (Number.isFinite(coste) ? `Cuesta ${coste} de poder` : null),
        'aria-label': [
          nombre,
          Number.isFinite(coste) ? `cuesta ${coste} de poder` : null,
          detalle && !Number.isFinite(coste) ? detalle : null,
          ...insignias
            .filter((i) => i?.etiqueta && !/poder/.test(i.etiqueta))
            .map((i) => i.etiqueta),
          efecto,
          impedimento,
        ]
          .filter(Boolean)
          .join('. '),
        'aria-describedby': describidaPor,
      },
      datos: { accion: nombre },
      hijos: [
        icono(simbolo, { clase: 'accion-combate__icono', etiqueta: null }),
        h('span', { clase: 'accion-combate__etiqueta', texto: nombre }),
        pieDeAccion({ insignias, detalle, coste }),
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
 * La linea de abajo de una accion: insignias (coste y carga con icono), un
 * texto de detalle, o el coste a secas. Lo primero que haya.
 *
 * @param {{insignias: Array<{icono: string, texto: string, etiqueta?: string}>,
 *   detalle: string|null, coste?: number}} datos
 * @returns {HTMLElement|null}
 */
function pieDeAccion({ insignias, detalle, coste }) {
  if (insignias.length > 0) {
    return h('span', {
      clase: 'accion-combate__insignias',
      atributos: { 'aria-hidden': 'true' },
      hijos: insignias.map((insignia) =>
        h('span', {
          clase: 'accion-combate__insignia',
          atributos: { title: insignia.etiqueta ?? null },
          hijos: [
            icono(insignia.icono, {
              clase: 'icono accion-combate__insignia-icono',
              etiqueta: null,
            }),
            h('span', { texto: insignia.texto }),
          ],
        }),
      ),
    });
  }
  if (detalle) {
    return h('span', { clase: 'accion-combate__detalle t-meta', texto: detalle });
  }
  if (Number.isFinite(coste)) {
    return h('span', { clase: 'accion-combate__coste t-meta', texto: `${coste}` });
  }
  return null;
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

/**
 * El poder del héroe — UXC-2 (PowerMeter).
 *
 * §6.1.1: las acciones especiales gastan poder y el poder se recupera dos
 * puntos por turno durante el combate. El MÁXIMO es un dato real (las cifras
 * del héroe con su equipo). El valor ACTUAL solo se pinta si el servidor lo
 * manda: hoy la partida no lo lleva (`HeroeEnPartida` no tiene poder), así que
 * el medidor dice la capacidad y que no hay seguimiento, en vez de enseñar un
 * «10/10» que nadie ha calculado.
 *
 * @param {{maximo: number|null, actual?: number|null}} poder
 * @returns {HTMLElement|null} `null` si no se conoce ni el máximo
 */
export function medidorDePoder({ maximo, actual = null }) {
  if (!Number.isFinite(maximo) || maximo <= 0) {
    return null;
  }
  const conActual = Number.isFinite(actual);
  const segmentos = Math.min(Math.round(maximo), 12);
  return h('div', {
    clase: clases('medidor-poder', !conActual && 'medidor-poder--sin-seguimiento'),
    atributos: conActual
      ? {
          role: 'meter',
          'aria-valuemin': '0',
          'aria-valuemax': String(maximo),
          'aria-valuenow': String(actual),
          'aria-label': `Poder: ${actual} de ${maximo}`,
        }
      : {},
    hijos: [
      h('p', {
        clase: 'medidor-poder__cabeza',
        hijos: [
          icono('rayo', { clase: 'icono medidor-poder__icono', etiqueta: null }),
          h('span', { clase: 'medidor-poder__etiqueta', texto: 'Poder' }),
          h('span', {
            clase: 'medidor-poder__valor',
            texto: conActual ? `${actual}/${maximo}` : `máx. ${maximo}`,
          }),
        ],
      }),
      h('span', {
        clase: 'medidor-poder__pista',
        atributos: { 'aria-hidden': 'true' },
        hijos: Array.from({ length: segmentos }, (_, i) =>
          h('span', {
            clase: clases(
              'medidor-poder__segmento',
              conActual &&
                i < Math.round((actual / maximo) * segmentos) &&
                'medidor-poder__segmento--lleno',
            ),
          }),
        ),
      }),
      h('p', {
        clase: 'medidor-poder__nota t-meta',
        texto: conActual ? '+2 por turno' : 'Sin seguimiento',
        atributos: {
          title: conActual
            ? 'El poder se recupera 2 puntos por turno durante el combate.'
            : 'La partida todavía no lleva la cuenta del poder: se muestra el máximo de tu héroe.',
        },
      }),
      conActual
        ? null
        : h('span', {
            clase: 'solo-lectores',
            texto:
              'La partida todavía no lleva la cuenta del poder: se muestra el máximo de tu héroe.',
          }),
    ],
  });
}

/** Símbolo del sprite para un efecto, por su código o su icono. */
const ICONO_DE_EFECTO = Object.freeze({
  VENENO: 'gota',
  QUEMADURA: 'fuego',
  FUEGO: 'fuego',
  CONGELADO: 'copo',
  HIELO: 'copo',
  DEFENSA: 'escudo',
  ESCUDO: 'escudo',
  ATAQUE: 'espada',
  CURACION: 'cruz',
  SANAR: 'cruz',
  INMUNE: 'escudo-check',
});

/**
 * Un efecto activo sobre un héroe — UXC-2 (EffectChip).
 *
 * §7.6: «todos los efectos o controles deben ser representados por iconos».
 * `Efecto` del contrato trae `codigo`, `nombre`, `icono` opcional y
 * `turnosRestantes` opcional. El icono se busca por el `icono` que mande el
 * servidor o por el código; si no se reconoce, una estrella neutra. El nombre
 * va siempre escrito (y en el nombre accesible), así que el icono refuerza.
 *
 * @param {{codigo: string, nombre: string, icono?: string|null, turnosRestantes?: number|null}} efecto
 * @param {{compacto?: boolean}} [opciones] solo icono y turnos (HUD); el nombre queda en `title`
 * @returns {HTMLElement}
 */
export function chipDeEfecto(efecto, { compacto = false } = {}) {
  const clave = String(efecto?.icono ?? efecto?.codigo ?? '').toUpperCase();
  const simbolo =
    ICONO_DE_EFECTO[clave] ??
    ICONO_DE_EFECTO[String(efecto?.codigo ?? '').toUpperCase()] ??
    'estrella';
  const turnos = Number.isInteger(efecto?.turnosRestantes) ? efecto.turnosRestantes : null;
  const accesible = [
    efecto?.nombre ?? efecto?.codigo ?? 'Efecto',
    turnos !== null ? `${turnos} ${turnos === 1 ? 'turno' : 'turnos'} restantes` : null,
  ]
    .filter(Boolean)
    .join(', ');
  return h('span', {
    clase: clases('efecto', compacto && 'efecto--compacto'),
    datos: { efecto: efecto?.codigo ?? '' },
    atributos: { title: accesible },
    hijos: [
      icono(simbolo, { clase: 'icono efecto__icono', etiqueta: null }),
      compacto
        ? h('span', { clase: 'solo-lectores', texto: accesible })
        : h('span', { clase: 'efecto__nombre', texto: efecto?.nombre ?? efecto?.codigo ?? '' }),
      turnos !== null
        ? h('span', {
            clase: 'efecto__turnos',
            texto: `${turnos}t`,
            atributos: { 'aria-hidden': 'true' },
          })
        : null,
    ],
  });
}

/**
 * El registro del combate — UXC-2 (CombatLog).
 *
 * Una lista con `role="log"`: cada línea nueva se anuncia sola (región viva
 * educada) y se queda escrita, así que quien no vio la animación del golpe, o
 * la tiene desactivada, sabe qué pasó. Se desplaza sola a la última línea
 * salvo que quien mira haya subido a leer las anteriores.
 *
 * @param {{maximo?: number}} [opciones] líneas que se conservan
 * @returns {{elemento: HTMLElement, anotar: (linea: {texto: string, tono?: string, icono?: string}) => void,
 *   lineas: () => number}}
 */
export function registroDeCombate({ maximo = 40 } = {}) {
  // El `role="log"` va en el contenedor y no en la lista: sobre el `<ol>` le
  // quitaria su semantica de lista y cada `<li>` quedaria huerfano (axe:
  // listitem).
  const lista = h('ol', { clase: 'registro-combate__lista' });
  const elemento = h('div', {
    clase: 'registro-combate',
    atributos: {
      role: 'log',
      'aria-live': 'polite',
      'aria-relevant': 'additions',
      // Se desplaza: tiene que poder alcanzarse con el teclado (axe:
      // scrollable-region-focusable).
      tabindex: '0',
      'aria-label': 'Registro del combate',
    },
    hijos: [lista],
  });

  function anotar({ texto, tono = 'sistema', icono: simbolo = 'reloj' }) {
    if (!texto) {
      return;
    }
    const cerca =
      elemento.scrollHeight - elemento.scrollTop - elemento.clientHeight < 24 ||
      !elemento.scrollHeight;
    lista.append(
      h('li', {
        clase: `registro-combate__linea registro-combate__linea--${tono}`,
        datos: { tono },
        hijos: [
          icono(simbolo, { clase: 'icono registro-combate__icono', etiqueta: null }),
          h('span', { texto }),
        ],
      }),
    );
    while (lista.children.length > maximo) {
      lista.firstElementChild.remove();
    }
    if (cerca) {
      elemento.scrollTop = elemento.scrollHeight;
      // Otra vez tras maquetar: la linea nueva puede partirse en dos y el
      // primer calculo se queda corto.
      globalThis.requestAnimationFrame?.(() => {
        elemento.scrollTop = elemento.scrollHeight;
      });
    }
  }

  return { elemento, anotar, lineas: () => lista.children.length };
}

/**
 * La cifra que salta sobre el héroe golpeado — UXC-2 (DamageCallout).
 *
 * Es decorativa (`aria-hidden`): el registro ya lo dice con palabras. Con
 * `prefers-reduced-motion` no se mueve; aparece y se va.
 *
 * @param {{cifra: string, etiqueta?: string|null, tono?: string}} impacto
 * @returns {HTMLElement}
 */
export function impactoEnCampo({ cifra, etiqueta = null, tono = 'dano' }) {
  return h('span', {
    clase: `impacto impacto--${tono}`,
    atributos: { 'aria-hidden': 'true' },
    hijos: [
      h('span', { clase: 'impacto__cifra', texto: cifra }),
      etiqueta ? h('span', { clase: 'impacto__etiqueta', texto: etiqueta }) : null,
    ],
  });
}
