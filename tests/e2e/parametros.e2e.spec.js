// @ts-check
/**
 * Parametros del sistema — HU-ADM-001 por el borde con admin-parametros
 * real y moderacion-sanciones consumiendolo (CA-04).
 *
 *   1. el catalogo es publico: trae editables e inalterables del Charter
 *   2. una jugadora no cambia nada (403); un inalterable no se edita (409);
 *      un valor fuera de rango se rechaza (400) y nada cambia
 *   3. el administrador cambia sanciones.suspension.maxima-dias a 2 con
 *      motivo: version 2, historial con anterior/nuevo
 *   4. CA-04: moderacion-sanciones lee el nuevo limite por API: una
 *      suspension de 72 h ahora se rechaza (400) y una de 24 h entra
 *   5. un cambio con vigencia futura queda programado y el valor vigente no
 *      cambia (CA-03)
 *   6. la vista (SA-8, punto 22): nombres de producto, el fijo dicho sin
 *      referencias internas, y un cambio hecho desde su panel con motivo que
 *      queda en el historial con el apodo de quien lo hizo
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const JUGADORA = process.env.E2E_PARAMETRIZADA ?? 'parametrizada_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/admin-parametros/parametros-admin.html';
const PARAMETRO = 'sanciones.suspension.maxima-dias';

/**
 * B1 — la cuenta nace pendiente de verificar su correo. Registrar, leer el
 * codigo del buzon, confirmarlo y entrar viven en un solo sitio
 * (`ayudantes/cuentas.js`); aqui solo se fija la contrasena de este spec.
 */
function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('Parametros del sistema (HU-ADM-001)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let moderadora;
  let jugadora;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    moderadora = await sesionDe(api, MODERADORA);
    jugadora = await sesionDe(api, JUGADORA);
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
  });

  test.afterAll(async () => {
    // Se deja el catalogo como estaba para los demas specs (30 dias).
    await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      headers: conToken(admin.token),
      data: { valor: '30', motivo: 'Restaurar tras el E2E' },
    });
    await api.dispose();
  });

  test('el catalogo es publico y trae editables e inalterables del Charter', async () => {
    const r = await api.get('/api/v1/parametros');
    expect(r.status(), await r.text()).toBe(200);
    const catalogo = await r.json();
    const porClave = Object.fromEntries(catalogo.map((p) => [p.clave, p]));
    expect(porClave[PARAMETRO].inalterable).toBe(false);
    expect(porClave[PARAMETRO].maximo).toBe(365);
    expect(porClave['torneos.dias-entre-torneos'].inalterable).toBe(true);
    expect(porClave['torneos.dias-entre-torneos'].valor).toBe('91');
    // D-25: la cifra de sanciones la fijo el PO (V6 la escribe en el catalogo);
    // la de reportes sigue sin decision y nace vacia.
    expect(porClave['metricas.umbral-sanciones-por-dia'].valor, 'decision del PO').toBe('3');
    expect(
      porClave['metricas.umbral-reportes-por-dia'].valor,
      'decision del PO sin tomar',
    ).toBeNull();
    const ligero = await api.get(`/api/v1/parametros/${PARAMETRO}/valor`);
    expect(ligero.status()).toBe(200);
    expect((await ligero.json()).tipo).toBe('ENTERO');
  });

  test('una jugadora no cambia nada; un inalterable no se edita; fuera de rango se rechaza', async () => {
    const jugadoraCambia = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      headers: conToken(jugadora.token),
      data: { valor: '1', motivo: 'quiero' },
    });
    expect(jugadoraCambia.status()).toBe(403);
    expect((await jugadoraCambia.json()).motivo).toBe('PERMISO_INSUFICIENTE');

    const sinToken = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      data: { valor: '1', motivo: 'x' },
    });
    expect(sinToken.status()).toBe(401);

    const inalterable = await api.put('/api/v1/parametros/torneos.cupos', {
      headers: conToken(admin.token),
      data: { valor: '16', motivo: 'mas equipos' },
    });
    expect(inalterable.status()).toBe(409);
    expect((await inalterable.json()).motivo).toBe('INALTERABLE');

    const fueraDeRango = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      headers: conToken(admin.token),
      data: { valor: '999', motivo: 'muy largo' },
    });
    expect(fueraDeRango.status()).toBe(400);
    expect((await fueraDeRango.json()).motivo).toBe('VALOR_INVALIDO');

    const sinMotivo = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      headers: conToken(admin.token),
      data: { valor: '10', motivo: '' },
    });
    expect(sinMotivo.status()).toBe(400);
    expect((await sinMotivo.json()).motivo).toBe('SOLICITUD_INVALIDA');

    const historial = await api.get(`/api/v1/parametros/${PARAMETRO}/historial`, {
      headers: conToken(jugadora.token),
    });
    expect(historial.status(), 'el historial es de administracion').toBe(403);
  });

  test('el administrador cambia el maximo de la suspension a 2 dias: versionado con motivo', async () => {
    const r = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
      headers: conToken(admin.token),
      data: { valor: '2', motivo: 'Acuerdo de la Sprint Review (E2E)' },
    });
    expect(r.status(), await r.text()).toBe(200);
    const cambiado = await r.json();
    expect(cambiado.valor).toBe('2');
    expect(cambiado.actualizadoPor).toBe(admin.claims.uid);
    expect(cambiado.version).toBeGreaterThanOrEqual(2);

    const historial = await api.get(`/api/v1/parametros/${PARAMETRO}/historial`, {
      headers: conToken(admin.token),
    });
    expect(historial.status()).toBe(200);
    const versiones = await historial.json();
    expect(versiones[0].valorNuevo).toBe('2');
    expect(versiones[0].motivo).toBe('Acuerdo de la Sprint Review (E2E)');
    expect(versiones[0].cambiadoPor).toBe(admin.claims.uid);
  });

  test('CA-04: moderacion-sanciones lee el limite nuevo por API y rechaza una suspension de 72 h', async () => {
    // La cache del consumidor es de 1 s en el banco.
    await new Promise((resolve) => setTimeout(resolve, 1500));
    const larga = await api.post('/api/v1/sanciones', {
      headers: conToken(moderadora.token),
      data: {
        usuarioId: jugadora.claims.uid,
        tipo: 'SUSPENSION',
        motivo: 'Prueba de limite',
        duracionHoras: 72,
      },
    });
    expect(larga.status(), await larga.text()).toBe(400);
    const problema = await larga.json();
    expect(problema.motivo).toBe('SOLICITUD_INVALIDA');
    expect(problema.detail).toMatch(/2 dias/);

    const corta = await api.post('/api/v1/sanciones', {
      headers: conToken(moderadora.token),
      data: {
        usuarioId: jugadora.claims.uid,
        tipo: 'SUSPENSION',
        motivo: 'Prueba de limite',
        duracionHoras: 24,
      },
    });
    expect(corta.status(), await corta.text()).toBe(201);
  });

  test('un cambio con vigencia futura queda programado y el valor vigente no cambia (CA-03)', async () => {
    const manana = new Date(Date.now() + 24 * 3600 * 1000).toISOString();
    const r = await api.put('/api/v1/parametros/chat.historial.tamano', {
      headers: conToken(admin.token),
      data: { valor: '100', motivo: 'Mas historial desde manana', vigenteDesde: manana },
    });
    expect(r.status(), await r.text()).toBe(200);
    const programado = await r.json();
    expect(programado.valor).toBe('50');
    expect(programado.valorProgramado).toBe('100');
    expect(Date.parse(programado.vigenteDesde)).toBeGreaterThan(Date.now());
    const ligero = await api.get('/api/v1/parametros/chat.historial.tamano/valor');
    expect((await ligero.json()).valor).toBe('50');
  });

  test('la vista: nombres de producto, el fijo sin referencias internas y el cambio desde su panel (SA-8)', async ({
    page,
  }) => {
    // Punto de partida fijo: 2 días. También si Playwright reintenta la prueba
    // después de que el primer intento ya hizo el cambio.
    const vigente = await api.get(`/api/v1/parametros/${PARAMETRO}/valor`);
    if ((await vigente.json()).valor !== '2') {
      const reponer = await api.put(`/api/v1/parametros/${PARAMETRO}`, {
        headers: conToken(admin.token),
        data: { valor: '2', motivo: 'Punto de partida de la vista (E2E)' },
      });
      expect(reponer.status(), await reponer.text()).toBe(200);
    }

    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [admin.token, ADMIN, admin.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);
    const fila = (clave) => page.locator(`[data-fila="${clave}"]`);
    await expect(fila(PARAMETRO)).toContainText('Suspensión máxima', { timeout: 20000 });
    await expect(fila(PARAMETRO).locator('[data-campo="valor"]')).toContainText('2 días');
    // Punto 22 del informe: el fijo dice «Fijo», sin Charter ni RNF, y no se edita.
    await expect(fila('torneos.cupos').locator('[data-campo="fijo"]')).toHaveText('Fijo');
    await expect(fila('torneos.cupos').locator('[data-accion="cambiar"]')).toHaveCount(0);
    await expect(fila('chat.historial.tamano').locator('[data-campo="programado"]')).toContainText(
      'Pasará a 100 mensajes',
    );
    await expect(page.locator('main')).not.toContainText(
      /Charter|RNF-|RF-[A-Z]|HU-[A-Z]|pendiente del PO|origen:/,
    );

    // Cambiar desde su panel: lo que admite, la frase de lo que pasará y el motivo.
    await fila(PARAMETRO).locator('[data-accion="cambiar"]').click();
    const panel = page.locator('[role="dialog"]');
    await expect(panel).toContainText('Entre 1 y 365 días');
    await panel.locator('[name="valor"]').fill('3');
    await expect(panel.locator('[data-zona="resumen-del-cambio"]')).toHaveText(
      'Pasará de 2 días a 3 días, de inmediato.',
    );
    await panel.locator('[name="motivo"]').fill('Ajuste desde la consola (E2E)');
    await panel.locator('[data-accion="guardar-cambio"]').click();
    await expect(page.locator('[data-zona="aviso"]')).toContainText(
      'Suspensión máxima: ahora 3 días.',
      { timeout: 20000 },
    );
    await expect(fila(PARAMETRO).locator('[data-campo="valor"]')).toContainText('3 días');

    // Y queda en el historial, con el apodo de quien lo hizo.
    await fila(PARAMETRO).locator('[data-accion="historial"]').click();
    const historial = page.locator('[role="dialog"] [data-zona="historial"]');
    await expect(historial).toContainText('2 días → 3 días', { timeout: 20000 });
    await expect(historial).toContainText('Ajuste desde la consola (E2E)');
    await expect(historial).toContainText(`Por ${ADMIN}`);
  });
});
