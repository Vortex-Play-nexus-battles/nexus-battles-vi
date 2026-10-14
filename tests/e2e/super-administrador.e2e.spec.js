// @ts-check
/**
 * Recorrido del Super Administrador — cierre del módulo (SA-9), de punta a
 * punta por el borde y con los servicios reales del banco.
 *
 * Responde con pruebas la pregunta del informe de revisión: ¿puede un Super
 * Administrador entrar y gestionar usuarios, productos, moderación, sanciones,
 * seguridad, parámetros, métricas y auditoría desde una interfaz profesional,
 * sin textos de desarrollo?
 *
 *   1. entra con contraseña y código (7.3.9) y la consola abre en su resumen;
 *   2. recorre las quince secciones de la consola por su dirección limpia:
 *      cada una carga con su título, sin errores en la página, sin textos de
 *      desarrollo y sin un solo enlace a un `.html` (punto crítico externo del
 *      informe: «salen los .html»);
 *   3. una dirección antigua con `.html` lleva a la limpia con su consulta;
 *   4. crea una cuenta de moderación desde la consola (7.3.4): confirma, la
 *      cuenta nace pendiente de activar con su rol y se encuentra en el
 *      directorio;
 *   5. lo exclusivo del Super Administrador no se le abre a un Administrador,
 *      ni en la vista ni en la API (RF-RBAC-003, RF-AUD-001).
 *
 * Las cuentas administrativas son las de pruebas del banco (con su secreto
 * TOTP ya enrolado); la cuenta que se crea lleva el sufijo de la corrida.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';
import { entrarEnLaPagina, recorrerLaConsola } from './ayudantes/consola.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

test.describe('Recorrido del Super Administrador (SA-9)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let superAdmin;
  let administrador;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo);
    administrador = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('1 · entra con contraseña y código y la consola abre en su resumen', async ({ page }) => {
    // 7.3.9: ninguna sesión administrativa se abre con la contraseña sola.
    expect(superAdmin.claims.rol).toBe('SUPER_ADMINISTRADOR');
    expect(superAdmin.claims.amr).toEqual(['pwd', 'otp']);

    await entrarEnLaPagina(page, superAdmin);
    await page.goto('/consola');
    await expect(page.locator('h1').first()).toHaveText('Control del Nexo', { timeout: 20_000 });
    // El resumen: las cifras del sistema y los últimos movimientos.
    await expect(page.locator('[data-indicador]').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('main')).toContainText('Últimos movimientos');
  });

  test('2 · recorre la consola por sus direcciones limpias, sin textos de desarrollo ni .html', async ({
    page,
  }) => {
    test.setTimeout(300_000);
    await entrarEnLaPagina(page, superAdmin);
    const errores = await recorrerLaConsola(page);
    expect(errores, 'ninguna sección rompe con un error de la página').toEqual([]);
  });

  test('3 · una dirección antigua con .html lleva a la limpia con su consulta', async ({
    page,
  }) => {
    await entrarEnLaPagina(page, superAdmin);
    await page.goto(
      `/frontend/app-web/src/cuentas/ficha-usuario.html?usuario=${superAdmin.claims.uid}`,
    );
    await expect(page).toHaveURL(
      new RegExp(`/consola/usuarios/ficha\\?usuario=${superAdmin.claims.uid}$`),
    );
    await expect(page.locator('h1').first()).toHaveText('Ficha del usuario', { timeout: 20_000 });
  });

  test('4 · crea una cuenta de moderación desde la consola y queda en el directorio', async ({
    page,
  }) => {
    const apodo = `mod_sa9_${SUFIJO}`;
    const email = `${apodo}@nexus.test`;

    await entrarEnLaPagina(page, superAdmin);
    await page.goto('/consola/usuarios/nueva-cuenta');
    await expect(page.locator('#formulario-contenedor')).toBeVisible({ timeout: 20_000 });
    await page.locator('#nombres').fill('Moderadora');
    await page.locator('#apellidos').fill('Del Recorrido');
    await page.locator('#apodo').fill(apodo);
    await page.locator('#email').fill(email);
    await page.locator('#rol').selectOption('MODERADOR');
    await page.locator('#btn-crear').click();

    // La confirmación dice qué va a pasar, sin un «¿Está seguro?» genérico.
    const dialogo = page.locator('[role="dialog"]');
    await expect(dialogo).toContainText(`Se creará la cuenta «${apodo}»`);
    await expect(dialogo).toContainText(email);
    const alta = page.waitForResponse(
      (r) => r.request().method() === 'POST' && r.url().includes('/api/v1/admin/cuentas'),
    );
    await dialogo.getByRole('button', { name: 'Crear cuenta' }).click();
    expect((await alta).status()).toBe(201);
    await expect(page.locator('#resultado-creacion')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('#resultado-texto')).toContainText(apodo);

    // Nace pendiente de activar (fija su contraseña con el código del correo)
    // y con el rol que se eligió.
    const directorio = await api.get(
      `/api/v1/admin/jugadores?buscar=${encodeURIComponent(apodo)}&size=5`,
      { headers: { Authorization: `Bearer ${superAdmin.token}` } },
    );
    expect(directorio.status(), await directorio.text()).toBe(200);
    const fila = (await directorio.json()).contenido.find((c) => c.apodo === apodo);
    expect(fila, 'la cuenta nueva está en el directorio').toBeTruthy();
    expect(fila.rol).toBe('MODERADOR');
    expect(fila.estado).toBe('INACTIVO');
  });

  test('5 · lo exclusivo del Super Administrador no se le abre a un Administrador', async ({
    page,
  }) => {
    expect(administrador.claims.rol).toBe('ADMINISTRADOR');
    await entrarEnLaPagina(page, administrador);
    for (const ruta of ['/consola/usuarios/nueva-cuenta', '/consola/auditoria']) {
      await page.goto(ruta);
      await expect(page.locator('main'), ruta).toContainText('No tienes acceso a esta sección', {
        timeout: 20_000,
      });
    }

    // Y la API dice lo mismo, se llame desde donde se llame.
    const alta = await api.post('/api/v1/admin/cuentas', {
      headers: { Authorization: `Bearer ${administrador.token}` },
      multipart: {
        nombres: 'No',
        apellidos: 'Permitida',
        email: `admin_no_${SUFIJO}@nexus.test`,
        apodo: `admin_no_${SUFIJO}`,
        rolNombre: 'MODERADOR',
      },
    });
    expect(alta.status()).toBe(403);
    const bitacora = await api.get('/api/v1/admin/auditoria', {
      headers: { Authorization: `Bearer ${administrador.token}` },
    });
    expect(bitacora.status()).toBe(403);
  });
});
