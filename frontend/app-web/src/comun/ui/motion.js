/**
 * Nexus Motion System — MOTION-1 (sistema central de movimiento).
 *
 * ## Qué es
 *
 * El único punto de entrada del movimiento hecho desde JavaScript. Las
 * animaciones de CSS (hover, foco, entradas del kit) siguen en las hojas; lo
 * que necesita una SECUENCIA —anticipar, lanzarse, golpear, volver— o nacer
 * en un instante que solo el código conoce (llega un aviso del canal) pasa
 * por aquí y usa la Web Animations API nativa (`Element.animate`). Sin
 * biblioteca: el POC de MOTION-0 (docs/motion/ANIMATION-RESEARCH.md) midió
 * que Motion y Anime.js cuestan 5–6× más hilo principal por la misma
 * coreografía, pesan 14–48 KB y serían la primera dependencia JS de tiempo de
 * ejecución del frontend.
 *
 * ## Las tres reglas que este módulo hace cumplir
 *
 * 1. **Movimiento reducido en un solo sitio.** `prefers-reduced-motion:
 *    reduce` y el modo determinista de pruebas (`<html data-test-motion="off">`)
 *    se consultan aquí, en cada llamada, y no en cada vista. La regla global
 *    de tokens.css (toda animación CSS a 0,01 ms) NO alcanza a la Web
 *    Animations API —se comprobó en el POC—, así que sin esta consulta una
 *    secuencia WAAPI ignoraría la preferencia del sistema operativo.
 * 2. **La animación nunca es la única información.** Este módulo no sabe de
 *    vida ni de daño: recibe un elemento y lo mueve. Quien llama ya escribió
 *    el dato (la cifra, el registro, el `aria-live`) ANTES de pedir el
 *    movimiento; con la preferencia puesta, el dato está y el movimiento no.
 * 3. **Solo transform, opacity y filter.** Nada de width/height/left/top:
 *    se componen fuera del hilo principal y no mueven lo de alrededor
 *    (`tokens-motion.test.js` lo vigila en las hojas; aquí, en los cuadros).
 *
 * Los tiempos y las curvas son los tokens del kit (`--motion-*`, `--ease-*`
 * en tokens.css): se leen del CSS para que haya una sola fuente y, si la hoja
 * no está (jsdom), valen lo mismo que en ella.
 *
 * @module motion
 */

/** Atributo del `<html>` que apaga el movimiento en capturas y pruebas. */
export const ATRIBUTO_DE_PRUEBA = 'data-test-motion';

const CONSULTA_REDUCE = '(prefers-reduced-motion: reduce)';

/** Duraciones por nombre (ms). Los mismos valores que `--motion-*` en tokens.css. */
export const DURACION = Object.freeze({
  instant: 80,
  fast: 120,
  normal: 200,
  slow: 320,
  turn: 600,
});

/** Curvas por nombre. Las mismas que `--ease-*` en tokens.css. */
export const CURVA = Object.freeze({
  standard: 'cubic-bezier(0.4, 0, 0.2, 1)',
  enter: 'cubic-bezier(0, 0, 0.2, 1)',
  exit: 'cubic-bezier(0.4, 0, 1, 1)',
  impact: 'cubic-bezier(0.2, 0.9, 0.3, 1)',
  spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
});

/** Propiedades que se pueden animar. Cualquier otra en un cuadro es un error. */
const PROPIEDADES_PERMITIDAS = new Set([
  'transform',
  'opacity',
  'filter',
  'offset',
  'easing',
  'composite',
]);

/**
 * Lo que devuelve `animar` cuando no hay que mover nada: tiene la forma de
 * una `Animation` ya terminada, así que quien encadena no distingue el caso.
 */
const NADA = Object.freeze({
  finished: Promise.resolve(),
  playState: 'finished',
  cancel() {},
  finish() {},
  pause() {},
  play() {},
});

/**
 * Si el movimiento está permitido ahora mismo.
 *
 * Se consulta en cada llamada, no una vez al cargar: la preferencia del
 * sistema puede cambiar con la página abierta y el atributo de pruebas se
 * pone desde fuera.
 *
 * @param {{documento?: Document, ventana?: Window}} [entorno]
 * @returns {boolean}
 */
export function permitido({ documento = globalThis.document, ventana = globalThis } = {}) {
  if (documento?.documentElement?.getAttribute?.(ATRIBUTO_DE_PRUEBA) === 'off') {
    return false;
  }
  const consulta = ventana?.matchMedia?.(CONSULTA_REDUCE);
  return !consulta?.matches;
}

/**
 * Lee un token de tiempo del CSS (`--motion-<nombre>`), para que la hoja sea
 * la única fuente; sin hoja (jsdom) vale lo de `DURACION`.
 *
 * @param {keyof typeof DURACION|number} nombre
 * @param {{documento?: Document}} [entorno]
 * @returns {number} milisegundos
 */
export function duracion(nombre, { documento = globalThis.document } = {}) {
  if (typeof nombre === 'number') {
    return Number.isFinite(nombre) && nombre > 0 ? nombre : 0;
  }
  if (!(nombre in DURACION)) {
    throw new RangeError(`motion: «${nombre}» no es una duración del sistema`);
  }
  const raiz = documento?.documentElement;
  const estilo = raiz && globalThis.getComputedStyle?.(raiz);
  const valor = estilo?.getPropertyValue?.(`--motion-${nombre}`)?.trim();
  const ms = leerMs(valor);
  return Number.isFinite(ms) ? ms : DURACION[nombre];
}

/** «200ms» → 200 · «0.2s» → 200 · otra cosa → NaN. */
function leerMs(texto) {
  const coincidencia = /^(-?\d*\.?\d+)\s*(ms|s)$/.exec(texto ?? '');
  if (!coincidencia) {
    return NaN;
  }
  const numero = Number(coincidencia[1]);
  return coincidencia[2] === 's' ? numero * 1000 : numero;
}

/**
 * Anima un elemento con la Web Animations API, respetando el sistema.
 *
 * @param {Element} elemento
 * @param {Array<Record<string, string|number>>} cuadros keyframes (solo transform/opacity/filter)
 * @param {{duracion?: keyof typeof DURACION|number, curva?: keyof typeof CURVA|string,
 *   retardo?: number, relleno?: FillMode, composicion?: CompositeOperation,
 *   iteraciones?: number}} [opciones]
 * @returns {Animation|typeof NADA} la animación, o una ya terminada si no hay que mover nada
 */
export function animar(elemento, cuadros, opciones = {}) {
  if (!(elemento instanceof Element)) {
    throw new TypeError('motion: se esperaba un Element');
  }
  for (const cuadro of cuadros) {
    for (const propiedad of Object.keys(cuadro)) {
      if (!PROPIEDADES_PERMITIDAS.has(propiedad)) {
        throw new RangeError(
          `motion: «${propiedad}» no se anima; solo transform, opacity y filter (regla 3).`,
        );
      }
    }
  }
  const {
    duracion: d = 'normal',
    curva = 'standard',
    retardo = 0,
    relleno = 'none',
    composicion = 'replace',
    iteraciones = 1,
  } = opciones;
  if (!permitido() || typeof elemento.animate !== 'function') {
    return NADA;
  }
  const ms = duracion(d);
  if (ms <= 0) {
    return NADA;
  }
  const animacion = elemento.animate(cuadros, {
    duration: ms,
    easing: CURVA[curva] ?? curva,
    delay: retardo,
    fill: relleno,
    composite: composicion,
    iterations: iteraciones,
  });
  // Cancelar una animación rechaza `finished` con AbortError; para quien
  // encadena, una secuencia interrumpida no es un error.
  animacion.finished.catch(() => {});
  return animacion;
}

/** Espera `ms`; con el movimiento apagado no espera: el estado final se ve ya. */
export function esperar(ms) {
  const espera = permitido() ? ms : 0;
  return espera > 0 ? new Promise((resolver) => setTimeout(resolver, espera)) : Promise.resolve();
}

/**
 * Encadena pasos: una función que devuelve una animación (o promesa), un
 * número (milisegundos de pausa, como el hit-stop) o una lista de pasos que
 * corren a la vez. Termina cuando termina el último.
 *
 * @param {Array<Function|number|Array<Function>>} pasos
 * @returns {Promise<void>}
 */
export async function secuencia(pasos) {
  for (const paso of pasos) {
    if (typeof paso === 'number') {
      await esperar(paso);
    } else if (Array.isArray(paso)) {
      await Promise.all(paso.map(ejecutar));
    } else {
      await ejecutar(paso);
    }
  }
}

async function ejecutar(paso) {
  const resultado = typeof paso === 'function' ? paso() : paso;
  if (resultado && typeof resultado === 'object' && 'finished' in resultado) {
    try {
      await resultado.finished;
    } catch {
      // Cancelada por una secuencia posterior: se sigue.
    }
    return;
  }
  await resultado;
}

/** Cancela todas las animaciones vivas de un elemento (vuelve a su CSS). */
export function detener(elemento) {
  for (const animacion of elemento?.getAnimations?.() ?? []) {
    animacion.cancel();
  }
}

/**
 * Una actualización del documento con View Transition si el navegador la
 * tiene y el movimiento está permitido; si no, la actualización tal cual.
 *
 * @param {() => void|Promise<void>} actualizar
 * @param {{documento?: Document}} [entorno]
 * @returns {Promise<void>}
 */
export async function transicionDePagina(actualizar, { documento = globalThis.document } = {}) {
  if (typeof documento?.startViewTransition === 'function' && permitido({ documento })) {
    const transicion = documento.startViewTransition(actualizar);
    try {
      await transicion.finished;
    } catch {
      // Saltada (otra transición en curso): la actualización ya se hizo.
    }
    return;
  }
  await actualizar();
}

const desplazamiento = (px) => `translate3d(0, ${px}px, 0)`;

/**
 * El vocabulario: cada verbo es un cambio de estado con nombre, no «mover
 * cosas». Todos devuelven la animación (o la ya terminada) para encadenar.
 */
export const motion = Object.freeze({
  /** Algo aparece: fundido y seis píxeles, una vez. */
  enter(elemento, { duracion: d = 'normal', desde = 6 } = {}) {
    return animar(
      elemento,
      [
        { opacity: 0, transform: desplazamiento(desde) },
        { opacity: 1, transform: 'none' },
      ],
      { duracion: d, curva: 'enter' },
    );
  },

  /** Algo se va: se queda invisible hasta que quien llama lo retire. */
  exit(elemento, { duracion: d = 'fast', hacia = 6 } = {}) {
    return animar(
      elemento,
      [
        { opacity: 1, transform: 'none' },
        { opacity: 0, transform: desplazamiento(hacia) },
      ],
      { duracion: d, curva: 'exit', relleno: 'forwards' },
    );
  },

  /** Se acerca un poco: para tarjetas y fichas que no son botones (el hover de botón es CSS). */
  hover(elemento) {
    return animar(
      elemento,
      [{ transform: 'none' }, { transform: 'translate3d(0, -2px, 0) scale(1.02)' }],
      {
        duracion: 'fast',
        curva: 'enter',
        relleno: 'forwards',
      },
    );
  },

  /** Se hunde y vuelve: respuesta al pulsar. */
  press(elemento) {
    return animar(
      elemento,
      [{ transform: 'scale(1)' }, { transform: 'scale(0.97)' }, { transform: 'scale(1)' }],
      {
        duracion: 'fast',
        curva: 'standard',
      },
    );
  },

  /** Un pulso, uno solo: insignia que pasa de 0 a 1, turno que llega. */
  pulse(elemento, { escala = 1.08 } = {}) {
    return animar(
      elemento,
      [{ transform: 'scale(1)' }, { transform: `scale(${escala})` }, { transform: 'scale(1)' }],
      {
        duracion: 'normal',
        curva: 'standard',
      },
    );
  },

  /** Recibe un golpe: destello y retroceso de unos píxeles, localizado en el elemento. */
  damage(elemento, { direccion = 1, fuerza = 1 } = {}) {
    const px = Math.round(14 * fuerza) * Math.sign(direccion || 1);
    return animar(
      elemento,
      [
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
        { transform: `translate3d(${px}px, 0, 0)`, filter: 'brightness(1.9)', offset: 0.3 },
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
      ],
      { duracion: 'slow', curva: 'impact', composicion: 'add' },
    );
  },

  /** Se cura: brillo que sube. */
  heal(elemento) {
    return animar(
      elemento,
      [
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
        { transform: 'translate3d(0, -3px, 0)', filter: 'brightness(1.35)', offset: 0.4 },
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
      ],
      { duracion: 'slow', curva: 'enter', composicion: 'add' },
    );
  },

  /** Golpe crítico: el daño, más fuerte, con una sacudida local de dos píxeles. */
  critical(elemento, { direccion = 1 } = {}) {
    const px = 20 * Math.sign(direccion || 1);
    return animar(
      elemento,
      [
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
        { transform: `translate3d(${px}px, 0, 0)`, filter: 'brightness(2.2)', offset: 0.25 },
        { transform: `translate3d(${px - 2}px, 0, 0)`, filter: 'brightness(1.4)', offset: 0.45 },
        { transform: `translate3d(${px + 2}px, 0, 0)`, offset: 0.6 },
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
      ],
      { duracion: 'turn', curva: 'impact', composicion: 'add' },
    );
  },

  /** Recompensa: encaje con rebote y brillo. */
  reward(elemento) {
    return animar(
      elemento,
      [
        { transform: 'scale(0.92)', filter: 'brightness(1)' },
        { transform: 'scale(1.04)', filter: 'brightness(1.3)', offset: 0.6 },
        { transform: 'scale(1)', filter: 'brightness(1)' },
      ],
      { duracion: 'slow', curva: 'spring' },
    );
  },

  /** Escudo o defensa: encaja en su sitio. */
  shield(elemento) {
    return animar(elemento, [{ transform: 'scale(0.96)' }, { transform: 'scale(1)' }], {
      duracion: 'normal',
      curva: 'spring',
    });
  },

  /** Mejora: se eleva y brilla una vez. */
  buff(elemento) {
    return animar(
      elemento,
      [
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
        { transform: 'translate3d(0, -3px, 0)', filter: 'brightness(1.2)', offset: 0.5 },
        { transform: 'translate3d(0, 0, 0)', filter: 'brightness(1)' },
      ],
      { duracion: 'normal', curva: 'enter', composicion: 'add' },
    );
  },

  /** Perjuicio: pierde color un instante. */
  debuff(elemento) {
    return animar(
      elemento,
      [
        { filter: 'saturate(1) brightness(1)' },
        { filter: 'saturate(0.4) brightness(0.9)', offset: 0.4 },
        { filter: 'saturate(1) brightness(1)' },
      ],
      { duracion: 'slow', curva: 'standard' },
    );
  },

  /**
   * Un texto que flota y se va (la cifra del golpe). Quien llama ya lo puso en
   * el DOM con su texto; aquí solo sube. Se retira al terminar si se pide.
   */
  float(elemento, { subida = 40, duracion: d = 1200, retirar = true } = {}) {
    const animacion = animar(
      elemento,
      [
        { opacity: 0, transform: 'translate3d(-50%, 8px, 0) scale(0.9)' },
        { opacity: 1, transform: 'translate3d(-50%, -4px, 0) scale(1.08)', offset: 0.12 },
        {
          opacity: 1,
          transform: `translate3d(-50%, ${-subida * 0.7}px, 0) scale(1)`,
          offset: 0.75,
        },
        { opacity: 0, transform: `translate3d(-50%, ${-subida}px, 0) scale(1)` },
      ],
      { duracion: d, curva: 'enter', relleno: 'forwards' },
    );
    if (retirar) {
      if (animacion === NADA) {
        // Sin movimiento el texto no sube, pero SE LEE: se queda quieto el
        // tiempo que habría durado y entonces se retira.
        const lectura = typeof d === 'number' ? d : DURACION[d];
        setTimeout(() => elemento.remove(), lectura);
      } else {
        animacion.finished.then(() => elemento.remove()).catch(() => elemento.remove());
      }
    }
    return animacion;
  },
});
