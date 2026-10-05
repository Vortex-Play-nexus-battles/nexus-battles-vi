/**
 * HU-AUT-007 — verificación en dos pasos (TOTP) de punta a punta, con el
 * borde, ms-identidad y Chromium de verdad.
 *
 * Una jugadora del banco:
 *
 *   1. entra con el login de siempre (sin segundo factor no cambia nada) y
 *      activa la verificación en «Mi cuenta > Seguridad»: lee la clave en la
 *      pantalla, calcula el código como lo haría su teléfono y guarda los
 *      códigos de recuperación;
 *   2. vuelve a entrar en DOS pasos: la contraseña ya no abre la sesión, el
 *      servidor pide el código, y la sesión que sale dice `amr: pwd + otp`;
 *   3. entra con un código de recuperación, que se gasta: el mismo código no
 *      sirve una segunda vez, y un cliente viejo nunca recibe un token en el
 *      403 del primer paso;
 *   4. la desactiva con su contraseña y otro código de recuperación, y el
 *      login vuelve a ser de un solo paso.
 *
 * El código TOTP se calcula aquí con `node:crypto` (RFC 6238: HMAC-SHA1, 6
 * dígitos, 30 s), sin dependencias. El servidor no acepta dos veces el mismo
 * paso de 30 s ni uno anterior, así que el segundo código es el del paso
 * siguiente al de la activación (la ventana de ±1 paso lo admite).
 *
 * El desafío, el código y la clave no aparecen en ninguna dirección ni en el
 * almacenamiento del navegador. Los secretos se escriben sin `fill` (su valor
 * iría al título del paso en el informe).
 *
 * El banco arranca ms-identidad con una clave de cifrado de PRUEBAS
 * (tests/e2e/compose.yml) y la obligatoriedad por rol apagada, igual que DEV.
 */

import { createHmac, randomBytes } from 'node:crypto';

import { expect, test, request as apiRequest } from '@playwright/test';

import {
  cuerpoDelToken,
  iniciarSesion,
  respetandoElLimite,
  sesionDe,
  tipoDe,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const MI_CUENTA = '/frontend/app-web/src/cuentas/perfil.html';
const ACEPTA = 'application/problem+json, application/json';

// ------------------------------------------------------------------ TOTP

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/** RFC 4648 sin relleno, como la da el servidor. */
function bytesDeBase32(texto) {
  const bytes = [];
  let acumulado = 0;
  let bits = 0;
  for (const letra of texto.replace(/[\s=]/g, '').toUpperCase()) {
    const valor = BASE32.indexOf(letra);
    if (valor < 0) {
      throw new Error(`la clave no es base32: «${letra}»`);
    }
    acumulado = ((acumulado << 5) | valor) & 0xffff;
    bits += 5;
    if (bits >= 8) {
      bytes.push((acumulado >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** El paso de 30 s de un instante (T0 = 0). */
function pasoDe(instanteMs = Date.now()) {
  return Math.floor(instanteMs / 1000 / 30);
}

/** El código de 6 dígitos de un paso (RFC 6238 sobre RFC 4226). */
function totp(secretoBase32, paso) {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(paso));
  const resumen = createHmac('sha1', bytesDeBase32(secretoBase32)).update(contador).digest();
  const desplazamiento = resumen[resumen.length - 1] & 0x0f;
  const binario =
    ((resumen[desplazamiento] & 0x7f) << 24) |
    (resumen[desplazamiento + 1] << 16) |
    (resumen[desplazamiento + 2] << 8) |
    resumen[desplazamiento + 3];
  return String(binario % 1_000_000).padStart(6, '0');
}

// --------------------------------------------------------------- ayudantes

function aleatorio() {
  return randomBytes(5).toString('hex');
}

/** Escribe un secreto como lo pega una persona, sin `fill`. */
async function escribir(campo, valor) {
  await campo.focus();
  await campo.evaluate((el, v) => {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, valor);
}

/** Todo lo que el navegador pide en este contexto. */
function grabar(contexto) {
  /** @type {Array<{url: string, referer: string}>} */
  const peticiones = [];
  contexto.on('request', (peticion) => {
    peticiones.push({ url: peticion.url(), referer: peticion.headers().referer ?? '' });
  });
  return peticiones;
}

/**
 * Ningún secreto en una dirección, un Referer o el almacenamiento del
 * navegador. Los largos (desafío, clave, códigos de recuperación) no pueden
 * aparecer en ninguna parte; un código de 6 cifras, como valor de un
 * parámetro o tramo de una ruta (seis cifras seguidas pueden salir por azar
 * dentro de una marca de tiempo).
 *
 * @param {import('@playwright/test').Page} page
 * @param {Array<{url: string, referer: string}>} peticiones
 * @param {{largos?: string[], cortos?: string[]}} secretos
 */
async function sinSecretos(page, peticiones, { largos = [], cortos = [] }) {
  const guardado = await page.evaluate(() =>
    JSON.stringify({ local: { ...localStorage }, sesion: { ...sessionStorage } }),
  );
  const direcciones = peticiones.flatMap(({ url, referer }) => [url, referer]).filter(Boolean);
  for (const secreto of largos.filter(Boolean)) {
    for (const direccion of direcciones) {
      expect(direccion.includes(secreto), `un secreto en ${direccion}`).toBe(false);
    }
    expect(guardado.includes(secreto), 'un secreto en el almacenamiento').toBe(false);
  }
  for (const codigo of cortos.filter(Boolean)) {
    const comoValor = new RegExp(`[=/]${codigo}(?:[&#/]|$)`);
    for (const direccion of direcciones) {
      expect(comoValor.test(direccion), `un código en ${direccion}`).toBe(false);
    }
    expect(guardado.includes(`"${codigo}"`), 'un código en el almacenamiento').toBe(false);
  }
}

/** El primer paso por la interfaz: correo y contraseña. */
async function primerPaso(page, cuenta) {
  await page.goto('/login');
  await expect(page.locator('#formLogin[data-listo]')).toBeAttached();
  await page.fill('#email', cuenta.email);
  await escribir(page.locator('#password'), cuenta.clave);
  const respuesta = page.waitForResponse(
    (r) => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/auth/login',
  );
  await page.click('#botonEnviar');
  return respuesta;
}

/** El token que dejó login.js en esta pestaña. */
function tokenDe(page) {
  return page.evaluate(() => sessionStorage.getItem('nexus.token'));
}

/** POST /auth/login/segundo-factor por API, con problem details. */
async function canjear(api, cuerpo) {
  const respuesta = await respetandoElLimite(() =>
    api.post('/api/v1/auth/login/segundo-factor', { headers: { Accept: ACEPTA }, data: cuerpo }),
  );
  const texto = await respuesta.text();
  let json = null;
  try {
    json = JSON.parse(texto);
  } catch {
    json = null;
  }
  return { estado: respuesta.status(), cuerpo: json, tipo: tipoDe(json), texto };
}

// ------------------------------------------------------------------ pruebas

test.describe('HU-AUT-007 · verificación en dos pasos', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  /** @type {{email: string, clave: string, secreto?: string, codigos?: string[], pasoDeActivacion?: number}} */
  const cuenta = { email: '', clave: '' };

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    const apodo = `tfa${aleatorio()}`;
    cuenta.clave = `Clave-2FA-${aleatorio()}-Aa1!`;
    const sesion = await sesionDe(api, apodo, { clave: cuenta.clave, base: BORDE });
    cuenta.email = sesion.email;
    // Regresión: sin segundo factor, el login de siempre y una sesión de
    // contraseña sola.
    expect(sesion.claims.amr).toEqual(['pwd']);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · la activa en «Mi cuenta > Seguridad» con la clave y un código de su aplicación', async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    const page = await contexto.newPage();

    // Sin segundo factor: un solo paso, como siempre.
    const login = await primerPaso(page, cuenta);
    expect(login.status()).toBe(200);
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    await page.goto(`${MI_CUENTA}#seguridad`);
    const seccion = page.locator('[data-zona="segundo-factor"]');
    const estado = seccion.locator('[data-zona="estado-segundo-factor"]');
    await expect(estado).toContainText('Desactivada', { timeout: 20_000 });
    await seccion.locator('[data-accion="activar-segundo-factor"]').click();

    const enlace = seccion.locator('[data-zona="enlace-otpauth"]');
    await expect(enlace).toHaveAttribute('href', /^otpauth:\/\/totp\//, { timeout: 20_000 });
    const secreto = (await seccion.locator('[data-zona="secreto"]').textContent()).replace(
      /\s+/g,
      '',
    );
    expect(secreto).toMatch(/^[A-Z2-7]{32}$/);
    // El enlace lleva la misma clave que se ve, y lo que la aplicación necesita.
    const uri = new URL(await enlace.getAttribute('href'));
    expect(uri.searchParams.get('secret')).toBe(secreto);
    expect(uri.searchParams.get('digits')).toBe('6');
    expect(uri.searchParams.get('period')).toBe('30');

    cuenta.pasoDeActivacion = pasoDe();
    const codigo = totp(secreto, cuenta.pasoDeActivacion);
    const campo = seccion.locator('#codigoActivacion');
    await expect(campo).toHaveAttribute('autocomplete', 'one-time-code');
    await escribir(campo, codigo);
    await seccion.locator('[data-accion="confirmar-activacion"]').click();

    // Los códigos de recuperación, una sola vez.
    const codigos = seccion.locator('[data-zona="codigo-recuperacion"]');
    await expect(codigos).toHaveCount(10, { timeout: 20_000 });
    cuenta.codigos = (await codigos.allTextContents()).map((c) => c.trim());
    for (const uno of cuenta.codigos) {
      expect(uno).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{5}-[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{5}$/);
    }
    // La clave ya no está en la pantalla.
    await expect(seccion.locator('[data-zona="secreto"]')).toHaveCount(0);

    await seccion.locator('[data-accion="codigos-guardados"]').click();
    await expect(codigos).toHaveCount(0);
    await expect(estado).toContainText('Activada desde el', { timeout: 20_000 });
    await expect(estado).toContainText('Te quedan 10 códigos de recuperación');

    cuenta.secreto = secreto;
    await sinSecretos(page, peticiones, {
      largos: [secreto, ...cuenta.codigos],
      cortos: [codigo],
    });
    await contexto.close();
  });

  test('2 · vuelve a entrar en dos pasos: la contraseña ya no basta y la sesión dice pwd + otp', async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    test.skip(!cuenta.secreto, 'depende de la activación de la prueba 1');
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    const page = await contexto.newPage();

    const login = await primerPaso(page, cuenta);
    expect(login.status()).toBe(403);
    const cuerpo = await login.json();
    expect(tipoDe(cuerpo)).toBe('segundo-factor-requerido');
    // Un cliente viejo no puede confundirlo con una sesión.
    expect(cuerpo.token).toBeUndefined();
    expect(typeof cuerpo.desafio).toBe('string');

    // El segundo paso, no un rechazo; la contraseña ya no está escrita.
    await expect(page.locator('[data-zona="segundo-paso"]')).toBeVisible();
    await expect(page.locator('#formLogin')).toBeHidden();
    await expect(page.locator('[data-zona="rechazo"]')).toBeHidden();
    expect(await tokenDe(page)).toBeNull();
    const campo = page.locator('#codigoSegundoPaso');
    await expect(campo).toHaveAttribute('autocomplete', 'one-time-code');
    await expect(campo).toHaveAttribute('inputmode', 'numeric');

    // Un paso posterior al de la activación (el mismo no se acepta dos veces):
    // el siguiente, que la ventana de ±1 admite, o el de ahora si ya pasó.
    const codigo = totp(cuenta.secreto, Math.max(cuenta.pasoDeActivacion + 1, pasoDe()));
    await escribir(campo, codigo);
    const canje = page.waitForResponse(
      (r) => new URL(r.url()).pathname === '/api/v1/auth/login/segundo-factor',
    );
    await page.locator('[data-accion="verificar-segundo-paso"]').click();
    expect((await canje).status()).toBe(200);
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    const token = await tokenDe(page);
    expect(token).toBeTruthy();
    expect(cuerpoDelToken(token).amr).toEqual(['pwd', 'otp']);
    await sinSecretos(page, peticiones, {
      largos: [cuerpo.desafio, cuenta.secreto],
      cortos: [codigo],
    });
    await contexto.close();
  });

  test('3 · un código de recuperación entra una vez y dice cuántos quedan; el mismo no sirve dos veces', async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    test.skip(!cuenta.codigos?.length, 'depende de la activación de la prueba 1');
    const recuperacion = cuenta.codigos[0];
    const contexto = await browser.newContext();
    const peticiones = grabar(contexto);
    const page = await contexto.newPage();

    expect((await primerPaso(page, cuenta)).status()).toBe(403);
    await page.locator('[data-accion="usar-recuperacion"]').click();
    const campo = page.locator('#codigoRecuperacionSegundoPaso');
    await expect(campo).toBeVisible();
    // Como lo copiaría alguien del archivo: en minúsculas también vale.
    await escribir(campo, recuperacion.toLowerCase());
    await page.locator('[data-accion="verificar-segundo-paso"]').click();

    const aviso = page.locator('[data-zona="rechazo-segundo-paso"]');
    await expect(aviso).toContainText('Entraste con un código de recuperación', {
      timeout: 20_000,
    });
    await expect(aviso).toContainText('Te quedan 9.');
    await page.locator('[data-accion="continuar-tras-recuperacion"]').click();
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });
    expect(await tokenDe(page)).toBeTruthy();
    await sinSecretos(page, peticiones, { largos: [recuperacion, cuenta.secreto] });
    await contexto.close();

    // Por API: el mismo código ya no sirve (y cuenta como intento fallido).
    const login = await iniciarSesion(api, cuenta.email, cuenta.clave);
    expect(login.estado, login.texto).toBe(403);
    expect(login.tipo).toBe('segundo-factor-requerido');
    expect(login.cuerpo.token).toBeUndefined();
    const otraVez = await canjear(api, {
      desafio: login.cuerpo.desafio,
      codigoRecuperacion: recuperacion,
    });
    expect(otraVez.estado, otraVez.texto).toBe(401);
    expect(otraVez.tipo).toBe('codigo-segundo-factor-invalido');
  });

  test('4 · la desactiva con su contraseña y otro código, y el login vuelve a ser de un paso', async ({
    browser,
  }) => {
    test.setTimeout(120_000);
    test.skip(!cuenta.codigos?.length, 'depende de la activación de la prueba 1');
    const contexto = await browser.newContext();
    const page = await contexto.newPage();

    // Entra con otro código de recuperación (el segundo de la lista).
    expect((await primerPaso(page, cuenta)).status()).toBe(403);
    await page.locator('[data-accion="usar-recuperacion"]').click();
    await escribir(page.locator('#codigoRecuperacionSegundoPaso'), cuenta.codigos[1]);
    await page.locator('[data-accion="verificar-segundo-paso"]').click();
    await page.locator('[data-accion="continuar-tras-recuperacion"]').click();
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    await page.goto(`${MI_CUENTA}#seguridad`);
    const seccion = page.locator('[data-zona="segundo-factor"]');
    const estado = seccion.locator('[data-zona="estado-segundo-factor"]');
    await expect(estado).toContainText('Te quedan 8 códigos de recuperación', { timeout: 20_000 });
    await seccion.locator('[data-accion="desactivar-segundo-factor"]').click();
    await escribir(seccion.locator('#passwordActualSegundoFactor'), cuenta.clave);
    await escribir(seccion.locator('#codigoDesactivacion'), cuenta.codigos[2]);
    await seccion.locator('[data-accion="confirmar-desactivacion"]').click();
    await expect(seccion.locator('[data-zona="aviso-segundo-factor"]')).toContainText(
      'quedó desactivada',
      { timeout: 20_000 },
    );
    await expect(estado).toContainText('Desactivada');
    await contexto.close();

    // El login vuelve a abrir la sesión a la primera, con contraseña sola.
    const login = await iniciarSesion(api, cuenta.email, cuenta.clave);
    expect(login.estado, login.texto).toBe(200);
    expect(cuerpoDelToken(login.cuerpo.token).amr).toEqual(['pwd']);
  });
});
