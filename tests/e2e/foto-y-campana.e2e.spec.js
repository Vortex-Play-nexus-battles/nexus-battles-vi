/**
 * Auditoría de AWS DEV del 30-sep — la foto de perfil y el número de la
 * campana, de punta a punta por el borde y contra los servicios reales del
 * banco (`tests/e2e/compose.yml`).
 *
 *   1. Foto de perfil. En DEV `/avatares-subidos/<id>.jpg` devolvía 404 y en
 *      Mi cuenta se veía el texto alternativo: ms-identidad guarda la foto y
 *      devuelve esa dirección del MISMO origen, pero el borde no la llevaba a
 *      ninguna parte. Aquí se sube una foto como lo hace Mi cuenta (PUT
 *      multipart), se pide la dirección que devuelve el perfil AL BORDE y se
 *      mira que la vista la pinte cargada. Por el borde la ruta solo se lee.
 *   2. Campana. El número solo se encendía al abrir la bandeja. Aquí Bruno
 *      está en «Jugar online», Ana le escribe un mensaje privado (salas-
 *      partidas le deja un aviso en la bandeja) y el número de la cabecera
 *      sube sin que Bruno abra nada; al marcarlo leído por la API —como desde
 *      otra pestaña— vuelve a bajar.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Contrasena-E2E-2026';

// Jugadores de este spec y de ningún otro: sus avisos no se cruzan con nadie.
const FOTO = process.env.E2E_FOTO ?? 'foto_ana_e2e';
const ANA = process.env.E2E_CAMPANA_ANA ?? 'campana_ana_e2e';
const BRUNO = process.env.E2E_CAMPANA_BRUNO ?? 'campana_bruno_e2e';

const MI_CUENTA = '/frontend/app-web/src/cuentas/perfil.html';
const JUGAR = '/frontend/app-web/src/plataforma/salas-partidas/batallas.html';

/** Un PNG de 1×1: lo mínimo que es una imagen de verdad. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==',
  'base64',
);

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, ...extra };
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

let api;

test.beforeAll(async () => {
  api = await apiRequest.newContext({ baseURL: BORDE });
});

test.afterAll(async () => {
  await api?.dispose();
});

test.describe('foto de perfil', () => {
  test('la foto que se sube se sirve por el borde, y por el borde solo se lee', async () => {
    const ana = await sesionDe(api, FOTO);
    const subida = await api.put(`/api/v1/perfiles/${ana.claims.uid}`, {
      headers: conToken(ana.token),
      multipart: {
        nombres: 'Ana',
        apellidos: 'De Prueba',
        preferencias: 'Combate cuerpo a cuerpo',
        avatar: { name: 'ana.png', mimeType: 'image/png', buffer: PNG },
      },
    });
    expect(subida.status(), await subida.text()).toBe(200);

    const perfil = await api.get(`/api/v1/perfiles/${ana.claims.uid}`, {
      headers: conToken(ana.token),
    });
    expect(perfil.status(), await perfil.text()).toBe(200);
    const { avatar } = await perfil.json();
    expect(avatar, 'el perfil devuelve la dirección de su foto').toMatch(/^\/avatares-subidos\//);

    const foto = await api.get(avatar);
    expect(
      foto.status(),
      `GET ${avatar} por el borde: tiene que llegar a ms-identidad (location /avatares-subidos/)`,
    ).toBe(200);
    expect(foto.headers()['content-type']).toContain('image/');
    expect((await foto.body()).equals(PNG), 'es la misma imagen que se subió').toBe(true);

    const escritura = await api.post(avatar, { data: 'no' });
    expect(escritura.status(), 'por el borde la ruta de las fotos solo se lee').toBe(403);
  });

  test('Mi cuenta pinta la foto cargada, no su texto alternativo', async ({ page }) => {
    const ana = await sesionDe(api, FOTO);
    await conSesion(page, ana);
    await page.goto(`${BORDE}${MI_CUENTA}`);

    const foto = page.locator('[data-zona="identidad"] img.avatar-vista-previa');
    await expect(foto).toBeVisible({ timeout: 20_000 });
    await expect
      .poll(() => foto.evaluate((img) => img.complete && img.naturalWidth > 0), {
        timeout: 10_000,
        message: 'la foto tiene que cargar de verdad (naturalWidth > 0), no quedarse rota',
      })
      .toBe(true);
    await expect(page.locator('[data-zona="identidad"] [data-avatar="inicial"]')).toHaveCount(0);
  });
});

test.describe('número de la campana', () => {
  test('sube con un aviso nuevo sin abrir la bandeja, y baja al leerlo desde otro sitio', async ({
    page,
  }) => {
    const ana = await sesionDe(api, ANA);
    const bruno = await sesionDe(api, BRUNO);
    const bandeja = `/api/v1/users/${bruno.claims.uid}/notifications`;
    const deBruno = { headers: conToken(bruno.token) };

    // Punto de partida limpio: lo que Bruno tuviera sin leer (de una corrida
    // anterior o de un reintento) se lee antes de empezar. Son sus avisos y de
    // nadie más. La conversación también: salas-partidas avisa una vez por
    // racha de no leídos del mismo remitente, y una racha abierta no avisaría.
    const conversacion = await api.post(
      `/api/v1/mensajes-directos/conversaciones/${ana.claims.uid}/leido`,
      deBruno,
    );
    expect(conversacion.status(), await conversacion.text()).toBe(204);
    const previa = await api.get(bandeja, deBruno);
    expect(previa.status(), await previa.text()).toBe(200);
    for (const aviso of (await previa.json()).avisos.filter((a) => !a.leida)) {
      const leido = await api.post(`${bandeja}/${encodeURIComponent(aviso.id)}/read`, deBruno);
      expect(leido.status(), await leido.text()).toBe(200);
    }

    await conSesion(page, bruno);
    await page.goto(`${BORDE}${JUGAR}`);
    const contador = page.locator('[data-cabecera-app] [data-zona="contador"]');
    const campana = page.locator('[data-cabecera-app] .cabecera__campana');
    // La cabecera ya preguntó: la campana dice cuántos hay, aunque sean cero.
    await expect(campana).toHaveAttribute('aria-label', /sin notificaciones sin leer/, {
      timeout: 20_000,
    });
    await expect(contador).toBeHidden();

    const envio = await api.post(
      `/api/v1/mensajes-directos/conversaciones/${bruno.claims.uid}/mensajes`,
      {
        headers: conToken(ana.token, { 'Content-Type': 'application/json' }),
        data: { texto: `hola desde la campana ${Date.now()}` },
      },
    );
    expect(envio.status(), await envio.text()).toBe(201);
    const mensaje = await envio.json();

    await expect(
      contador,
      'el aviso del mensaje llega por el canal y la cabecera lo cuenta sin abrir la bandeja',
    ).toHaveText('1', { timeout: 20_000 });
    await expect(contador).toBeVisible();
    await expect(campana).toHaveAttribute('aria-label', 'Notificaciones: 1 notificación sin leer');
    await expect(page, 'nadie abrió la bandeja').not.toHaveURL(/notificaciones/);

    // Leído «desde otro sitio» (la API, como otra pestaña): el contador baja solo.
    const leido = await api.post(
      `${bandeja}/${encodeURIComponent(`mensaje-directo-${mensaje.id}`)}/read`,
      deBruno,
    );
    expect(leido.status(), await leido.text()).toBe(200);
    await expect(contador).toBeHidden({ timeout: 20_000 });
  });
});
