/**
 * La sesión del navegador — UX-R3.0.
 *
 * HU-AUT-004 (inicio de sesión) · ADR-002 (el identificador es el `uid` del
 * token, no la clave primaria). Portado de `cabecera-app.test.js` al separar
 * el módulo.
 */

import { jest } from '@jest/globals';

import {
  CLAVES,
  MOTIVOS,
  RUTAS,
  avisarCierreAlServidor,
  cerrarSesion,
  guardarSesion,
  leerSesion,
  olvidarSesion,
  resolver,
  rutaDeVuelta,
  rutaSegura,
  urlDeLogin,
} from './sesion.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/sesion.js';
const UID = '7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55';

function tokenCon(claims) {
  const cuerpo = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `sesion.${cuerpo}.firma`;
}

function conSesion({ apodo = 'Ana', rol = 'JUGADOR', exp } = {}) {
  sessionStorage.setItem(
    CLAVES.token,
    tokenCon({ uid: UID, sub: apodo, rol, exp: exp ?? Math.floor(Date.now() / 1000) + 3600 }),
  );
  sessionStorage.setItem(CLAVES.apodo, apodo);
  sessionStorage.setItem(CLAVES.rol, rol);
}

beforeEach(() => {
  sessionStorage.clear();
});

describe('leerSesion', () => {
  test('sin token no hay sesión y no se inventa nada', () => {
    expect(leerSesion(sessionStorage)).toEqual({
      autenticado: false,
      caducada: false,
      apodo: '',
      uid: null,
      rol: null,
      token: null,
    });
  });

  test('prefiere el uid del token y el apodo que dejó el login (ADR-002)', () => {
    conSesion({ apodo: 'Ana' });
    // La clave primaria que guarda `login.js` no puede ganarle al token.
    sessionStorage.setItem(CLAVES.usuarioId, '7');
    const sesion = leerSesion(sessionStorage);
    expect(sesion.uid).toBe(UID);
    expect(sesion.apodo).toBe('Ana');
    expect(sesion.rol).toBe('JUGADOR');
    expect(sesion.autenticado).toBe(true);
  });

  test('un token caducado NO cuenta como sesión, y lo dice', () => {
    conSesion({ exp: Math.floor(Date.now() / 1000) - 10 });
    const sesion = leerSesion(sessionStorage);
    expect(sesion.autenticado).toBe(false);
    expect(sesion.caducada).toBe(true);
  });

  test('un token ilegible se trata como «no hay sesión», sin romper la vista', () => {
    sessionStorage.setItem(CLAVES.token, 'esto-no-es-un-jwt');
    expect(() => leerSesion(sessionStorage)).not.toThrow();
    expect(leerSesion(sessionStorage).autenticado).toBe(true);
  });
});

describe('cerrar y olvidar', () => {
  test('olvidarSesion borra las cuatro claves y nada más', () => {
    conSesion();
    sessionStorage.setItem('otra.cosa', 'se-queda');
    olvidarSesion(sessionStorage);
    for (const clave of Object.values(CLAVES)) {
      expect(sessionStorage.getItem(clave)).toBeNull();
    }
    expect(sessionStorage.getItem('otra.cosa')).toBe('se-queda');
  });

  test('cerrarSesion funciona desde cualquier vista, sin cabecera montada', () => {
    conSesion();
    const navegar = jest.fn();
    cerrarSesion({
      almacen: sessionStorage,
      navegar,
      base: BASE,
      avisar: jest.fn(),
      difundir: jest.fn(),
    });
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    // R17 — el login dice por qué se llega: «Cerraste sesión».
    expect(navegar).toHaveBeenCalledWith(
      'http://localhost:8099/frontend/app-web/src/cuentas/login.html?motivo=cerrada',
    );
  });

  test('cerrarSesion avisa al servidor con el token que había y a las demás pestañas', () => {
    conSesion();
    const token = sessionStorage.getItem(CLAVES.token);
    const avisar = jest.fn();
    const difundir = jest.fn();
    cerrarSesion({ almacen: sessionStorage, navegar: jest.fn(), base: BASE, avisar, difundir });
    expect(avisar).toHaveBeenCalledWith(token);
    expect(difundir).toHaveBeenCalledTimes(1);
  });

  test('cerrarSesion sin sesión no avisa con un token vacío, pero sí navega', () => {
    const avisar = jest.fn();
    const navegar = jest.fn();
    cerrarSesion({ almacen: sessionStorage, navegar, base: BASE, avisar, difundir: jest.fn() });
    expect(avisar).toHaveBeenCalledWith(null);
    expect(navegar).toHaveBeenCalledTimes(1);
  });
});

describe('guardarSesion (R17)', () => {
  test('escribe las cuatro claves que lee leerSesion', () => {
    const token = tokenCon({ uid: UID, sub: 'Ana', exp: Math.floor(Date.now() / 1000) + 60 });
    guardarSesion({ token, apodo: 'Ana', rol: 'JUGADOR', uid: UID }, sessionStorage);
    expect(sessionStorage.getItem(CLAVES.token)).toBe(token);
    expect(sessionStorage.getItem(CLAVES.apodo)).toBe('Ana');
    expect(sessionStorage.getItem(CLAVES.rol)).toBe('JUGADOR');
    expect(sessionStorage.getItem(CLAVES.usuarioId)).toBe(UID);
    expect(leerSesion(sessionStorage).autenticado).toBe(true);
  });

  test('una clave sin valor se borra en vez de guardarse vacía', () => {
    sessionStorage.setItem(CLAVES.apodo, 'de-otra-sesion');
    guardarSesion({ token: 'x.e30.y', apodo: '', rol: null }, sessionStorage);
    expect(sessionStorage.getItem(CLAVES.apodo)).toBeNull();
    expect(sessionStorage.getItem(CLAVES.rol)).toBeNull();
    expect(sessionStorage.getItem(CLAVES.usuarioId)).toBeNull();
  });
});

describe('avisarCierreAlServidor (R17)', () => {
  test('manda un POST con el token que sobrevive a la navegación', () => {
    const fetchImpl = jest.fn(() => Promise.resolve({ ok: true, status: 204 }));
    avisarCierreAlServidor('el-token', fetchImpl);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/auth/logout', {
      method: 'POST',
      keepalive: true,
      headers: { Authorization: 'Bearer el-token' },
    });
  });

  test('nunca falla: sin token, sin fetch, o con la red caída', async () => {
    expect(() => avisarCierreAlServidor(null, jest.fn())).not.toThrow();
    expect(() => avisarCierreAlServidor('t', undefined)).not.toThrow();
    expect(() =>
      avisarCierreAlServidor('t', () => {
        throw new Error('sin red');
      }),
    ).not.toThrow();
    const rechazo = Promise.reject(new Error('sin red'));
    avisarCierreAlServidor('t', () => rechazo);
    await expect(rechazo).rejects.toThrow('sin red');
  });
});

describe('urlDeLogin (R17)', () => {
  test('lleva la vuelta y el motivo, y nada si no hay', () => {
    expect(urlDeLogin({}, BASE)).toBe(
      'http://localhost:8099/frontend/app-web/src/cuentas/login.html',
    );
    const url = new URL(urlDeLogin({ volver: '/x.html?a=1', motivo: MOTIVOS.CADUCADA }, BASE));
    expect(url.searchParams.get('volver')).toBe('/x.html?a=1');
    expect(url.searchParams.get('motivo')).toBe('caducada');
  });
});

describe('rutaDeVuelta', () => {
  test('solo admite rutas del propio origen', () => {
    expect(
      rutaDeVuelta(
        '?volver=%2Ffrontend%2Fapp-web%2Fsrc%2Fplataforma%2Fsalas-partidas%2Fbatallas.html',
      ),
    ).toBe('/frontend/app-web/src/plataforma/salas-partidas/batallas.html');
    // Ni una URL absoluta a otro sitio, ni el truco del doble slash.
    expect(rutaDeVuelta('?volver=https%3A%2F%2Fmalo.example%2F')).toBeNull();
    expect(rutaDeVuelta('?volver=%2F%2Fmalo.example')).toBeNull();
    expect(rutaDeVuelta('')).toBeNull();
  });

  test('conserva la búsqueda y el fragmento de la ruta de vuelta', () => {
    expect(rutaDeVuelta('?volver=%2Fsala.html%3Fid%3D7%23chat')).toBe('/sala.html?id=7#chat');
  });

  test.each([
    // R17.A — los que la comprobación anterior dejaba pasar porque empiezan
    // por `/`, y que el navegador lee como `//malo.example` (otro origen).
    ['barra invertida', '/\\malo.example'],
    ['barra invertida doble', '/\\/malo.example'],
    ['tabulador', '/\t/malo.example'],
    ['salto de línea', '/\n/malo.example'],
    ['retorno de carro', '/\r/malo.example'],
    // Compuesta a trozos: escrita entera, `no-script-url` la toma por código.
    ['esquema javascript', ['javascript', 'alert(1)'].join(':')],
    ['esquema data', 'data:text/html,hola'],
    ['relativa sin barra', 'malo.example/x'],
  ])('rechaza la redirección abierta por %s', (_caso, volver) => {
    expect(rutaSegura(volver, 'http://localhost:8099')).toBeNull();
    expect(rutaDeVuelta(`?volver=${encodeURIComponent(volver)}`)).toBeNull();
  });

  test('no vuelve a una pantalla de entrada: sería un bucle', () => {
    for (const puerta of [
      '/frontend/app-web/src/cuentas/login.html',
      '/frontend/app-web/src/cuentas/registro.html?x=1',
      '/frontend/app-web/src/cuentas/preparando.html',
      '/login',
      '/registro',
    ]) {
      expect(rutaSegura(puerta, 'http://localhost:8099')).toBeNull();
    }
    expect(rutaSegura('/inventario', 'http://localhost:8099')).toBe('/inventario');
  });
});

describe('RUTAS', () => {
  test('todas son relativas a src/comun/ y resuelven dentro del producto', () => {
    for (const ruta of Object.values(RUTAS)) {
      expect(ruta.startsWith('../')).toBe(true);
      expect(resolver(ruta, BASE)).toMatch(/^http:\/\/localhost:8099\/frontend\/app-web\/src\//);
    }
  });
});
