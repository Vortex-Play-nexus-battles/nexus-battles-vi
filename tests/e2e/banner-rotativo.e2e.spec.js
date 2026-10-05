// @ts-check
/**
 * Banner informativo rotativo — RF-NOT-002 (#533) sobre la gestión de
 * HU-PRD-013 (#854), de punta a punta por el borde, con productos e
 * identidad reales.
 *
 * ## Por qué existe
 *
 * El borde no enrutaba `/api/v1/banners`: la consulta pública de vigentes
 * caía en el 404 genérico de «prefijo sin dueño» y la home se quedaba sin
 * banner en DEV sin que ningún banco lo notara. Este banco ya lleva el
 * prefijo a `srv-productos` (la misma sustitución de `e2e-borde-conf` que
 * `/api/v1/productos`), así que se puede afirmar el recorrido entero:
 *
 *   1. un ADMINISTRADOR programa un anuncio vigente por la API (y un jugador
 *      no puede, ni puede leer la lista de administración);
 *   2. la consulta pública lo devuelve, sin token;
 *   3. el jugador lo ve en Inicio, con «Publicado el …» y «Vigente hasta el …»;
 *   4. el administrador lo retira y deja de verse.
 *
 * El anuncio dura diez minutos y se retira en `afterAll` pase lo que pase:
 * si una corrida se corta a medias, no se queda en la home de los demás specs.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const INICIO = '/frontend/app-web/src/cuentas/index.html';

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
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

test.describe('Banner rotativo de la home (RF-NOT-002 sobre HU-PRD-013)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugador;
  let idBanner = null;
  const sufijo = Date.now();
  const contenido = `Anuncio del banco E2E ${sufijo}: torneo de otoño abierto`;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    jugador = await sesionDe(api, `banner_${sufijo}`);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
    expect(jugador.claims.rol).toBe('JUGADOR');
  });

  test.afterAll(async () => {
    if (idBanner) {
      // Idempotente: si la última prueba ya lo retiró, sigue siendo 204.
      await api.delete(`/api/v1/banners/${idBanner}`, { headers: conToken(admin.token) });
    }
    await api.dispose();
  });

  test('un administrador programa un anuncio vigente; un jugador no gestiona banners', async () => {
    const ahora = Date.now();
    const alta = await api.post('/api/v1/banners', {
      headers: conToken(admin.token),
      data: {
        contenido,
        publicarDesde: new Date(ahora - 60_000).toISOString(),
        vigenteHasta: new Date(ahora + 10 * 60_000).toISOString(),
      },
    });
    expect(alta.status(), await alta.text()).toBe(201);
    const creado = await alta.json();
    idBanner = creado.id;
    expect(creado).toMatchObject({ contenido, retirado: false });

    const intruso = await api.post('/api/v1/banners', {
      headers: conToken(jugador.token),
      data: {
        contenido: `Intruso ${sufijo}`,
        publicarDesde: new Date(ahora).toISOString(),
        vigenteHasta: new Date(ahora + 60_000).toISOString(),
      },
    });
    expect(intruso.status()).toBe(403);
    const listaDeAdministracion = await api.get('/api/v1/banners', {
      headers: conToken(jugador.token),
    });
    expect(listaDeAdministracion.status()).toBe(403);
  });

  test('la consulta pública de vigentes lo devuelve por el borde, sin token', async () => {
    const vigentes = await api.get('/api/v1/banners/vigentes');
    expect(
      vigentes.status(),
      'GET /api/v1/banners/vigentes tiene que llegar a productos (location del borde)',
    ).toBe(200);
    const lista = await vigentes.json();
    expect(lista.map((b) => b.id)).toContain(idBanner);
  });

  test('el jugador lo ve en Inicio con su fecha de publicación y su vigencia', async ({ page }) => {
    await conSesion(page, jugador);
    await page.goto(`${BORDE}${INICIO}`);

    const mensaje = page.locator(`[data-componente="banner-rotativo"] [data-banner="${idBanner}"]`);
    await expect(mensaje).toBeAttached({ timeout: 20_000 });
    // Puede estar rotando con otros vigentes: el texto se lee aunque no sea
    // el que se ve en este instante.
    const texto = (await mensaje.textContent()) ?? '';
    expect(texto).toContain(contenido);
    expect(texto).toContain('Publicado el');
    expect(texto).toContain('Vigente hasta el');
    await expect(page.locator('[data-zona="bloque-banners"]')).toBeVisible();
  });

  test('al retirarlo deja de estar vigente y la home ya no lo pinta', async ({ page }) => {
    const retiro = await api.delete(`/api/v1/banners/${idBanner}`, {
      headers: conToken(admin.token),
    });
    expect(retiro.status(), await retiro.text()).toBe(204);

    const vigentes = await api.get('/api/v1/banners/vigentes');
    expect((await vigentes.json()).map((b) => b.id)).not.toContain(idBanner);

    await conSesion(page, jugador);
    // La consulta de la home tiene que haber vuelto antes de afirmar que el
    // anuncio no está: si no, «no está» se cumpliría solo por llegar pronto.
    const consulta = page.waitForResponse((r) => r.url().includes('/api/v1/banners/vigentes'));
    await page.goto(`${BORDE}${INICIO}`);
    const respuesta = await consulta;
    expect(respuesta.status()).toBe(200);
    expect((await respuesta.json()).map((b) => b.id)).not.toContain(idBanner);
    await expect(page.locator(`[data-banner="${idBanner}"]`)).toHaveCount(0);
  });
});
