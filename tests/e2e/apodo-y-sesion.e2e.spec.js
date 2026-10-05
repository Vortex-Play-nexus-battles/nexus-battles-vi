/**
 * RFINAL-03 — el apodo prohibido se explica y la sesión sobrevive a un
 * cambio de apodo, de punta a punta por el borde y contra los servicios
 * reales del banco (`tests/e2e/compose.yml`).
 *
 * Lo encontró la revisión de AWS DEV del 4-oct (informe del jugador):
 *
 *   1. Cambiar el apodo a «batman» desde «Mi cuenta» daba 400, pero la vista
 *      decía «Revisa los datos e inténtalo otra vez»: el 400 venía en texto
 *      plano y la vista lee JSON. Ahora quien pide problem details recibe
 *      `apodo-no-permitido` (ms-identidad-perfiles.yaml 1.3.0) y la vista
 *      dice «Ese apodo no está permitido. Elige otro.», sin nombrar el término
 *      ni su categoría.
 *   2. Tras cambiar de apodo, el token seguía diciendo el apodo viejo en
 *      `sub` y el interceptor de ms-identidad buscaba al titular por ese
 *      apodo: no encontraba a nadie y cada ruta de identidad respondía 403
 *      hasta volver a entrar. Peor: si otra cuenta tomaba después el apodo
 *      libre, el token viejo la encontraba a ella. Ahora el titular se busca
 *      por el `uid` del token.
 *
 * Las variantes de «spiderman» son las de la política ya implementada
 * (lista negra normalizada, B2); la semilla del banco trae «spiderman» y
 * «batman» (V7 de moderacion-sanciones).
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const MARCA = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e3)}`;

const MI_CUENTA = '/frontend/app-web/src/cuentas/perfil.html';
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const ACEPTA = 'application/problem+json, application/json';

function sesionDe(api, apodo, opciones = {}) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE, ...opciones });
}

/** El PUT multipart que manda «Mi cuenta». */
function cambiarApodo(api, jugador, apodo) {
  return api.put(`/api/v1/perfiles/${jugador.claims.uid}`, {
    headers: { Authorization: `Bearer ${jugador.token}`, Accept: ACEPTA },
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', preferencias: '', apodo },
  });
}

/** Deja la sesión en el navegador como la deja login.js. */
async function conSesion(page, jugador) {
  await page.addInitScript(
    ([token, apodo, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [jugador.token, jugador.apodo, jugador.claims.uid],
  );
}

test.describe('RFINAL-03 · apodo prohibido y sesión tras cambiar de apodo', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  const apodoInicial = `rf03a${MARCA}`;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · el 400 del apodo prohibido es un problem details discriminable, sin el término', async () => {
    test.setTimeout(120_000);
    jugadora = await sesionDe(api, apodoInicial);

    const detalles = new Set();
    for (const apodo of ['spiderman', 'Spiderman', 'spider-man', 'batman']) {
      const respuesta = await cambiarApodo(api, jugadora, apodo);
      const texto = await respuesta.text();
      expect(respuesta.status(), `${apodo}: ${texto}`).toBe(400);
      expect(respuesta.headers()['content-type'], apodo).toContain('application/problem+json');
      const problema = JSON.parse(texto);
      expect(problema.type, apodo).toBe(`${ERRORES}apodo-no-permitido`);
      expect(problema.campo, apodo).toBe('apodo');
      // Sin revelar internals: el detalle no repite el término que saltó.
      expect(problema.detail.toLowerCase(), apodo).not.toMatch(/spider|batman/);
      detalles.add(problema.detail);
    }
    // Y es la frase de la política, la misma para cualquier término: no dice
    // cuál saltó ni en qué categoría está (marca, celebridad...).
    expect([...detalles], 'el mismo detalle para los cuatro').toHaveLength(1);

    // Nada cambió: el apodo sigue siendo el de antes.
    const perfil = await api.get(`/api/v1/perfiles/${jugadora.claims.uid}`, {
      headers: { Authorization: `Bearer ${jugadora.token}` },
    });
    expect(perfil.status(), await perfil.text()).toBe(200);
    expect((await perfil.json()).apodo).toBe(apodoInicial);
  });

  test('2 · «Mi cuenta» dice «Ese apodo no está permitido. Elige otro.» y marca el campo', async ({
    page,
  }) => {
    test.setTimeout(90_000);
    await conSesion(page, jugadora);
    // «Mi cuenta» abre en «Resumen»: el formulario vive en la pestaña «Perfil»
    // (comun/ui/pestanas.js la elige por el hash).
    await page.goto(`${BORDE}${MI_CUENTA}#perfil`);

    const apodo = page.locator('#formulario-perfil [name="apodo"]');
    await expect(apodo).toBeVisible({ timeout: 20_000 });
    await expect(apodo).toHaveValue(apodoInicial, { timeout: 20_000 });
    await apodo.fill('Spiderman');
    await page.locator('[data-accion="guardar-perfil"]').click();
    await page.locator('[data-accion="confirmar"]').click();

    const aviso = page.locator('[data-zona="aviso"]');
    await expect(aviso).toContainText('Ese apodo no está permitido. Elige otro.', {
      timeout: 20_000,
    });
    await expect(aviso).not.toContainText('Revisa los datos');
    await expect(apodo).toHaveAttribute('aria-invalid', 'true');
  });

  test('3 · tras cambiar de apodo el mismo token sigue valiendo, y no sirve para otra cuenta', async () => {
    test.setTimeout(180_000);
    const apodoNuevo = `rf03b${MARCA}`;

    const cambio = await cambiarApodo(api, jugadora, apodoNuevo);
    expect(cambio.status(), await cambio.text()).toBe(200);

    // El token dice todavía el apodo viejo en `sub`: antes, 403 aquí.
    const conTokenViejo = { headers: { Authorization: `Bearer ${jugadora.token}` } };
    const propio = await api.get(`/api/v1/perfiles/${jugadora.claims.uid}`, conTokenViejo);
    expect(propio.status(), await propio.text()).toBe(200);
    expect((await propio.json()).apodo).toBe(apodoNuevo);

    // Otra cuenta toma el apodo que quedó libre.
    const otra = await sesionDe(api, apodoInicial, { email: `otra_${apodoInicial}@nexus.test` });
    expect(otra.claims.uid).not.toBe(jugadora.claims.uid);

    // El token viejo sigue siendo de la jugadora: su perfil, no el de la otra.
    const otraVez = await api.get(`/api/v1/perfiles/${jugadora.claims.uid}`, conTokenViejo);
    expect(otraVez.status(), await otraVez.text()).toBe(200);
    expect((await otraVez.json()).apodo).toBe(apodoNuevo);
    const ajeno = await api.get(`/api/v1/perfiles/${otra.claims.uid}`, conTokenViejo);
    expect(ajeno.status(), 'el perfil de la otra cuenta no se lee con este token').toBe(403);
  });

  test('4 · cambiar solo las mayúsculas del propio apodo se permite; el de otra cuenta no', async () => {
    const soloMayusculas = await cambiarApodo(api, jugadora, `RF03B${MARCA}`.toUpperCase());
    expect(soloMayusculas.status(), await soloMayusculas.text()).toBe(200);

    // El apodo de la otra cuenta, en otras mayúsculas: en uso (como el registro).
    const ajeno = await cambiarApodo(api, jugadora, apodoInicial.toUpperCase());
    const texto = await ajeno.text();
    expect(ajeno.status(), texto).toBe(400);
    expect(JSON.parse(texto).type).toBe(`${ERRORES}apodo-en-uso`);
  });
});
