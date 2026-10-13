/**
 * HTTPS del borde, como lo ve un jugador (6-oct).
 *
 * Corre en el smoke de DEV y solo cuando el entorno se publica por https
 * (`E2E_AWS` = `PUBLIC_BASE_URL` = `https://<dominio>`). Mientras el borde
 * siga en `http://<IP>` se salta entero: no hay nada que comprobar.
 *
 * Lo que una sola peticion no dice y un navegador si:
 *   - el 80 lleva al 443 con 301, conservando ruta y consulta, tambien si se
 *     entra por la IP;
 *   - las direcciones limpias se sirven por https y la barra nunca termina en
 *     `/frontend/app-web/src/...`;
 *   - contenido mixto 0: ninguna peticion de la pagina sale por `http://` ni
 *     por `ws://`, ni hacia otro host (la IP, un puerto de servicio como
 *     `:8089`), con sesion y sin ella;
 *   - los enlaces de los correos (verificar, restablecer) llevan al dominio
 *     por https;
 *   - el canal de notificaciones acepta el CONNECT por `wss://` (el de salas
 *     lo prueba `smoke-aws.smoke.spec.js`, que deriva wss de esta misma base).
 */

import dns from 'node:dns/promises';

import { test, expect, request as apiRequest } from '@playwright/test';

import { esperarCodigo } from './ayudantes/correo.js';
import { respetandoElLimite, sesionDe } from './ayudantes/cuentas.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const ES_HTTPS = AWS.startsWith('https://');
const ORIGEN = ES_HTTPS ? new URL(AWS).origin : '';
const HOST = ES_HTTPS ? new URL(AWS).host : '';
const CLAVE = 'Contrasena-HTTPS-2026';
const ACEPTA = 'application/problem+json, application/json, text/plain';

/** Lo que un jugador escribe o comparte; las de sesion acaban en /login. */
const DIRECCIONES = [
  '/',
  '/login',
  '/registro',
  '/recuperar',
  '/inicio',
  '/jugar',
  '/inventario',
  '/misiones',
  '/torneos',
  '/subastas',
  '/cuenta',
];

/**
 * Apunta todo lo que la pagina pide (peticiones y WebSocket) y los avisos de
 * contenido mixto de la consola.
 *
 * @param {import('@playwright/test').Page} page
 */
function vigilar(page) {
  const vistas = { urls: [], mixtos: [] };
  page.on('request', (r) => vistas.urls.push(r.url()));
  page.on('websocket', (ws) => vistas.urls.push(ws.url()));
  page.on('console', (m) => {
    if (/mixed content/i.test(m.text())) {
      vistas.mixtos.push(m.text());
    }
  });
  return vistas;
}

/** Lo que no sea del propio dominio por https/wss (data: y blob: no salen a la red). */
function ajenas(urls) {
  return urls.filter(
    (u) =>
      !u.startsWith(`${ORIGEN}/`) &&
      !u.startsWith(`wss://${HOST}/`) &&
      !u.startsWith('data:') &&
      !u.startsWith('blob:'),
  );
}

/** Escribe la contrasena como la pega una persona, sin `fill` (ver login-sin-envio-nativo). */
async function escribirSecreto(campo, valor) {
  await campo.focus();
  await campo.evaluate((el, v) => {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, valor);
}

test.describe('HTTPS del borde', () => {
  test.skip(!ES_HTTPS, 'E2E_AWS no es https: el borde todavia no publica el dominio');
  test.describe.configure({ mode: 'serial' });

  const marca = Date.now();
  const apodo = `https_${marca}`;
  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  /** @type {import('@playwright/test').APIRequestContext} */
  let sinRedireccion;
  let jugador;

  test.beforeAll(async () => {
    // Sin ignoreHTTPSErrors: un certificado que no valga hace fallar la prueba.
    api = await apiRequest.newContext({ baseURL: AWS });
    sinRedireccion = await apiRequest.newContext();
  });

  test.afterAll(async () => {
    await api?.dispose();
    await sinRedireccion?.dispose();
  });

  test('el 80 lleva al 443 con 301 y conserva ruta y consulta', async () => {
    const r = await sinRedireccion.get(`http://${HOST}/jugar?sala=prueba`, { maxRedirects: 0 });
    expect(r.status()).toBe(301);
    expect(r.headers().location).toBe(`${ORIGEN}/jugar?sala=prueba`);
  });

  test('entrar por la IP tambien acaba en el dominio', async () => {
    const [ip] = await dns.resolve4(new URL(AWS).hostname);
    const r = await sinRedireccion.get(`http://${ip}/login`, { maxRedirects: 0 });
    expect(r.status()).toBe(301);
    expect(r.headers().location).toBe(`${ORIGEN}/login`);
  });

  test('las direcciones limpias se sirven por https, sin /frontend/ en la barra', async ({
    page,
  }) => {
    for (const ruta of DIRECCIONES) {
      const respuesta = await page.goto(`${ORIGEN}${ruta}`);
      expect(respuesta?.status(), `${ruta} respondio ${respuesta?.status()}`).toBeLessThan(400);
      // Las de sesion redirigen al login en el navegador: se espera a que se asiente.
      await page.waitForLoadState('domcontentloaded');
      const final = new URL(page.url());
      expect(final.origin, `${ruta} termino fuera del dominio: ${page.url()}`).toBe(ORIGEN);
      expect(final.pathname, `${ruta} termino en ${page.url()}`).not.toContain(
        '/frontend/app-web/src/',
      );
    }
  });

  test('sin sesion: contenido mixto 0 y nada fuera del dominio', async ({ page }) => {
    const vistas = vigilar(page);
    for (const ruta of ['/', '/login', '/registro', '/recuperar']) {
      await page.goto(`${ORIGEN}${ruta}`);
      await page.waitForLoadState('networkidle');
    }
    expect(vistas.mixtos, vistas.mixtos.join('\n')).toEqual([]);
    expect(ajenas(vistas.urls), ajenas(vistas.urls).join('\n')).toEqual([]);
  });

  test('los enlaces del correo de verificacion llevan al dominio por https', async () => {
    jugador = await sesionDe(api, apodo, { clave: CLAVE, base: AWS });
    const correo = await esperarCodigo(jugador.email, { tipo: 'verificacion', base: AWS });
    expect(correo.enlace, 'el correo de verificacion no trae enlace').toBeTruthy();
    expect(correo.enlace).toMatch(new RegExp(`^${ORIGEN.replace(/[.]/g, '\\.')}/verificar#`));
  });

  test('y los del correo de recuperacion tambien', async () => {
    const r = await respetandoElLimite(() =>
      api.post('/api/v1/auth/restablecer/solicitar', {
        headers: { Accept: ACEPTA },
        data: { email: jugador.email },
      }),
    );
    expect(r.status()).toBe(200);
    const correo = await esperarCodigo(jugador.email, { tipo: 'recuperacion', base: AWS });
    expect(correo.enlace, 'el correo de recuperacion no trae enlace').toBeTruthy();
    expect(correo.enlace).toMatch(new RegExp(`^${ORIGEN.replace(/[.]/g, '\\.')}/restablecer#`));
  });

  test('con sesion: contenido mixto 0, nada fuera del dominio y los canales por wss', async ({
    page,
  }) => {
    const vistas = vigilar(page);
    await page.goto(`${ORIGEN}/login`);
    await expect(page.locator('#formLogin[data-listo]')).toBeAttached();
    await page.fill('#email', jugador.email);
    await escribirSecreto(page.locator('#password'), CLAVE);
    await page.click('#botonEnviar');
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30_000 });

    for (const ruta of ['/inicio', '/jugar', '/inventario', '/misiones', '/torneos', '/cuenta']) {
      await page.goto(`${ORIGEN}${ruta}`);
      await page.waitForLoadState('networkidle');
    }
    expect(vistas.mixtos, vistas.mixtos.join('\n')).toEqual([]);
    expect(ajenas(vistas.urls), ajenas(vistas.urls).join('\n')).toEqual([]);
    const sockets = vistas.urls.filter((u) => /^wss?:/.test(u));
    for (const s of sockets) {
      expect(s, 'un WebSocket sin cifrar desde una pagina https').toMatch(/^wss:\/\//);
    }
  });

  test('el canal de notificaciones acepta el CONNECT por wss con el JWT', async ({ page }) => {
    await page.goto(`${ORIGEN}/login`);
    const url = `wss://${HOST}/ws/notificaciones`;
    const resultado = await page.evaluate(
      ([destino, token]) =>
        new Promise((resolver) => {
          const NUL = '\u0000';
          const socket = new WebSocket(destino);
          const cortar = setTimeout(() => resolver('sin respuesta en 15 s'), 15000);
          socket.onopen = () =>
            socket.send(
              `CONNECT\naccept-version:1.2\nheart-beat:0,0\n` +
                `Authorization:Bearer ${token}\n\n${NUL}`,
            );
          socket.onmessage = (evento) => {
            clearTimeout(cortar);
            resolver(String(evento.data).split('\n')[0]);
          };
          socket.onerror = () => {
            clearTimeout(cortar);
            resolver('error de transporte');
          };
          socket.onclose = () => {
            clearTimeout(cortar);
            resolver('cerrado sin CONNECTED');
          };
        }),
      [url, jugador.token],
    );
    expect(resultado, `respuesta del canal en ${url}`).toBe('CONNECTED');
  });
});
