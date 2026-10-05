/**
 * El vigilante de la sesión — R17.
 *
 * Lo que se afirma aquí es lo que un profesor que no conoce el juego vería
 * mal si faltara: que una sesión que ya no vale le lleva al login diciendo
 * por qué y volviendo después a donde estaba; que un servicio caído NO le
 * echa; y que cerrar en una pestaña cierra en todas.
 */

import { jest } from '@jest/globals';

import { CLAVES } from './sesion.js';
import {
  EVENTO_RECHAZO,
  VALIDEZ_DE_COMPROBACION_MS,
  VEREDICTOS,
  caducidadDe,
  comprobarCredencial,
  crearVigilante,
} from './vigilante-sesion.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/vigilante-sesion.js';
const UBICACION = {
  origin: 'http://localhost:8099',
  pathname: '/frontend/app-web/src/contenido/inventario/inventario.html',
  search: '?vista=cuadricula',
  hash: '',
};

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `vigilante.${cuerpo}.firma`;
}

/** Reloj manual: el tiempo solo avanza cuando la prueba lo dice. */
function crearReloj(inicio = 1_700_000_000_000) {
  let ahora = inicio;
  let siguienteId = 1;
  const pendientes = new Map();
  return {
    ahora: () => ahora,
    setTimeout(funcion, ms) {
      const id = siguienteId++;
      pendientes.set(id, { funcion, cuando: ahora + ms });
      return id;
    },
    clearTimeout(id) {
      pendientes.delete(id);
    },
    avanzar(ms) {
      ahora += ms;
      for (const [id, { funcion, cuando }] of [...pendientes]) {
        if (cuando <= ahora) {
          pendientes.delete(id);
          funcion();
        }
      }
    },
    pendientes,
  };
}

/** Un objetivo de eventos mínimo, para no depender de `window` en las pruebas. */
function crearObjetivo(extra = {}) {
  const objetivo = new EventTarget();
  return Object.assign(objetivo, extra);
}

function conSesion(reloj, segundos = 3600) {
  const exp = Math.floor((reloj.ahora() + segundos * 1000) / 1000);
  sessionStorage.setItem(CLAVES.token, tokenCon({ uid: 'u-1', sub: 'Ana', rol: 'JUGADOR', exp }));
  sessionStorage.setItem(CLAVES.apodo, 'Ana');
  sessionStorage.setItem(CLAVES.rol, 'JUGADOR');
}

function montar(reloj, extra = {}) {
  const navegar = jest.fn();
  const ventana = crearObjetivo();
  const documento = crearObjetivo({ visibilityState: 'visible' });
  const canal = { alCerrarse: null, sesionParaCompartir: null, soltado: false };
  const vigilante = crearVigilante({
    almacen: sessionStorage,
    ubicacion: UBICACION,
    navegar,
    base: BASE,
    ahora: reloj.ahora,
    reloj,
    ventana,
    documento,
    comprobar: jest.fn(() => Promise.resolve(VEREDICTOS.VALIDA)),
    escuchar: ({ alCerrarse, sesionParaCompartir }) => {
      canal.alCerrarse = alCerrarse;
      canal.sesionParaCompartir = sesionParaCompartir;
      return () => {
        canal.soltado = true;
      };
    },
    ...extra,
  });
  return { vigilante, navegar, ventana, documento, canal };
}

function destino(navegar) {
  return new URL(navegar.mock.calls.at(-1)[0]);
}

beforeEach(() => {
  sessionStorage.clear();
});

describe('crearVigilante', () => {
  test('sin sesión no hay nada que vigilar', () => {
    const reloj = crearReloj();
    expect(montar(reloj).vigilante).toBeNull();
  });

  test('a la hora que dice el token, al login con el motivo y la vuelta', () => {
    const reloj = crearReloj();
    conSesion(reloj, 60);
    const { navegar } = montar(reloj);

    reloj.avanzar(59_000);
    expect(navegar).not.toHaveBeenCalled();

    reloj.avanzar(1_000);
    expect(navegar).toHaveBeenCalledTimes(1);
    const url = destino(navegar);
    expect(url.pathname).toBe('/frontend/app-web/src/cuentas/login.html');
    expect(url.searchParams.get('motivo')).toBe('caducada');
    expect(url.searchParams.get('volver')).toBe(
      '/frontend/app-web/src/contenido/inventario/inventario.html?vista=cuadricula',
    );
    // Y la sesión muerta no se queda en el navegador.
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
  });

  test('al volver a la pestaña se mira el reloj otra vez (el equipo pudo dormir)', () => {
    const reloj = crearReloj();
    conSesion(reloj, 60);
    const { navegar, documento } = montar(reloj);
    // El temporizador se «pierde» (equipo suspendido) y el token ya caducó.
    reloj.pendientes.clear();
    reloj.avanzar(120_000);
    expect(navegar).not.toHaveBeenCalled();

    documento.dispatchEvent(new Event('visibilitychange'));
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(destino(navegar).searchParams.get('motivo')).toBe('caducada');
  });

  test('«Atrás» tras cerrar sesión: la página resucitada de la caché vuelve al login', () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const { navegar, ventana } = montar(reloj);

    // Otra parte de la página cerró sesión y navegó; el navegador la
    // devuelve desde su caché de páginas.
    sessionStorage.clear();
    const evento = new Event('pageshow');
    evento.persisted = true;
    ventana.dispatchEvent(evento);

    expect(navegar).toHaveBeenCalledTimes(1);
    expect(destino(navegar).pathname).toBe('/frontend/app-web/src/cuentas/login.html');

    // Si vuelve a resucitar, vuelve a echar: el oyente sigue puesto.
    ventana.dispatchEvent(evento);
    expect(navegar).toHaveBeenCalledTimes(2);
  });

  test('un pageshow normal (no desde la caché) no hace nada', () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const { navegar, ventana } = montar(reloj);
    ventana.dispatchEvent(new Event('pageshow'));
    expect(navegar).not.toHaveBeenCalled();
  });

  test('cerrar en otra pestaña cierra aquí, y sin ofrecer volver', () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const { navegar, canal } = montar(reloj);

    canal.alCerrarse();

    const url = destino(navegar);
    expect(url.searchParams.get('motivo')).toBe('cerrada');
    expect(url.searchParams.get('volver')).toBeNull();
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    expect(canal.soltado).toBe(true);
  });

  test('comparte la sesión con otra pestaña solo mientras vale', () => {
    const reloj = crearReloj();
    conSesion(reloj, 10);
    const { canal } = montar(reloj);

    expect(canal.sesionParaCompartir()).toEqual(
      expect.objectContaining({ apodo: 'Ana', rol: 'JUGADOR', uid: 'u-1' }),
    );
    expect(canal.sesionParaCompartir().token).toBe(sessionStorage.getItem(CLAVES.token));

    reloj.pendientes.clear();
    reloj.avanzar(11_000);
    expect(canal.sesionParaCompartir()).toBeNull();
  });
});

describe('un servicio rechaza el token', () => {
  test('si el emisor dice que no vale, al login', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const comprobar = jest.fn(() => Promise.resolve(VEREDICTOS.INVALIDA));
    const { navegar, ventana, vigilante } = montar(reloj, { comprobar });
    const token = sessionStorage.getItem(CLAVES.token);

    ventana.dispatchEvent(new CustomEvent(EVENTO_RECHAZO, { detail: { estado: 401 } }));
    await vigilante.comprobar();

    expect(comprobar).toHaveBeenCalledWith(token);
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(destino(navegar).searchParams.get('motivo')).toBe('caducada');
  });

  test('si el emisor dice que vale, NO se echa a nadie (el fallo era del servicio)', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const comprobar = jest.fn(() => Promise.resolve(VEREDICTOS.VALIDA));
    const { navegar, ventana, vigilante } = montar(reloj, { comprobar });

    ventana.dispatchEvent(new CustomEvent(EVENTO_RECHAZO));
    await vigilante.comprobar();

    expect(navegar).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(CLAVES.token)).not.toBeNull();
  });

  test('si el emisor no contesta, tampoco: una caída no es una sesión caducada', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const comprobar = jest.fn(() => Promise.resolve(VEREDICTOS.DESCONOCIDA));
    const { navegar, vigilante } = montar(reloj, { comprobar });

    await vigilante.comprobar();

    expect(navegar).not.toHaveBeenCalled();
  });

  test('una ráfaga de rechazos hace UNA comprobación, y un «vale» dura un rato', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const comprobar = jest.fn(() => Promise.resolve(VEREDICTOS.VALIDA));
    const { ventana, vigilante } = montar(reloj, { comprobar });

    for (let i = 0; i < 5; i++) {
      ventana.dispatchEvent(new CustomEvent(EVENTO_RECHAZO));
    }
    await vigilante.comprobar();
    expect(comprobar).toHaveBeenCalledTimes(1);

    // Dentro de la ventana de validez no se vuelve a preguntar...
    await vigilante.comprobar();
    expect(comprobar).toHaveBeenCalledTimes(1);

    // ...y pasada, sí.
    reloj.avanzar(VALIDEZ_DE_COMPROBACION_MS + 1);
    await vigilante.comprobar();
    expect(comprobar).toHaveBeenCalledTimes(2);
  });

  test('detener suelta todos los oyentes', async () => {
    const reloj = crearReloj();
    conSesion(reloj);
    const comprobar = jest.fn(() => Promise.resolve(VEREDICTOS.INVALIDA));
    const { navegar, ventana, vigilante, canal } = montar(reloj, { comprobar });

    vigilante.detener();
    ventana.dispatchEvent(new CustomEvent(EVENTO_RECHAZO));
    await Promise.resolve();

    expect(comprobar).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(canal.soltado).toBe(true);
    expect(reloj.pendientes.size).toBe(0);
  });
});

describe('comprobarCredencial', () => {
  const respuesta = (status) => Promise.resolve({ ok: status >= 200 && status < 300, status });

  test('pregunta al emisor con el token, sin caché y sin pasar por el interceptor', async () => {
    const fetchImpl = jest.fn(() => respuesta(200));
    await expect(comprobarCredencial('el-token', fetchImpl)).resolves.toBe(VEREDICTOS.VALIDA);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/auth/onboarding', {
      headers: { Authorization: 'Bearer el-token', Accept: 'application/json' },
      cache: 'no-store',
    });
  });

  test.each([
    [401, VEREDICTOS.INVALIDA],
    [403, VEREDICTOS.INVALIDA],
    [500, VEREDICTOS.DESCONOCIDA],
    [502, VEREDICTOS.DESCONOCIDA],
  ])('un %i es «%s»', async (status, veredicto) => {
    await expect(comprobarCredencial('t', () => respuesta(status))).resolves.toBe(veredicto);
  });

  test('sin red, sin token o sin fetch: desconocida', async () => {
    await expect(comprobarCredencial('t', () => Promise.reject(new Error('red')))).resolves.toBe(
      VEREDICTOS.DESCONOCIDA,
    );
    await expect(comprobarCredencial(null, jest.fn())).resolves.toBe(VEREDICTOS.DESCONOCIDA);
    await expect(comprobarCredencial('t', undefined)).resolves.toBe(VEREDICTOS.DESCONOCIDA);
  });
});

describe('caducidadDe', () => {
  test('lee el exp del token, en milisegundos', () => {
    expect(caducidadDe({ token: tokenCon({ exp: 100 }) })).toBe(100_000);
    expect(caducidadDe({ token: tokenCon({}) })).toBeNull();
    expect(caducidadDe({ token: 'ilegible' })).toBeNull();
    expect(caducidadDe(null)).toBeNull();
  });
});
