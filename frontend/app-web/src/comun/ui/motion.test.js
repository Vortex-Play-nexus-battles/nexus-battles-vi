/**
 * Nexus Motion System — MOTION-1.
 *
 * Lo que se comprueba es el SISTEMA, no una animación: que el movimiento se
 * apaga en un solo sitio (preferencia del sistema o modo de pruebas), que
 * ninguna llamada rompe sin Web Animations API (jsdom no la trae), que las
 * secuencias respetan el orden y las pausas, y que un cuadro solo puede
 * animar transform, opacity y filter.
 */

import { jest } from '@jest/globals';
import {
  ATRIBUTO_DE_PRUEBA,
  CURVA,
  DURACION,
  animar,
  detener,
  duracion,
  esperar,
  motion,
  permitido,
  secuencia,
  transicionDePagina,
} from './motion.js';

/** Una `Animation` de mentira que termina cuando se le dice. */
function animacionFalsa() {
  let terminar;
  const finished = new Promise((resolver) => {
    terminar = resolver;
  });
  return { finished, terminar, cancel: jest.fn(), playState: 'running' };
}

/** `matchMedia` de mentira con la preferencia que se pida. */
function conPreferencia(reduce) {
  globalThis.matchMedia = jest.fn(() => ({
    matches: reduce,
    media: '(prefers-reduced-motion: reduce)',
  }));
}

function elemento() {
  const el = document.createElement('div');
  document.body.append(el);
  return el;
}

afterEach(() => {
  document.body.innerHTML = '';
  document.documentElement.removeAttribute(ATRIBUTO_DE_PRUEBA);
  delete globalThis.matchMedia;
});

describe('permitido: el movimiento se decide en un solo sitio', () => {
  test('sin preferencia ni atributo, se mueve', () => {
    conPreferencia(false);
    expect(permitido()).toBe(true);
  });

  test('con prefers-reduced-motion: reduce, no', () => {
    conPreferencia(true);
    expect(permitido()).toBe(false);
  });

  test('con <html data-test-motion="off">, no, aunque el sistema no pida nada', () => {
    conPreferencia(false);
    document.documentElement.setAttribute(ATRIBUTO_DE_PRUEBA, 'off');
    expect(permitido()).toBe(false);
  });

  test('sin matchMedia (un entorno viejo) se mueve: la ausencia no es una preferencia', () => {
    expect(permitido()).toBe(true);
  });

  test('se consulta en cada llamada, no una vez: la preferencia puede cambiar con la página abierta', () => {
    conPreferencia(false);
    expect(permitido()).toBe(true);
    conPreferencia(true);
    expect(permitido()).toBe(false);
  });
});

describe('duracion: los tokens del kit son la única fuente', () => {
  test('sin hoja (jsdom) valen lo mismo que en tokens.css', () => {
    expect(duracion('instant')).toBe(80);
    expect(duracion('fast')).toBe(120);
    expect(duracion('normal')).toBe(200);
    expect(duracion('slow')).toBe(320);
    expect(duracion('turn')).toBe(600);
  });

  test('con la hoja, manda el CSS (`--motion-*`)', () => {
    const original = globalThis.getComputedStyle;
    globalThis.getComputedStyle = () => ({
      getPropertyValue: (p) => (p === '--motion-fast' ? '0.1s' : ''),
    });
    try {
      expect(duracion('fast')).toBe(100);
      expect(duracion('normal')).toBe(200);
    } finally {
      globalThis.getComputedStyle = original;
    }
  });

  test('un número son milisegundos tal cual; un nombre desconocido es un error', () => {
    expect(duracion(150)).toBe(150);
    expect(duracion(-5)).toBe(0);
    expect(() => duracion('eterna')).toThrow(RangeError);
  });

  test('DURACION y CURVA son los mismos valores que declara tokens.css', () => {
    expect(DURACION).toEqual({ instant: 80, fast: 120, normal: 200, slow: 320, turn: 600 });
    expect(CURVA.impact).toBe('cubic-bezier(0.2, 0.9, 0.3, 1)');
    expect(CURVA.enter).toBe('cubic-bezier(0, 0, 0.2, 1)');
  });
});

describe('animar: la Web Animations API, respetando el sistema', () => {
  test('sin Element.animate (jsdom) no rompe: devuelve una animación ya terminada', async () => {
    conPreferencia(false);
    const el = elemento();
    expect(typeof el.animate).toBe('undefined');
    const a = animar(el, [{ opacity: 0 }, { opacity: 1 }]);
    expect(a.playState).toBe('finished');
    await expect(a.finished).resolves.toBeUndefined();
  });

  test('con movimiento permitido llama a animate con los cuadros y los tokens', () => {
    conPreferencia(false);
    const el = elemento();
    const falsa = animacionFalsa();
    el.animate = jest.fn(() => falsa);
    const a = animar(el, [{ opacity: 0 }, { opacity: 1 }], { duracion: 'slow', curva: 'enter' });
    expect(a).toBe(falsa);
    expect(el.animate).toHaveBeenCalledWith([{ opacity: 0 }, { opacity: 1 }], {
      duration: 320,
      easing: CURVA.enter,
      delay: 0,
      fill: 'none',
      composite: 'replace',
      iterations: 1,
    });
  });

  test('con la preferencia puesta NO llama a animate: WAAPI no obedece la regla CSS de tokens.css', () => {
    conPreferencia(true);
    const el = elemento();
    el.animate = jest.fn();
    animar(el, [{ opacity: 0 }, { opacity: 1 }]);
    expect(el.animate).not.toHaveBeenCalled();
  });

  test('con data-test-motion="off" tampoco', () => {
    conPreferencia(false);
    document.documentElement.setAttribute(ATRIBUTO_DE_PRUEBA, 'off');
    const el = elemento();
    el.animate = jest.fn();
    animar(el, [{ opacity: 0 }, { opacity: 1 }]);
    expect(el.animate).not.toHaveBeenCalled();
  });

  test('solo transform, opacity y filter: width, left o margin en un cuadro son un error', () => {
    conPreferencia(false);
    const el = elemento();
    expect(() => animar(el, [{ width: '0' }, { width: '10px' }])).toThrow(RangeError);
    expect(() => animar(el, [{ left: '0' }])).toThrow(/solo transform, opacity y filter/);
    expect(() => animar(el, [{ opacity: 0, offset: 0.2, easing: 'linear' }])).not.toThrow();
  });

  test('algo que no es un Element es un error del llamador', () => {
    expect(() => animar(null, [])).toThrow(TypeError);
  });

  test('una curva por nombre se traduce; una cubic-bezier literal pasa tal cual', () => {
    conPreferencia(false);
    const el = elemento();
    el.animate = jest.fn(() => animacionFalsa());
    animar(el, [{ opacity: 0 }], { curva: 'impact' });
    animar(el, [{ opacity: 0 }], { curva: 'steps(4)' });
    expect(el.animate.mock.calls[0][1].easing).toBe(CURVA.impact);
    expect(el.animate.mock.calls[1][1].easing).toBe('steps(4)');
  });
});

describe('secuencia: anticipa → golpea → espera → vuelve', () => {
  test('corre los pasos en orden, las listas a la vez y los números como pausa', async () => {
    conPreferencia(false);
    const orden = [];
    const a1 = animacionFalsa();
    const a2 = animacionFalsa();
    const corriendo = secuencia([
      () => {
        orden.push('anticipa');
        return a1;
      },
      [
        () => {
          orden.push('flash');
          return a2;
        },
        () => {
          orden.push('recoil');
          return Promise.resolve();
        },
      ],
      1,
      () => {
        orden.push('vuelve');
      },
    ]);
    await Promise.resolve();
    expect(orden).toEqual(['anticipa']);
    a1.terminar();
    await Promise.resolve();
    await Promise.resolve();
    expect(orden).toEqual(['anticipa', 'flash', 'recoil']);
    a2.terminar();
    await corriendo;
    expect(orden).toEqual(['anticipa', 'flash', 'recoil', 'vuelve']);
  });

  test('con el movimiento apagado las pausas no esperan: el estado final se ve ya', async () => {
    conPreferencia(true);
    const antes = Date.now();
    await esperar(500);
    expect(Date.now() - antes).toBeLessThan(100);
  });

  test('una animación cancelada no tumba la secuencia', async () => {
    conPreferencia(false);
    const cancelada = { finished: Promise.reject(new Error('AbortError')) };
    cancelada.finished.catch(() => {});
    await expect(secuencia([() => cancelada, () => 'listo'])).resolves.toBeUndefined();
  });
});

describe('el vocabulario', () => {
  test('cada verbo devuelve algo con `finished` y solo anima transform, opacity o filter', () => {
    conPreferencia(false);
    const el = elemento();
    el.animate = jest.fn((cuadros) => {
      for (const cuadro of cuadros) {
        for (const propiedad of Object.keys(cuadro)) {
          expect(['transform', 'opacity', 'filter', 'offset']).toContain(propiedad);
        }
      }
      return animacionFalsa();
    });
    for (const verbo of [
      'enter',
      'exit',
      'hover',
      'press',
      'pulse',
      'damage',
      'heal',
      'critical',
      'reward',
      'shield',
      'buff',
      'debuff',
    ]) {
      const resultado = motion[verbo](el);
      expect(resultado).toHaveProperty('finished');
    }
    expect(el.animate).toHaveBeenCalledTimes(12);
  });

  test('damage y critical retroceden hacia donde vino el golpe (direccion) y se SUMAN al idle (composite add)', () => {
    conPreferencia(false);
    const el = elemento();
    el.animate = jest.fn(() => animacionFalsa());
    motion.damage(el, { direccion: -1 });
    const [cuadros, opciones] = el.animate.mock.calls[0];
    expect(cuadros[1].transform).toBe('translate3d(-14px, 0, 0)');
    expect(opciones.composite).toBe('add');
    motion.critical(el, { direccion: 1 });
    expect(el.animate.mock.calls[1][0][1].transform).toBe('translate3d(20px, 0, 0)');
  });

  test('float retira el texto al terminar; sin movimiento lo deja leer y lo retira después', async () => {
    jest.useFakeTimers();
    try {
      conPreferencia(true);
      const cifra = elemento();
      motion.float(cifra, { duracion: 1200 });
      expect(document.body.contains(cifra)).toBe(true);
      jest.advanceTimersByTime(1199);
      expect(document.body.contains(cifra)).toBe(true);
      jest.advanceTimersByTime(1);
      expect(document.body.contains(cifra)).toBe(false);
    } finally {
      jest.useRealTimers();
    }
    conPreferencia(false);
    const otra = elemento();
    const falsa = animacionFalsa();
    otra.animate = jest.fn(() => falsa);
    motion.float(otra);
    expect(document.body.contains(otra)).toBe(true);
    falsa.terminar();
    await falsa.finished;
    await Promise.resolve();
    expect(document.body.contains(otra)).toBe(false);
  });

  test('detener cancela lo que esté vivo y no rompe sin getAnimations', () => {
    const el = elemento();
    expect(() => detener(el)).not.toThrow();
    const viva = { cancel: jest.fn() };
    el.getAnimations = () => [viva];
    detener(el);
    expect(viva.cancel).toHaveBeenCalled();
  });
});

describe('transicionDePagina: View Transition si existe, si no la actualización tal cual', () => {
  test('sin startViewTransition actualiza igual', async () => {
    conPreferencia(false);
    const actualizar = jest.fn();
    await transicionDePagina(actualizar, { documento: {} });
    expect(actualizar).toHaveBeenCalledTimes(1);
  });

  test('con startViewTransition la usa', async () => {
    conPreferencia(false);
    const actualizar = jest.fn();
    const documento = {
      documentElement: document.documentElement,
      startViewTransition: jest.fn((fn) => {
        fn();
        return { finished: Promise.resolve() };
      }),
    };
    await transicionDePagina(actualizar, { documento });
    expect(documento.startViewTransition).toHaveBeenCalledTimes(1);
    expect(actualizar).toHaveBeenCalledTimes(1);
  });

  test('con la preferencia puesta no la usa aunque exista', async () => {
    conPreferencia(true);
    const actualizar = jest.fn();
    const documento = { documentElement: document.documentElement, startViewTransition: jest.fn() };
    await transicionDePagina(actualizar, { documento });
    expect(documento.startViewTransition).not.toHaveBeenCalled();
    expect(actualizar).toHaveBeenCalledTimes(1);
  });
});
