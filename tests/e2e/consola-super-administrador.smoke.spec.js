// @ts-check
/**
 * La consola del Super Administrador en AWS DEV — cierre del módulo (SA-9).
 *
 * El banco (`super-administrador.e2e.spec.js`) recorre la consola y además
 * crea una cuenta. Aquí solo se mira, en el entorno desplegado, lo que el
 * informe de revisión pidió ver en él:
 *
 *   - el Super Administrador entra con contraseña y código (7.3.9);
 *   - las quince secciones de la consola cargan por su dirección limpia, con
 *     su título, sin errores de página, sin textos de desarrollo y sin un solo
 *     enlace a un `.html` (punto crítico externo);
 *   - una dirección antigua con `.html` lleva a la limpia con su consulta.
 *
 * Entra con la cuenta administrativa de PRUEBAS de DEV (`ADMINS_QA`, por
 * `E2E_ADMINS_QA`). No cambia nada: solo abre páginas.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { cuentaAdministrativaDeRol, sesionDe } from './ayudantes/cuentas.js';
import { entrarEnLaPagina, recorrerLaConsola } from './ayudantes/consola.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';

test.describe('La consola del Super Administrador en AWS DEV (SA-9)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let superAdmin;

  test.beforeAll(async () => {
    const cuenta = cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR');
    test.skip(!cuenta, 'sin la cuenta de pruebas del Super Administrador (E2E_ADMINS_QA)');
    api = await apiRequest.newContext({ baseURL: AWS });
    superAdmin = await sesionDe(api, cuenta.apodo, { base: AWS });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('entra con código y recorre la consola por sus direcciones limpias', async ({ page }) => {
    test.setTimeout(300_000);
    expect(superAdmin.claims.rol).toBe('SUPER_ADMINISTRADOR');
    expect(superAdmin.claims.amr).toEqual(['pwd', 'otp']);

    await entrarEnLaPagina(page, superAdmin);
    const errores = await recorrerLaConsola(page, { base: AWS });
    expect(errores, 'ninguna sección rompe con un error de la página').toEqual([]);
  });

  test('una dirección antigua con .html lleva a la limpia con su consulta', async ({ page }) => {
    await entrarEnLaPagina(page, superAdmin);
    await page.goto(
      `${AWS}/frontend/app-web/src/cuentas/ficha-usuario.html?usuario=${superAdmin.claims.uid}`,
    );
    await expect(page).toHaveURL(
      new RegExp(`/consola/usuarios/ficha\\?usuario=${superAdmin.claims.uid}$`),
    );
    await expect(page.locator('h1').first()).toHaveText('Ficha del usuario');
  });
});
