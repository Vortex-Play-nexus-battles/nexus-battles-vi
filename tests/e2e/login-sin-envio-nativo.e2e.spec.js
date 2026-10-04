/**
 * G1 (4-oct) — la contraseña nunca en una dirección.
 *
 * ## El defecto
 *
 * El formulario de entrada no tenía `method`, así que el navegador lo enviaba
 * por GET si se pulsaba antes de que `login.js` escuchara `submit`:
 * `/login?email=…&password=…`. La regresión del feedback lo vio en AWS DEV
 * (#844). La contraseña quedaba en la barra, en el historial, en la bitácora
 * del borde y en el `Referer` de la página siguiente.
 *
 * ## Lo que se prueba aquí, con Chromium y el borde de verdad
 *
 *   1. JavaScript normal: se entra con UNA petición POST a la API.
 *   2. JavaScript desactivado: ni el clic ni Enter envían nada.
 *   3. El guion tarda en llegar: Enter y clic antes de que llegue no envían
 *      nada; cuando llega, se entra con una sola petición.
 *   4. Enter muy rápido y doble envío, con la respuesta del servidor lenta:
 *      una sola petición.
 *   5. El borde: una dirección con la contraseña (una página vieja en caché)
 *      se redirige a la misma ruta sin consulta, y la página siguiente no la
 *      arrastra en su `Referer`.
 *
 * En todas, ninguna petición del navegador lleva la contraseña ni
 * `password=` en su dirección ni en su `Referer`. Que la bitácora del borde
 * tampoco la tenga lo comprueba e2e.yml al terminar (paso «El borde no
 * registró ninguna contraseña»), con la marca `G1-NoDebeQuedar`.
 *
 * La contraseña de la cuenta se escribe sin `fill` (su valor iría al título
 * del paso en el informe); la marca de las pruebas que no deben enviar nada
 * no es la contraseña de nadie.
 */

import { randomBytes } from 'node:crypto';

import { expect, test, request as apiRequest } from '@playwright/test';

import { sesionDe } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';

/** Lo que nunca puede aparecer en una dirección ni en un Referer. */
const MARCA = 'G1-NoDebeQuedar';

const RUTA_LOGIN = /\/api\/v1\/auth\/login$/;

function aleatorio() {
  return randomBytes(6).toString('hex');
}

/**
 * Todo lo que el navegador pide en este contexto: dirección, método y Referer.
 *
 * @param {import('@playwright/test').BrowserContext} contexto
 */
function grabar(contexto) {
  /** @type {Array<{url: string, metodo: string, referer: string}>} */
  const peticiones = [];
  contexto.on('request', (peticion) => {
    peticiones.push({
      url: peticion.url(),
      metodo: peticion.method(),
      referer: peticion.headers().referer ?? '',
    });
  });
  return peticiones;
}

/** Las peticiones de entrada a la API. */
function entradas(peticiones) {
  return peticiones.filter(
    ({ url, metodo }) => metodo === 'POST' && RUTA_LOGIN.test(new URL(url).pathname),
  );
}

/**
 * Ninguna dirección ni Referer lleva el secreto ni un parámetro `password`.
 *
 * @param {Array<{url: string, referer: string}>} peticiones
 * @param {string[]} secretos
 * @param {{salvoLaPrimera?: boolean}} [opciones] la 5.ª prueba ENTRA con la
 *   marca en la dirección a propósito: esa primera petición no cuenta.
 */
function sinSecretoEnDirecciones(peticiones, secretos, { salvoLaPrimera = false } = {}) {
  const revisadas = salvoLaPrimera ? peticiones.slice(1) : peticiones;
  for (const { url, referer } of revisadas) {
    for (const texto of [url, referer]) {
      expect(texto, 'una dirección o un Referer con password=').not.toMatch(
        /[?&][^=&#]*password[^=&#]*=/i,
      );
      for (const secreto of secretos) {
        expect(texto.includes(secreto), 'una dirección o un Referer con el secreto').toBe(false);
      }
    }
  }
}

/** Escribe un secreto como lo pega una persona, sin `fill`. */
async function escribirSecreto(campo, valor) {
  await campo.focus();
  await campo.evaluate((el, v) => {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, valor);
}

const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

test.describe('G1 — la contraseña nunca en una dirección', () => {
  /** @type {{email: string, clave: string}} */
  let cuenta;
  /** @type {import('@playwright/test').APIRequestContext} */
  let api;

  // Como en el resto del banco: `request` es por prueba y no vale en
  // beforeAll, así que la cuenta se crea con un contexto propio.
  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    const apodo = `g1login${aleatorio()}`;
    const clave = `Clave-G1-${aleatorio()}-Aa1!`;
    const sesion = await sesionDe(api, apodo, { clave, base: BORDE });
    cuenta = { email: sesion.email, clave };
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1. JavaScript normal: se entra con una sola petición POST', async ({ browser }) => {
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    const page = await contexto.newPage();

    await page.goto('/login');
    await expect(page.locator('#formLogin[data-listo]')).toBeAttached();
    await expect(page.locator('#formLogin')).toHaveAttribute('method', 'post');
    await page.fill('#email', cuenta.email);
    await escribirSecreto(page.locator('#password'), cuenta.clave);
    await page.click('#botonEnviar');
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    expect(entradas(peticiones)).toHaveLength(1);
    sinSecretoEnDirecciones(peticiones, [cuenta.clave]);
    await contexto.close();
  });

  test('2. JavaScript desactivado: ni el clic ni Enter envían nada', async ({ browser }) => {
    const contexto = await browser.newContext({ javaScriptEnabled: false });
    const peticiones = grabar(contexto);
    const page = await contexto.newPage();
    const marca = `${MARCA}-${aleatorio()}`;

    await page.goto('/login');
    await expect(page.getByText('Para entrar hace falta JavaScript')).toBeVisible();
    await expect(page.locator('#botonEnviar')).toBeDisabled();
    const antes = peticiones.length;

    await page.fill('#email', 'nadie@nexus.test');
    await page.fill('#password', marca);
    await page.locator('#password').press('Enter');
    await page.locator('#botonEnviar').click({ force: true });
    await esperar(1_500);

    expect(new URL(page.url()).pathname).toBe('/login');
    expect(new URL(page.url()).search).toBe('');
    // Ni una petición más: ni un GET con la consulta ni un POST al documento.
    expect(peticiones.slice(antes).filter(({ metodo }) => metodo !== 'GET')).toHaveLength(0);
    expect(
      peticiones.slice(antes).filter(({ url }) => new URL(url).pathname === '/login'),
    ).toHaveLength(0);
    sinSecretoEnDirecciones(peticiones, [marca]);
    await contexto.close();
  });

  test('3. El guion tarda: nada antes de que llegue, una sola petición después', async ({
    browser,
  }) => {
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    await contexto.route('**/cuentas/login.js', async (ruta) => {
      await esperar(4_000);
      await ruta.continue();
    });
    const page = await contexto.newPage();

    await page.goto('/login', { waitUntil: 'commit' });
    await page.locator('#password').waitFor({ state: 'attached' });
    await page.fill('#email', cuenta.email);
    await escribirSecreto(page.locator('#password'), cuenta.clave);
    // Enter muy rápido y un clic, antes de que el guion escuche `submit`.
    await page.locator('#password').press('Enter');
    await page.locator('#botonEnviar').click({ force: true });
    await expect(page.locator('#botonEnviar')).toBeDisabled();
    expect(page.url()).not.toContain('password');
    expect(entradas(peticiones)).toHaveLength(0);

    // Llega el guion: el botón se enciende y la entrada funciona.
    await expect(page.locator('#formLogin[data-listo]')).toBeAttached({ timeout: 20_000 });
    expect(new URL(page.url()).pathname).toBe('/login');
    expect(new URL(page.url()).search).toBe('');
    await page.locator('#password').press('Enter');
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    expect(entradas(peticiones)).toHaveLength(1);
    sinSecretoEnDirecciones(peticiones, [cuenta.clave]);
    await contexto.close();
  });

  test('4. Enter muy rápido y doble envío con el servidor lento: una sola petición', async ({
    browser,
  }) => {
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    // La respuesta tarda: los envíos de más caen mientras la primera vuela.
    await contexto.route(RUTA_LOGIN, async (ruta) => {
      await esperar(1_500);
      await ruta.continue();
    });
    const page = await contexto.newPage();

    await page.goto('/login');
    await expect(page.locator('#formLogin[data-listo]')).toBeAttached();
    await page.fill('#email', cuenta.email);
    await escribirSecreto(page.locator('#password'), cuenta.clave);
    await page.locator('#password').press('Enter');
    await page.locator('#password').press('Enter');
    await page.locator('#botonEnviar').dblclick({ force: true });
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    expect(entradas(peticiones)).toHaveLength(1);
    sinSecretoEnDirecciones(peticiones, [cuenta.clave]);
    await contexto.close();
  });

  test('5. El borde: una dirección con la contraseña vuelve a la ruta sin consulta', async ({
    browser,
    request,
  }) => {
    const marca = `${MARCA}-${aleatorio()}`;

    // Sin navegador: el 303 y adónde.
    const directa = await request.get(`/login?email=nadie%40nexus.test&password=${marca}`, {
      maxRedirects: 0,
    });
    expect(directa.status()).toBe(303);
    expect(directa.headers().location).toBe('/login');
    const antigua = await request.get(
      `/frontend/app-web/src/cuentas/login.html?password=${marca}`,
      { maxRedirects: 0 },
    );
    expect(antigua.status()).toBe(303);
    expect(antigua.headers().location).toBe('/frontend/app-web/src/cuentas/login.html');
    // Lo legítimo pasa sin tocar: la vuelta y la invitación a una sala privada.
    const vuelta = await request.get('/login?volver=%2Fjugar&motivo=caducada', {
      maxRedirects: 0,
    });
    expect(vuelta.status()).toBe(200);

    // Con navegador: lo que pasaría con una página vieja en caché.
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    const page = await contexto.newPage();
    await page.goto(`/login?email=nadie%40nexus.test&password=${marca}`);
    await expect(page.locator('#formLogin[data-listo]')).toBeAttached();

    expect(new URL(page.url()).pathname).toBe('/login');
    expect(new URL(page.url()).search).toBe('');
    // La primera petición es la que se hizo a propósito con la marca; ninguna
    // de las siguientes (la redirección, la vista, sus módulos, la tienda) la
    // lleva en su dirección ni en su Referer.
    sinSecretoEnDirecciones(peticiones, [marca], { salvoLaPrimera: true });
    await contexto.close();
  });
});
