// @ts-check
/**
 * Subastas por el borde, con el token real de ms-identidad — primer corte del
 * bloque de subastas (HU-SUB-011 listado, HU-SUB-001 publicar, HU-SUB-004 pujar).
 *
 * Lo que este corte demuestra es lo que en el host de dev daba 502 y, de
 * haber estado desplegado, habria dado 401: que `ms-subastas` esta detras del
 * mismo borde y acepta el mismo inicio de sesion que el resto de la
 * aplicacion. El listado es publico; publicar y pujar exigen el token; y un
 * token real ENTRA (la respuesta es la del negocio, no un 401 de firma).
 *
 * Lo que NO cubre todavia: publicar un producto real del inventario y pujar
 * hasta el cierre. Hace falta semilla de inventario para subastar
 * (HU-SUB-001 CA de inventario) y el adaptador de inventario en modo http en
 * el banco; es el siguiente corte de la misma HU.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/cuentas/subastas.html';

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  const cuerpo = await login.json();
  return { ...cuerpo, claims: cuerpoDelToken(cuerpo.token) };
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('Subastas por el borde (HU-SUB-011 / HU-SUB-001 / HU-SUB-004)', () => {
  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('el listado de subastas activas es publico y responde por el borde (antes: 502)', async () => {
    const r = await api.get('/api/v1/subastas?page=0&size=4');
    expect(r.status(), await r.text()).toBe(200);
    const pagina = await r.json();
    expect(Array.isArray(pagina.contenido)).toBe(true);
    expect(pagina.tamanoPagina).toBe(4);
  });

  test('publicar y pujar exigen sesion: sin token es 401 con problem details', async () => {
    const publicar = await api.post('/api/v1/subastas', {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'e2e-sin-token' },
      data: {
        elementoInventarioId: 'x',
        productoId: '00000000-0000-0000-0000-000000000001',
        duracion: '24H',
        precioInicial: 10,
      },
    });
    expect(publicar.status()).toBe(401);
    const pujar = await api.post('/api/v1/subastas/00000000-0000-0000-0000-000000000001/pujas', {
      headers: { 'Content-Type': 'application/json' },
      data: { monto: '10' },
    });
    expect(pujar.status()).toBe(401);
  });

  test('con el token real de ms-identidad la peticion entra: la respuesta es del negocio, no de la firma', async () => {
    // Antes del 21-sep ms-subastas validaba con una clave HS256 propia y este
    // mismo token (RS256, firmado por ms-identidad) daba 401. Ahora llega al
    // controlador: la subasta no existe, y eso es un 404 del negocio.
    const r = await api.get(
      '/api/v1/subastas/00000000-0000-0000-0000-000000000001/mi-participacion',
      {
        headers: conToken(anfitriona.token),
      },
    );
    expect(r.status(), await r.text()).not.toBe(401);
    expect([200, 404]).toContain(r.status());

    // Y las pujas del propio jugador: bandeja vacia, pero suya.
    const mias = await api.get('/api/v1/mis-pujas/resumen', {
      headers: conToken(anfitriona.token),
    });
    expect(mias.status(), await mias.text()).toBe(200);
  });

  test('la vista de subastas carga el listado con la cabecera de la aplicacion', async ({
    page,
  }) => {
    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [anfitriona.token, ANFITRION],
    );
    const listado = page.waitForResponse(
      (r) => r.url().includes('/api/v1/subastas') && r.request().method() === 'GET',
    );
    await page.goto(`${BORDE}${VISTA}`);
    expect((await listado).status()).toBe(200);
    await expect(page.locator('.cabecera [data-zona="apodo"]')).toHaveText(ANFITRION);
    await expect(page.locator('.cabecera [data-seccion="subasta"]')).toHaveAttribute(
      'aria-current',
      'page',
    );
  });
});
