/**
 * RFINAL-05 — «Mi cuenta › Privacidad» de punta a punta (§7.3.6: HU-PRV-004
 * #544, HU-PRV-005 #545, HU-PRV-006 #546), por el borde y contra los
 * servicios reales del banco (`tests/e2e/compose.yml`).
 *
 * Una cuenta nueva del banco:
 *
 *   1. por la API: sin nada programado, el cierre responde `SIN_SOLICITUD`
 *      con su plazo de 30 días; una contraseña equivocada no programa nada
 *      (422 `contrasena-actual-incorrecta`) y la ruta de otra cuenta es 403;
 *   2. ve su portal «Tus datos», con su correo en el bloque de la cuenta, y
 *      descarga el JSON: generado en el navegador, con su fecha de generación
 *      y la lista de módulos que no se pudieron reunir;
 *   3. saca el reporte para imprimir: al imprimir solo queda esa hoja;
 *   4. pide el cierre con su contraseña, lo ve «Cierre programado para el …»
 *      (y la API dice lo mismo, 30 días después de pedirlo) y lo cancela.
 *
 * El cierre pregunta a ms-subastas si la cuenta tiene subastas activas o
 * pujas vigentes (`IDENTIDAD_SUBASTAS_URL` del banco): una cuenta recién
 * creada no tiene ninguna, así que se programa. Si ms-subastas no contesta,
 * el servicio no programa nada (503) y esta prueba lo dirá.
 *
 * Solo un intento con la contraseña equivocada: cuenta para el bloqueo por
 * intentos fallidos (RF-AUT-009), igual que en el login.
 */

import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const MARCA = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e3)}`;

const MI_CUENTA = '/frontend/app-web/src/cuentas/perfil.html';
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const ACEPTA = 'application/problem+json, application/json';
const DIA_MS = 24 * 60 * 60 * 1000;

function sesionDe(api, apodo, opciones = {}) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE, ...opciones });
}

const rutaDelCierre = (uid) => `/api/v1/perfiles/${uid}/cierre`;

const conToken = (jugador) => ({
  Authorization: `Bearer ${jugador.token}`,
  Accept: ACEPTA,
});

/** El estado del cierre según la API, para afirmar sobre lo que pinta la vista. */
async function cierreSegunLaApi(api, jugador) {
  const respuesta = await api.get(rutaDelCierre(jugador.claims.uid), {
    headers: conToken(jugador),
  });
  expect(respuesta.status(), await respuesta.text()).toBe(200);
  return respuesta.json();
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

/** «Mi cuenta» abierta en «Privacidad», con el portal ya reunido. */
async function abrirPrivacidad(page, jugador) {
  await conSesion(page, jugador);
  await page.goto(`${BORDE}${MI_CUENTA}#privacidad`);
  await expect(page.locator('#pestana-privacidad')).toHaveAttribute('aria-selected', 'true', {
    timeout: 20_000,
  });
  // El portal pregunta a cada módulo; el botón de descarga se enciende al
  // terminar, haya ido bien o no cada consulta.
  await expect(page.locator('[data-accion="descargar-datos"]')).toBeEnabled({ timeout: 60_000 });
}

test.describe('RFINAL-05 · «Privacidad»: tus datos y el cierre de la cuenta', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  const apodo = `rf05p${MARCA}`;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
  });

  test.afterAll(async () => {
    // Que ninguna corrida deje una cuenta del banco camino de borrarse:
    // cancelar es idempotente (200 aunque no haya nada programado).
    if (jugadora) {
      await api
        .delete(rutaDelCierre(jugadora.claims.uid), { headers: conToken(jugadora) })
        .catch(() => null);
    }
    await api?.dispose();
  });

  test('1 · la API del cierre: sin solicitud, la contraseña equivocada no programa nada', async () => {
    test.setTimeout(150_000);
    jugadora = await sesionDe(api, apodo);

    const inicial = await cierreSegunLaApi(api, jugadora);
    expect(inicial.estado).toBe('SIN_SOLICITUD');
    expect(inicial.plazoDias).toBe(30);

    // El perfil propio ya trae el correo de la cuenta (perfiles 1.4.0): el
    // portal lo necesita para decir qué se guarda.
    const perfil = await api.get(`/api/v1/perfiles/${jugadora.claims.uid}`, {
      headers: conToken(jugadora),
    });
    expect(perfil.status(), await perfil.text()).toBe(200);
    expect((await perfil.json()).email).toBe(jugadora.email);

    const equivocada = await api.post(rutaDelCierre(jugadora.claims.uid), {
      headers: conToken(jugadora),
      data: { passwordActual: `${CLAVE}-no` },
    });
    const texto = await equivocada.text();
    expect(equivocada.status(), texto).toBe(422);
    expect(equivocada.headers()['content-type']).toContain('application/problem+json');
    const problema = JSON.parse(texto);
    expect(problema.type).toBe(`${ERRORES}contrasena-actual-incorrecta`);
    expect((await cierreSegunLaApi(api, jugadora)).estado).toBe('SIN_SOLICITUD');

    // La ruta de otra cuenta, con este token: 403 antes de buscar nada.
    const ajena = await api.get(rutaDelCierre(randomUUID()), {
      headers: conToken(jugadora),
    });
    expect(ajena.status(), await ajena.text()).toBe(403);
  });

  test('2 · ve su portal «Tus datos» y descarga el JSON generado en el navegador', async ({
    page,
  }) => {
    test.setTimeout(120_000);
    await abrirPrivacidad(page, jugadora);

    const cuenta = page.locator('[data-zona="bloques-portal"] [data-fuente="perfil"]');
    await expect(cuenta).toHaveAttribute('data-estado', 'completo');
    await expect(cuenta).toContainText(jugadora.email);
    await expect(cuenta).toContainText('Para qué se usa:');
    await expect(page.locator('[data-zona="resumen-portal"]')).toContainText(
      'Reunimos tus datos de',
    );

    const [descarga] = await Promise.all([
      page.waitForEvent('download'),
      page.locator('[data-accion="descargar-datos"]').click(),
    ]);
    // Sin datos de la persona en el nombre: solo la fecha.
    expect(descarga.suggestedFilename()).toMatch(
      /^mis-datos-nexus-battles-\d{4}-\d{2}-\d{2}\.json$/,
    );
    // Ni el apodo ni el correo viajan en la dirección: el JSON es un blob local.
    expect(descarga.url()).toMatch(/^blob:/);
    const exportado = JSON.parse(await readFile(await descarga.path(), 'utf8'));

    expect(exportado.formato).toBe('nexus-battles.mis-datos');
    expect(Number.isNaN(Date.parse(exportado.generadoEn))).toBe(false);
    expect(Math.abs(Date.now() - Date.parse(exportado.generadoEn))).toBeLessThan(10 * 60 * 1000);
    expect(Array.isArray(exportado.modulosIncompletos)).toBe(true);
    expect(exportado.completo).toBe(exportado.modulosIncompletos.length === 0);
    expect(exportado.modulos.perfil.estado).toBe('completo');
    expect(exportado.modulos.perfil.datos.email).toBe(jugadora.email);
    expect(exportado.modulos.perfil.datos.apodo).toBe(jugadora.apodo);
    // Cada módulo que no está completo aparece en la lista, con su motivo.
    const incompletos = new Set(exportado.modulosIncompletos.map((m) => m.modulo));
    for (const [id, modulo] of Object.entries(exportado.modulos)) {
      expect(incompletos.has(id), `${id}: ${modulo.estado}`).toBe(modulo.estado !== 'completo');
    }

    // Y nada de la persona quedó guardado en el navegador.
    const guardado = await page.evaluate(() => JSON.stringify({ ...localStorage }));
    expect(guardado).not.toContain(jugadora.email);
  });

  test('3 · el reporte para imprimir deja sola su hoja al imprimir', async ({ page }) => {
    test.setTimeout(120_000);
    await abrirPrivacidad(page, jugadora);

    // El diálogo de impresión no existe sin pantalla: se sustituye por uno que
    // apunta qué había en la hoja en el momento de imprimir.
    await page.evaluate(() => {
      window.print = () => {
        window.__impresion = {
          clase: document.body.classList.contains('imprimiendo-privacidad'),
          texto: document.querySelector('[data-zona="reporte-privacidad"]').innerText,
        };
      };
    });
    await page.locator('[data-accion="imprimir-datos"]').click();

    const impresion = await page.evaluate(() => window.__impresion);
    expect(impresion.clase).toBe(true);
    expect(impresion.texto).toContain('Tus datos en The Nexus Battles VI');
    expect(impresion.texto).toContain(jugadora.apodo);
    expect(impresion.texto).toContain('Generado el');

    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('[data-zona="reporte-privacidad"]')).toBeVisible();
    await expect(page.locator('main')).toBeHidden();

    await page.emulateMedia({ media: 'screen' });
    await page.evaluate(() => window.dispatchEvent(new Event('afterprint')));
    await expect(page.locator('[data-zona="reporte-privacidad"]')).toBeHidden();
    await expect(page.locator('main')).toBeVisible();
  });

  test('4 · pide el cierre con su contraseña, lo ve programado y lo cancela', async ({ page }) => {
    test.setTimeout(120_000);
    await abrirPrivacidad(page, jugadora);

    const cierre = page.locator('[data-zona="cierre-cuenta"]');
    const estado = cierre.locator('[data-zona="estado-cierre"]');
    await expect(estado).toContainText('Tu cuenta está activa', { timeout: 20_000 });
    // Antes de pedirlo, la vista explica qué se borra, qué se conserva y cuándo.
    await expect(cierre.locator('[data-zona="cierre-plazo"]')).toContainText('30 días');

    await cierre.locator('[data-accion="solicitar-cierre"]').click();
    const clave = cierre.locator('[name="passwordActualCierre"]');
    await expect(clave).toBeVisible();
    // Como la escribe una persona, sin `fill`: su valor iría al informe.
    await clave.evaluate((campo, valor) => {
      campo.value = valor;
      campo.dispatchEvent(new Event('input', { bubbles: true }));
    }, CLAVE);
    await cierre.locator('[data-accion="confirmar-cierre"]').click();
    await page.locator('[data-accion="confirmar"]').click();

    await expect(estado).toContainText('Cierre programado para el', { timeout: 20_000 });
    await expect(cierre.locator('[data-zona="aviso-cierre"]')).toContainText(
      'Programaste el cierre de tu cuenta',
    );
    await expect(cierre.locator('[data-accion="cancelar-cierre"]')).toBeVisible();
    // La contraseña no se queda escrita ni viaja en la dirección.
    expect(page.url()).not.toContain(CLAVE);

    const programado = await cierreSegunLaApi(api, jugadora);
    expect(programado.estado).toBe('PROGRAMADO');
    // Treinta días de calendario en la zona del servicio: una hora de margen
    // por si el plazo cruzara un cambio de horario.
    const plazo = Date.parse(programado.programadoPara) - Date.parse(programado.solicitadoEn);
    expect(Math.abs(plazo - 30 * DIA_MS)).toBeLessThanOrEqual(60 * 60 * 1000);

    await cierre.locator('[data-accion="cancelar-cierre"]').click();
    await page.locator('[data-accion="confirmar"]').click();

    await expect(cierre.locator('[data-zona="aviso-cierre"]')).toContainText(
      'Cancelaste el cierre de tu cuenta',
      { timeout: 20_000 },
    );
    await expect(estado).toContainText('Tu cuenta está activa');
    await expect(cierre.locator('[data-accion="solicitar-cierre"]')).toBeVisible();
    expect((await cierreSegunLaApi(api, jugadora)).estado).toBe('SIN_SOLICITUD');

    // La cuenta sigue funcionando: el token de siempre lee su perfil.
    const perfil = await api.get(`/api/v1/perfiles/${jugadora.claims.uid}`, {
      headers: conToken(jugadora),
    });
    expect(perfil.status(), await perfil.text()).toBe(200);
  });
});
