// @ts-check
/**
 * La consola de cada rol en AWS DEV — 7.3.1 y la matriz de permisos.
 *
 * Lo mismo que `consola-por-rol.e2e.spec.js` comprueba en el banco, en el
 * entorno desplegado y con sus cuentas: el Moderador y el Administrador
 * entran con contraseña y código, recorren lo suyo sin errores, sin textos
 * de desarrollo y sin enlaces a un `.html`, su barra no les ofrece nada más y
 * lo demás les dice que no tienen acceso; un jugador no ve ninguna sección de
 * la consola. El Super Administrador ya lo recorre
 * `consola-super-administrador.smoke.spec.js`, y la API lo niega en
 * `seguridad-administrativa.smoke.spec.js`.
 *
 * Entra con las cuentas administrativas de PRUEBAS de DEV (`ADMINS_QA`, por
 * `E2E_ADMINS_QA`) y con un jugador de pruebas propio (dominio reservado
 * `nexus.test`, el mismo en cada corrida). No cambia nada: solo abre páginas.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { cuentaAdministrativaDeRol, sesionDe } from './ayudantes/cuentas.js';
import {
  comprobarSinAcceso,
  entrarEnLaPagina,
  recorrerLaConsola,
  seccionesDeLaBarra,
  seccionesDeRol,
  seccionesVetadasA,
} from './ayudantes/consola.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-Smoke-2026';
const JUGADOR = 'smoke_consola_jugador';

test.describe('La consola de cada rol en AWS DEV (7.3.1)', () => {
  test.describe.configure({ mode: 'serial' });
  test.skip(
    !process.env.E2E_ADMINS_QA,
    'E2E_ADMINS_QA no está definida: este entorno no tiene cuentas administrativas de pruebas',
  );

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  for (const [rol, quien] of [
    ['MODERADOR', 'el Moderador'],
    ['ADMINISTRADOR', 'el Administrador'],
  ]) {
    test(`${quien} recorre lo suyo y lo demás no se le abre`, async ({ page }) => {
      test.setTimeout(300_000);
      const sesion = await sesionDe(api, cuentaAdministrativaDeRol(rol).apodo, { base: AWS });
      expect(sesion.claims.rol).toBe(rol);
      expect(sesion.claims.amr).toEqual(['pwd', 'otp']);
      await entrarEnLaPagina(page, sesion);

      const suyas = seccionesDeRol(rol);
      const errores = await recorrerLaConsola(page, { base: AWS, secciones: suyas });
      expect(errores, 'ninguna sección rompe con un error de la página').toEqual([]);

      await page.goto(`${AWS}/consola`);
      const enLaBarra = await seccionesDeLaBarra(page);
      const abiertas = suyas.map(([ruta]) => ruta);
      expect(enLaBarra.length, 'la barra ofrece secciones').toBeGreaterThan(0);
      for (const ruta of enLaBarra) {
        expect(abiertas, `la barra de ${rol} no ofrece ${ruta}`).toContain(ruta);
      }

      await comprobarSinAcceso(page, seccionesVetadasA(rol), { base: AWS });
    });
  }

  test('un jugador no ve ninguna sección de la consola', async ({ page }) => {
    test.setTimeout(240_000);
    const jugador = await sesionDe(api, JUGADOR, { clave: CLAVE, base: AWS });
    expect(jugador.claims.rol).toBe('JUGADOR');
    await entrarEnLaPagina(page, jugador);
    await comprobarSinAcceso(page, seccionesVetadasA('JUGADOR'), { base: AWS });
  });
});
