// @ts-check
/**
 * Ficha administrativa del usuario — HU-USR-010 (RF-USR-010), por el borde y
 * con los servicios reales del banco (ms-identidad, moderacion-sanciones y
 * comentarios).
 *
 *   1. la ruta (ms-identidad-admin.yaml 1.4.0): el administrador lee la ficha
 *      de una jugadora nueva por su uid y por su clave, sin credenciales y sin
 *      cache; la jugadora no puede (403); una cuenta que no existe es un
 *      problem details 404 `cuenta-no-encontrada` (CA-03).
 *   2. la vista: datos de la cuenta, sanciones y comentarios de sus servicios,
 *      lo que no tiene fuente dicho como tal, y el aviso de auditoría. El banco
 *      no tiene ms-cumplimiento, así que aquí la consulta NO queda auditada y
 *      la vista tiene que decirlo (nunca en silencio); en un entorno con la
 *      bitácora, lo contrario.
 *   3. el directorio de Control integral abre la ficha de cada cuenta.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const JUGADORA = `ficha_${Date.now().toString(36)}`;
const VISTAS = '/frontend/app-web/src';
const FICHA = `${VISTAS}/plataforma/moderacion-sanciones/ficha-usuario.html`;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

async function conSesion(page, sesion) {
  await page.addInitScript(
    ([token, nombre, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [sesion.token, sesion.apodo, sesion.claims.uid],
  );
}

test.describe('Ficha administrativa del usuario (HU-USR-010)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugadora;
  let ficha;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
    jugadora = await sesionDe(api, JUGADORA);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('la ruta: el administrador la lee por uid y por clave; la jugadora no; lo inexistente es 404', async () => {
    const porUid = await api.get(`/api/v1/admin/usuarios/${jugadora.claims.uid}/ficha`, {
      headers: { Authorization: `Bearer ${admin.token}` },
    });
    expect(porUid.status(), await porUid.text()).toBe(200);
    expect(porUid.headers()['cache-control']).toContain('no-store');
    ficha = await porUid.json();
    expect(ficha.apodo).toBe(JUGADORA);
    expect(ficha.uid).toBe(jugadora.claims.uid);
    expect(ficha.email).toBe(`${JUGADORA}@nexus.test`);
    expect(ficha.rol).toBe('JUGADOR');
    expect(typeof ficha.accesoAuditado).toBe('boolean');
    expect(JSON.stringify(ficha)).not.toMatch(/password|versionToken|\$2a\$/i);

    const porClave = await api.get(`/api/v1/admin/usuarios/${ficha.id}/ficha`, {
      headers: { Authorization: `Bearer ${admin.token}` },
    });
    expect(porClave.status()).toBe(200);
    expect((await porClave.json()).uid).toBe(jugadora.claims.uid);

    const propia = await api.get(`/api/v1/admin/usuarios/${ficha.id}/ficha`, {
      headers: { Authorization: `Bearer ${jugadora.token}` },
    });
    expect(propia.status()).toBe(403);

    const nadie = await api.get(
      '/api/v1/admin/usuarios/00000000-0000-0000-0000-000000000000/ficha',
      {
        headers: { Authorization: `Bearer ${admin.token}`, Accept: 'application/problem+json' },
      },
    );
    expect(nadie.status()).toBe(404);
    expect((await nadie.json()).type).toBe(
      'https://nexusbattles.upb.edu.co/errors/cuenta-no-encontrada',
    );
  });

  test('la vista consolida la cuenta y dice qué no tiene fuente y si la consulta quedó auditada', async ({
    page,
  }) => {
    await conSesion(page, admin);
    await page.goto(`${BORDE}${FICHA}?usuario=${ficha.id}`);

    await expect(page.locator('h1')).toHaveText(`Ficha de ${JUGADORA}`, { timeout: 20_000 });
    const cuenta = page.locator('[data-panel="cuenta"]');
    await expect(cuenta).toContainText(`${JUGADORA}@nexus.test`);
    await expect(cuenta).toContainText('Jugador');
    // Sanciones y comentarios responden en el banco: una cuenta nueva no
    // tiene ni lo uno ni lo otro, y se dice.
    await expect(page.locator('[data-panel="sanciones"]')).toContainText(
      'No tiene sanciones ni advertencias.',
    );
    await expect(page.locator('[data-panel="comentarios"]')).toContainText(
      'No ha publicado comentarios.',
    );
    for (const id of [
      'reportes',
      'transacciones',
      'subastas',
      'misiones',
      'estadisticas',
      'auditoria',
    ]) {
      await expect(page.locator(`[data-panel="${id}"]`)).toContainText('Sin fuente publicada');
    }
    await expect(page.locator('[data-panel="linea-de-tiempo"] .linea-tiempo')).toContainText(
      'Se registró',
    );
    const aviso = page.locator('[data-zona="aviso"]');
    await expect(aviso).toHaveAttribute('data-auditado', /^(si|no)$/);
    await expect(aviso).toContainText(/quedó registrada en la auditoría/);
  });

  test('el directorio de Control integral abre la ficha de la cuenta', async ({ page }) => {
    await conSesion(page, admin);
    await page.goto(`${BORDE}${VISTAS}/plataforma/consola/control-integral.html`);
    const directorio = page.locator('[data-panel="directorio"]');
    // Las cuentas del banco son @nexus.test, un dominio de pruebas: el
    // directorio las oculta por omisión (RFINAL-06). Se muestran para buscarla.
    await directorio.locator('input[name="ocultarPruebas"]').uncheck();
    await directorio.locator('input[name="buscar"]').fill(JUGADORA);
    await directorio.locator('button[type="submit"]').click();

    const enlace = directorio.locator('tbody a[data-accion="ver-ficha"]').first();
    await expect(enlace).toHaveAttribute(
      'href',
      new RegExp(`ficha-usuario\\.html\\?usuario=${ficha.id}$`),
      {
        timeout: 20_000,
      },
    );
    await enlace.click();
    await expect(page.locator('h1')).toHaveText(`Ficha de ${JUGADORA}`, { timeout: 20_000 });
  });
});
