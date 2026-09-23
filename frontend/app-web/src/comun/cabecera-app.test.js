/**
 * `cabecera-app.js` como capa de compatibilidad — UX-R3.0.
 *
 * Este archivo probaba la cabecera única de HU-UX-001. UX-R3.0 partió ese
 * módulo en tres (`sesion.js`, `acceso.js`, `shell.js`) y sus pruebas viven
 * ahora en `sesion.test.js`, `acceso.test.js` y `shell.test.js`.
 *
 * Lo que queda aquí es lo único que este archivo sigue prometiendo: que las
 * 31 vistas y los módulos de los otros dos grupos que importan de
 * `cabecera-app.js` siguen encontrando lo que importaban, y que
 * `montarCabecera` monta **el armazón que toca**, no el de siempre.
 */

import { jest } from '@jest/globals';

import {
  CLAVES,
  MATRIZ,
  RUTAS,
  ROLES,
  SECCIONES,
  cerrarSesion,
  exigirAcceso,
  exigirSesion,
  leerSesion,
  montarArmazon,
  montarCabecera,
  puedeVer,
  resolver,
  rutaDeVuelta,
} from './cabecera-app.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/cabecera-app.js';

function conSesion(rol = 'JUGADOR') {
  const cuerpo = btoa(
    JSON.stringify({ uid: 'u-1', sub: 'Ana', rol, exp: Math.floor(Date.now() / 1000) + 3600 }),
  )
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  sessionStorage.setItem(CLAVES.token, `c.${cuerpo}.f`);
  sessionStorage.setItem(CLAVES.apodo, 'Ana');
  sessionStorage.setItem(CLAVES.rol, rol);
}

function montar(opciones = {}) {
  const raiz = document.createElement('div');
  document.body.appendChild(raiz);
  return montarCabecera(raiz, { base: BASE, navegar: jest.fn(), ...opciones });
}

beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '';
});

describe('superficie que las vistas siguen importando', () => {
  test('todo lo que importaba alguna vista sigue exportado', () => {
    for (const exportado of [
      CLAVES,
      RUTAS,
      ROLES,
      MATRIZ,
      SECCIONES,
      resolver,
      leerSesion,
      cerrarSesion,
      rutaDeVuelta,
      exigirSesion,
      exigirAcceso,
      puedeVer,
      montarCabecera,
      montarArmazon,
    ]) {
      expect(exportado).toBeDefined();
    }
  });

  test('`resolver` sigue resolviendo contra la base que se le pase', () => {
    expect(resolver(RUTAS.login, BASE)).toBe(
      'http://localhost:8099/frontend/app-web/src/cuentas/login.html',
    );
  });
});

describe('montarCabecera elige armazón', () => {
  test('con la vista declarada, monta el de la matriz', () => {
    conSesion('ADMINISTRADOR');
    expect(montar({ vista: 'gestion-usuarios' }).elemento.dataset.armazon).toBe('admin');
  });

  test('sin vista declarada, la deduce de la URL', () => {
    // Es el respaldo: una vista que olvide declararse no vuelve en silencio
    // al comportamiento anterior.
    expect(
      montar({ ruta: '/frontend/app-web/src/cuentas/login.html' }).elemento.dataset.armazon,
    ).toBe('publico');
  });

  test('devuelve la sesión leída, como siempre', () => {
    conSesion();
    expect(montar({ vista: 'home' }).sesion.apodo).toBe('Ana');
  });
});
