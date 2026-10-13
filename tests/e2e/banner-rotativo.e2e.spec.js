// @ts-check
/**
 * Banner informativo rotativo — RF-NOT-002 (#533) sobre la gestión de
 * HU-PRD-013 (#854 → #869), de punta a punta por el borde, con productos,
 * Mongo e identidad reales.
 *
 * ## Por qué existe
 *
 * El borde no enrutaba `/api/v1/banners`: la consulta pública de vigentes
 * caía en el 404 genérico de «prefijo sin dueño» y la home se quedaba sin
 * banner en DEV sin que ningún banco lo notara. Este banco ya lleva el
 * prefijo a `srv-productos` (la misma sustitución de `e2e-borde-conf` que
 * `/api/v1/productos`), así que se puede afirmar el recorrido entero:
 *
 *   1. un ADMINISTRADOR programa un anuncio vigente por la API; un jugador no
 *      puede crear, editar, retirar ni leer la lista de administración, y sin
 *      token la respuesta es 401;
 *   2. el administrador lo edita y lo consulta en su lista;
 *   3. HU-PRD-013 CA-02/CA-03: la consulta pública y la home muestran el
 *      vigente y NO muestran uno programado a futuro ni uno que ya venció;
 *   4. el jugador lo ve en Inicio, con «Publicado el …» y «Vigente hasta el …»;
 *   5. el administrador lo retira y deja de verse (y su lista lo marca
 *      retirado).
 *
 * Todo anuncio que crea este spec se retira en `afterAll` pase lo que pase
 * (retirar es lógico, nunca un borrado): si una corrida se corta a medias, no
 * se queda en la home de los demás specs.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const INICIO = '/frontend/app-web/src/cuentas/index.html';
const MINUTO = 60_000;

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

/** Los id de la consulta pública, la misma que pide la home. */
async function idsVigentes(api) {
  const vigentes = await api.get('/api/v1/banners/vigentes');
  expect(vigentes.status()).toBe(200);
  return (await vigentes.json()).map((b) => b.id);
}

test.describe('Banner rotativo de la home (RF-NOT-002 sobre HU-PRD-013)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugador;
  let idBanner = null;
  let idFuturo = null;
  let idVencido = null;
  const creados = [];
  const sufijo = Date.now();
  const contenido = `Anuncio del banco E2E ${sufijo}: torneo de otoño abierto`;
  const contenidoEditado = `Anuncio del banco E2E ${sufijo}: torneo de otoño, inscripciones hasta el viernes`;

  /** Programa un anuncio como el administrador y lo apunta para retirarlo al final. */
  async function programar(texto, publicarDesde, vigenteHasta) {
    const alta = await api.post('/api/v1/banners', {
      headers: conToken(admin.token),
      data: {
        contenido: texto,
        publicarDesde: new Date(publicarDesde).toISOString(),
        vigenteHasta: new Date(vigenteHasta).toISOString(),
      },
    });
    expect(alta.status(), await alta.text()).toBe(201);
    const creado = await alta.json();
    creados.push(creado.id);
    return creado;
  }

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
    // Idempotente: retirar uno ya retirado sigue siendo 204.
    for (const id of creados) {
      await api.delete(`/api/v1/banners/${id}`, { headers: conToken(admin.token) });
    }
    await api.dispose();
  });

  test('un administrador programa un anuncio vigente; un jugador no gestiona banners', async () => {
    const ahora = Date.now();
    const creado = await programar(contenido, ahora - MINUTO, ahora + 10 * MINUTO);
    idBanner = creado.id;
    expect(creado).toMatchObject({ contenido, retirado: false });

    const cuerpoIntruso = {
      contenido: `Intruso ${sufijo}`,
      publicarDesde: new Date(ahora).toISOString(),
      vigenteHasta: new Date(ahora + MINUTO).toISOString(),
    };
    const intruso = await api.post('/api/v1/banners', {
      headers: conToken(jugador.token),
      data: cuerpoIntruso,
    });
    expect(intruso.status()).toBe(403);
    const listaDeAdministracion = await api.get('/api/v1/banners', {
      headers: conToken(jugador.token),
    });
    expect(listaDeAdministracion.status()).toBe(403);
    const edicionDelJugador = await api.put(`/api/v1/banners/${idBanner}`, {
      headers: conToken(jugador.token),
      data: cuerpoIntruso,
    });
    expect(edicionDelJugador.status()).toBe(403);
    const retiroDelJugador = await api.delete(`/api/v1/banners/${idBanner}`, {
      headers: conToken(jugador.token),
    });
    expect(retiroDelJugador.status()).toBe(403);

    // Sin token: 401, ni crear ni leer la lista de administración.
    const sinToken = await api.post('/api/v1/banners', {
      headers: { 'Content-Type': 'application/json' },
      data: cuerpoIntruso,
    });
    expect(sinToken.status()).toBe(401);
    expect((await api.get('/api/v1/banners')).status()).toBe(401);

    // Nada de lo anterior cambió el anuncio.
    expect(await idsVigentes(api)).toContain(idBanner);
  });

  test('el administrador lo edita y lo consulta en su lista', async () => {
    const actual = (
      await (await api.get('/api/v1/banners', { headers: conToken(admin.token) })).json()
    ).find((b) => b.id === idBanner);
    const edicion = await api.put(`/api/v1/banners/${idBanner}`, {
      headers: conToken(admin.token),
      data: {
        contenido: contenidoEditado,
        publicarDesde: actual.publicarDesde,
        vigenteHasta: actual.vigenteHasta,
      },
    });
    expect(edicion.status(), await edicion.text()).toBe(200);
    expect(await edicion.json()).toMatchObject({
      id: idBanner,
      contenido: contenidoEditado,
      retirado: false,
    });

    const lista = await api.get('/api/v1/banners', { headers: conToken(admin.token) });
    expect(lista.status()).toBe(200);
    const enLaLista = (await lista.json()).find((b) => b.id === idBanner);
    expect(enLaLista).toMatchObject({ contenido: contenidoEditado, retirado: false });
    expect(Date.parse(enLaLista.modificadoEn)).toBeGreaterThanOrEqual(
      Date.parse(enLaLista.creadoEn),
    );
  });

  test('HU-PRD-013: ni uno programado a futuro ni uno vencido salen en la consulta pública', async () => {
    const ahora = Date.now();
    idFuturo = (
      await programar(`Futuro del banco E2E ${sufijo}`, ahora + 10 * MINUTO, ahora + 20 * MINUTO)
    ).id;
    // La vigencia tiene que estar en el futuro al crearlo (@Future): se programa
    // uno que vence en segundos y se espera a que venza de verdad.
    idVencido = (await programar(`Vencido del banco E2E ${sufijo}`, ahora - MINUTO, ahora + 4_000))
      .id;
    await expect
      .poll(async () => idsVigentes(api), { timeout: 20_000, intervals: [1_000] })
      .not.toContain(idVencido);

    const vigentes = await idsVigentes(api);
    expect(vigentes).toContain(idBanner);
    expect(vigentes).not.toContain(idFuturo);
    expect(vigentes).not.toContain(idVencido);
  });

  test('el jugador ve en Inicio solo el vigente, con su fecha de publicación y su vigencia', async ({
    page,
  }) => {
    await conSesion(page, jugador);
    const consulta = page.waitForResponse((r) => r.url().includes('/api/v1/banners/vigentes'));
    await page.goto(`${BORDE}${INICIO}`);
    expect((await consulta).status()).toBe(200);

    const mensaje = page.locator(`[data-componente="banner-rotativo"] [data-banner="${idBanner}"]`);
    await expect(mensaje).toBeAttached({ timeout: 20_000 });
    // Puede estar rotando con otros vigentes: el texto se lee aunque no sea
    // el que se ve en este instante.
    const texto = (await mensaje.textContent()) ?? '';
    expect(texto).toContain(contenidoEditado);
    expect(texto).toContain('Publicado el');
    expect(texto).toContain('Vigente hasta el');
    await expect(page.locator('[data-zona="bloque-banners"]')).toBeVisible();
    await expect(page.locator(`[data-banner="${idFuturo}"]`)).toHaveCount(0);
    await expect(page.locator(`[data-banner="${idVencido}"]`)).toHaveCount(0);
  });

  test('al retirarlo deja de estar vigente, la home ya no lo pinta y la lista lo marca retirado', async ({
    page,
  }) => {
    const retiro = await api.delete(`/api/v1/banners/${idBanner}`, {
      headers: conToken(admin.token),
    });
    expect(retiro.status(), await retiro.text()).toBe(204);

    expect(await idsVigentes(api)).not.toContain(idBanner);
    const lista = await api.get('/api/v1/banners', { headers: conToken(admin.token) });
    expect((await lista.json()).find((b) => b.id === idBanner)).toMatchObject({ retirado: true });

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
