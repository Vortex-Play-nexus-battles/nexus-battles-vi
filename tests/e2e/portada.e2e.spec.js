// @ts-check
/**
 * F6 (auditoría del 4-oct, cambio autorizado n.º 3) — la portada pública con
 * la tienda, de punta a punta por el borde real del banco.
 *
 * Lo que se afirma:
 *
 *   1. `/` es la portada (200, sin redirección); la ruta de su fichero lleva a
 *      `/`; entrar sigue en `/login` y crear la cuenta en `/registro`;
 *   2. sin cuenta se ven productos reales del catálogo y su detalle; comprar,
 *      guardar, calificar y comentar piden entrar: «Entra para comprar» lleva
 *      a `/login` con la vuelta a la tienda, y la API lo exige (401);
 *   3. la vitrina pública no expone campos de administración ni internos;
 *   4. con sesión, la portada ofrece «Ir a mi inicio» (`/inicio`);
 *   5. axe sin barreras graves en la portada.
 */

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';
const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);

/**
 * Los campos que la vitrina pública puede enseñar (ProductoDeVitrina,
 * ecommerce-carrito.yaml). Nada de tiraje, estado, versiones, fechas de
 * administración ni quién creó el producto.
 */
const CAMPOS_PUBLICOS = new Set([
  'id',
  'nombre',
  'imagenUrl',
  'descripcion',
  'habilidades',
  'tipo',
  'precioFinal',
  'precioOriginal',
  'moneda',
  'enPromocion',
  'porcentajeDescuento',
  'esPropio',
  'enListaDeseos',
  'precioCreditos',
]);

test.describe('F6 — la portada pública con la tienda', () => {
  /** @type {import('@playwright/test').APIRequestContext} */
  let api;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('`/` es la portada, sin redirección; su fichero lleva a `/`; login y registro siguen donde estaban', async () => {
    const raiz = await api.get('/', { maxRedirects: 0 });
    expect(raiz.status()).toBe(200);
    const html = await raiz.text();
    expect(html).toContain('data-vista="portada"');
    expect(html).toContain('<base href="/frontend/app-web/src/cuentas/">');
    expect(html).toContain('<meta name="nexus-rutas" content="limpias">');

    const fichero = await api.get('/frontend/app-web/src/cuentas/portada.html', {
      maxRedirects: 0,
    });
    expect(fichero.status()).toBe(302);
    expect(new URL(fichero.headers().location, BORDE).pathname).toBe('/');

    expect((await api.get('/login', { maxRedirects: 0 })).status()).toBe(200);
    expect((await api.get('/registro', { maxRedirects: 0 })).status()).toBe(200);
  });

  test('la vitrina pública no expone campos de administración ni internos', async () => {
    const r = await api.get('/api/v1/vitrina?page=0&size=50');
    expect(r.status(), await r.text()).toBe(200);
    const productos = (await r.json()).content;
    expect(productos.length).toBeGreaterThan(0);
    for (const producto of productos) {
      const sobran = Object.keys(producto).filter((campo) => !CAMPOS_PUBLICOS.has(campo));
      expect(sobran, `campos no públicos en ${producto.id}`).toEqual([]);
    }
  });

  test('sin cuenta, la API pide entrar para comprar y para guardar (401)', async () => {
    const r = await api.get('/api/v1/vitrina?page=0&size=1');
    const [primero] = (await r.json()).content;

    const carrito = await api.post('/api/v1/carrito/items', {
      data: { productoId: primero.id, cantidad: 1 },
    });
    const deseos = await api.put(`/api/v1/lista-deseos/${primero.id}`);
    const pago = await api.post('/api/v1/checkout', {
      headers: { 'Idempotency-Key': `portada-sin-cuenta-${Date.now()}` },
      data: {},
    });

    expect(carrito.status()).toBe(401);
    expect(deseos.status()).toBe(401);
    expect(pago.status()).toBe(401);
  });

  test('sin cuenta: productos reales y su detalle; «Entra para comprar» lleva a /login con la vuelta', async ({
    browser,
  }) => {
    const contexto = await browser.newContext();
    const page = await contexto.newPage();
    try {
      await page.goto(`${BORDE}/`);
      expect(new URL(page.url()).pathname).toBe('/');
      const tarjetas = page.locator('.vitrina-publica .product-card');
      await expect(tarjetas.first()).toBeVisible({ timeout: 20_000 });
      await expect(page.locator('.vitrina-publica .btn-add')).toHaveCount(0);
      await expect(page.locator('[data-zona="acciones-sin-sesion"]')).toBeVisible();
      await expect(page.locator('[data-zona="acciones-con-sesion"]')).toBeHidden();

      const axe = await new AxeBuilder({ page }).withTags(NORMAS).analyze();
      const graves = axe.violations.filter((v) => GRAVES.has(v.impact ?? ''));
      expect(graves.map((v) => `${v.id}: ${v.help}`)).toEqual([]);

      await tarjetas.first().locator('[data-ver-producto]').click();
      const ficha = page.locator('[role="dialog"].ficha');
      await expect(ficha.locator('.hilo-comentarios')).toBeVisible({ timeout: 20_000 });
      // Opinar pide entrar: no hay redactor sin cuenta.
      await expect(ficha.locator('.redactor-comentario')).toHaveCount(0);

      await ficha.locator('[data-accion="entrar-para-comprar"]').click();
      await expect(page).toHaveURL(/\/login\?/);
      const destino = new URL(page.url());
      expect(destino.pathname).toBe('/login');
      expect(destino.searchParams.get('volver')).toMatch(/tienda\.html$/);
      await expect(page.locator('#formLogin')).toBeVisible();
    } finally {
      await contexto.close();
    }
  });

  test('«Crear una cuenta» lleva a /registro', async ({ browser }) => {
    const contexto = await browser.newContext();
    const page = await contexto.newPage();
    try {
      await page.goto(`${BORDE}/`);
      await page.getByRole('link', { name: 'Crear una cuenta', exact: true }).click();
      await expect(page).toHaveURL(/\/registro$/);
    } finally {
      await contexto.close();
    }
  });

  test('con sesión: «Ir a mi inicio» en vez de entrar otra vez, y lleva a /inicio', async ({
    page,
  }) => {
    const jugadora = await sesionDelBanco(api, `portada_${Date.now()}`, {
      clave: CLAVE,
      base: BORDE,
    });
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugadora.token, jugadora.apodo, jugadora.claims.uid],
    );

    await page.goto(`${BORDE}/`);

    await expect(page.locator('[data-zona="acciones-con-sesion"]')).toBeVisible();
    await expect(page.locator('[data-zona="acciones-sin-sesion"]')).toBeHidden();
    await expect(page.locator('[data-zona="saludo"]')).toContainText(jugadora.apodo);
    await page.locator('[data-accion="ir-al-inicio"]').click();
    await expect(page).toHaveURL(/\/inicio$/);
  });
});
