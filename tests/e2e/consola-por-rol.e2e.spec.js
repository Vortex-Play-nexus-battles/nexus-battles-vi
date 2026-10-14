// @ts-check
/**
 * La consola de cada rol — 7.3.1 y la matriz de permisos del documento, de
 * punta a punta por el borde y con los servicios reales del banco.
 *
 * El recorrido del Super Administrador está en `super-administrador.e2e.spec.js`.
 * Aquí, los otros tres roles, con la misma vara:
 *
 *   1. el Moderador entra con contraseña y código y recorre lo suyo (resumen,
 *      comentarios, sanciones y lista negra) sin errores, sin textos de
 *      desarrollo y sin enlaces a un `.html`; la barra no le ofrece nada más,
 *      y lo demás le dice que no tiene acceso sin enseñarle la herramienta;
 *   2. el Administrador, igual, con la gestión de usuarios, productos,
 *      parámetros, métricas, tablero técnico y asistente; crear cuentas
 *      administrativas y la auditoría no se le abren;
 *   3. un jugador no ve ninguna sección de la consola.
 *
 * Lo mismo corre contra AWS DEV en `consola-por-rol.smoke.spec.js`.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';
import {
  comprobarSinAcceso,
  entrarEnLaPagina,
  recorrerLaConsola,
  seccionesDeLaBarra,
  seccionesDeRol,
  seccionesVetadasA,
} from './ayudantes/consola.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

test.describe('La consola de cada rol (7.3.1)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  for (const [numero, rol, quien] of [
    ['1', 'MODERADOR', 'el Moderador'],
    ['2', 'ADMINISTRADOR', 'el Administrador'],
  ]) {
    test(`${numero} · ${quien} recorre lo suyo y lo demás no se le abre`, async ({ page }) => {
      test.setTimeout(300_000);
      const sesion = await sesionDe(api, cuentaAdministrativaDeRol(rol).apodo);
      expect(sesion.claims.rol).toBe(rol);
      expect(sesion.claims.amr).toEqual(['pwd', 'otp']);
      await entrarEnLaPagina(page, sesion);

      const suyas = seccionesDeRol(rol);
      const errores = await recorrerLaConsola(page, { secciones: suyas });
      expect(errores, 'ninguna sección rompe con un error de la página').toEqual([]);

      // La barra no ofrece puertas que no se abren.
      await page.goto('/consola');
      const enLaBarra = await seccionesDeLaBarra(page);
      const abiertas = suyas.map(([ruta]) => ruta);
      expect(enLaBarra.length, 'la barra ofrece secciones').toBeGreaterThan(0);
      for (const ruta of enLaBarra) {
        expect(abiertas, `la barra de ${rol} no ofrece ${ruta}`).toContain(ruta);
      }

      await comprobarSinAcceso(page, seccionesVetadasA(rol));
    });
  }

  test('3 · un jugador no ve ninguna sección de la consola', async ({ page }) => {
    const jugador = await sesionDe(api, `jugador_consola_${SUFIJO}`);
    expect(jugador.claims.rol).toBe('JUGADOR');
    await entrarEnLaPagina(page, jugador);
    await comprobarSinAcceso(page, seccionesVetadasA('JUGADOR'));
  });
});
