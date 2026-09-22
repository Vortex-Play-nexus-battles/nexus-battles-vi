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
  RUTAS,
  cerrarSesion,
  leerSesion,
  olvidarSesion,
  resolver,
  rutaDeVuelta,
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
    cerrarSesion({ almacen: sessionStorage, navegar, base: BASE });
    expect(sessionStorage.getItem(CLAVES.token)).toBeNull();
    expect(navegar).toHaveBeenCalledWith(
      'http://localhost:8099/frontend/app-web/src/cuentas/login.html',
    );
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
});

describe('RUTAS', () => {
  test('todas son relativas a src/comun/ y resuelven dentro del producto', () => {
    for (const ruta of Object.values(RUTAS)) {
      expect(ruta.startsWith('../')).toBe(true);
      expect(resolver(ruta, BASE)).toMatch(/^http:\/\/localhost:8099\/frontend\/app-web\/src\//);
    }
  });
});
