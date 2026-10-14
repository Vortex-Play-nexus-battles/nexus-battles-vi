/**
 * Mi inventario de punta a punta en AWS DEV — revisión final del módulo
 * Jugador / Inventario (§7.1 y §7.1.1 del Documento Oficial, 13-oct).
 *
 * Una jugadora recién registrada (cuenta de QA reproducible: `canario_inv…`,
 * `@nexus.test`, verificada con el código de su buzón, sin SQL), contra los
 * servicios desplegados y por el borde público, como entra una persona:
 *
 *   API (lo que la interfaz no puede demostrar)
 *     - cada pestaña pide su página (`categoria=HEROES|OBJETOS`, 1.7.0);
 *     - el inventario ajeno no se toca (renombrar o retirar: 403/404) y el
 *       jugador no acuña elementos (alta: 403);
 *     - la respuesta no publica datos del dueño (correo, uid, propietario).
 *   Interfaz
 *     - login → «Mi inventario» desde la barra (§7.1.1) → héroe → su ficha;
 *     - objetos: dieciséis como mucho, la casilla «1» de §7.1, sin IDs técnicos;
 *     - búsqueda de cuatro letras o más sobre lo que dice el catálogo;
 *     - detalle con efectos, promedio, control de calificación, hilo; opinión
 *       con imagen, con apodo y fecha, y retirada de la opinión propia;
 *     - equipamiento: quitar una pieza, cerrar sesión, volver a entrar y que
 *       siga quitada (persistencia); devolverla; estadísticas a la vista;
 *     - axe sin hallazgos serios ni críticos.
 *
 * ## Lo que NO se hace aquí, a propósito
 *
 * No se envía una calificación: se queda para siempre en el promedio público
 * de un producto real de DEV, y una cuenta de QA no es una opinión de nadie
 * (no se inventan datos). Calificar una vez y el promedio se prueban en el
 * banco E2E (`comentarios.e2e.spec.js`, servicios reales y aislados). Aquí se
 * comprueba que el control se ofrece. La opinión de prueba sí se publica,
 * porque el recorrido la retira al final (eliminar la propia es requisito).
 */

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe } from './ayudantes/cuentas.js';
import { conLaPaginaQuieta } from './ayudantes/movimiento.js';
import { servicioNoDesplegadoDe } from './ayudantes/no-desplegados.js';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-Canario-2026';
const MARCA = Date.now().toString(36);

const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

/** §7.1.1 — la barra del jugador: el inventario y las secciones a las que lleva. */
const BARRA_DEL_JUGADOR = [
  { id: 'jugar', etiqueta: 'Jugar online', ruta: /\/jugar(?:[?#]|$)/ },
  { id: 'misiones', etiqueta: 'Misiones', ruta: /\/misiones(?:[?#]|$)/ },
  { id: 'torneo', etiqueta: 'Torneo', ruta: /\/torneos(?:[?#]|$)/ },
  { id: 'inventario', etiqueta: 'Mi inventario', ruta: /\/inventario(?:[?#]|$)/ },
  { id: 'subasta', etiqueta: 'Subasta', ruta: /\/subastas(?:[?#]|$)/ },
  { id: 'cuenta', etiqueta: 'Mi Cuenta', ruta: /\/cuenta(?:[?#]|$)/ },
];

/** Un PNG real de 3×2 (el mismo que usan las pruebas del servicio de comentarios). */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAHElEQVR42mNkYGBQZTBVZTBlYYg2ZWAwZWAwBQAPAgG01W6s7gAAAABJRU5ErkJggg==',
  'base64',
);

function conToken(token) {
  return { Authorization: `Bearer ${token}`, Accept: 'application/json' };
}

/** Espera a que el alta del jugador termine (héroe inicial y su equipo). */
async function esperarAlta(api, token) {
  const tope = Date.now() + 120_000;
  for (;;) {
    const r = await api.get('/api/v1/auth/onboarding', { headers: conToken(token) });
    const estado = r.ok() ? (await r.json()).estado : null;
    if (estado === 'COMPLETO' || estado === 'NO_APLICA') {
      return estado;
    }
    if (Date.now() > tope) {
      throw new Error(`el alta no terminó: ${estado ?? r.status()}`);
    }
    await new Promise((resolver) => setTimeout(resolver, 3_000));
  }
}

async function pagina(api, token, categoria, numero = 0) {
  const r = await api.get(`/api/v1/inventario/elementos?pagina=${numero}&categoria=${categoria}`, {
    headers: conToken(token),
  });
  expect(r.status(), await r.text()).toBe(200);
  return r.json();
}

async function sinBarrerasGraves(page, donde) {
  await conLaPaginaQuieta(page);
  const { violations } = await new AxeBuilder({ page }).withTags(NORMAS).analyze();
  const graves = violations.filter((v) => GRAVES.has(v.impact));
  expect(
    graves.map(
      (v) => `${v.id} [${v.impact}] ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`,
    ),
    `axe en ${donde}`,
  ).toEqual([]);
}

async function capturar(page, testInfo, nombre) {
  const ruta = testInfo.outputPath(`${nombre}.png`);
  await page.screenshot({ path: ruta });
  await testInfo.attach(nombre, { path: ruta, contentType: 'image/png' });
}

async function entrar(page, cuenta) {
  await page.goto('/login');
  await page.fill('#email', cuenta.email);
  const clave = page.locator('#password');
  await clave.focus();
  await clave.evaluate((el, valor) => {
    el.value = valor;
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }, CLAVE);
  await page.click('#botonEnviar');
  await page.waitForURL(/\/(inicio|preparando)(?:[?#]|$)/, { timeout: 30_000 });
  if (/\/preparando/.test(page.url())) {
    await expect(page.locator('[data-zona="titulo"]')).toHaveText('¡Tu cuenta está lista!', {
      timeout: 90_000,
    });
    await page.click('[data-zona="empezar"]');
    await page.waitForURL(/\/inicio(?:[?#]|$)/, { timeout: 30_000 });
  }
}

/**
 * Va a «Mi inventario» desde la barra del juego, como una persona (§7.1.1).
 * El canario corre en escritorio: la barra está desplegada. Se espera a que la
 * vista esté montada (sus pestañas) y no solo a que no haya «Cargando»: justo
 * después de navegar todavía no hay nada que cargue.
 */
async function irAlInventario(page) {
  const enlace = page.locator('header[data-cabecera-app] a[data-seccion="inventario"]');
  await expect(enlace).toBeVisible({ timeout: 30_000 });
  await enlace.click();
  await expect(page).toHaveURL(/\/inventario(?:[?#]|$)/);
  await expect(page.getByRole('tablist', { name: 'Secciones de Mi inventario' })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.locator('.estado-carga')).toHaveCount(0, { timeout: 30_000 });
}

async function abrirEquipo(page, nombreHeroe) {
  await page.locator('#pestana-heroes').click();
  await page
    .getByRole('button', { name: new RegExp(`^(Equipamiento de|Equiparlo:) ${nombreHeroe}$`) })
    .click();
  await expect(page.locator('.inventario-equipo')).toBeVisible();
  await expect(page.locator('.inventario-equipo .ranura')).toHaveCount(10, { timeout: 30_000 });
}

test.describe('Mi inventario en AWS DEV (revisión final §7.1)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let jugadora;
  let otra;
  let heroe;

  test.beforeAll(async () => {
    test.setTimeout(300_000);
    api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
    jugadora = await sesionDe(api, `canario_inv${MARCA}`, {
      clave: CLAVE,
      nombres: 'Canario',
      base: AWS,
    });
    otra = await sesionDe(api, `canario_inb${MARCA}`, {
      clave: CLAVE,
      nombres: 'Canario',
      base: AWS,
    });
    await esperarAlta(api, jugadora.token);
    await esperarAlta(api, otra.token);
    const heroes = await pagina(api, jugadora.token, 'HEROES');
    [heroe] = heroes.elementos;
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('API: cada pestaña su página, sin datos del dueño, sin tocar lo ajeno ni acuñar', async () => {
    const heroes = await pagina(api, jugadora.token, 'HEROES');
    const objetos = await pagina(api, jugadora.token, 'OBJETOS');
    expect(heroes.elementos.length, 'el alta deja un héroe').toBeGreaterThanOrEqual(1);
    expect(heroes.elementos.every((e) => e.tipo === 'HEROE')).toBe(true);
    expect(objetos.elementos.every((e) => e.tipo !== 'HEROE')).toBe(true);
    expect(objetos.tamanio).toBe(16);

    const texto = JSON.stringify([heroes, objetos]);
    expect(texto).not.toContain(jugadora.email);
    expect(texto).not.toContain(jugadora.claims.uid);
    expect(texto).not.toMatch(/"propietario/);

    const invalida = await api.get('/api/v1/inventario/elementos?pagina=0&categoria=TODO', {
      headers: conToken(jugadora.token),
    });
    expect(invalida.status()).toBe(400);
    const corta = await api.get('/api/v1/inventario/elementos/busqueda?criterio=abc&pagina=0', {
      headers: conToken(jugadora.token),
    });
    expect(corta.status()).toBe(400);

    // RNF-REN-003: la búsqueda indexada, medida desde fuera (borde incluido).
    // Se informa siempre; el tope aquí es holgado porque mide también la red
    // del runner, no solo el servicio.
    // Su nombre propio, o el tipo («héroe», también buscable) si es corto.
    const criterio = heroe.nombrePropio.length >= 4 ? heroe.nombrePropio.slice(0, 6) : 'heroe';
    const inicio = Date.now();
    const busqueda = await api.get(
      `/api/v1/inventario/elementos/busqueda?criterio=${encodeURIComponent(criterio)}&pagina=0`,
      { headers: conToken(jugadora.token) },
    );
    const milisegundos = Date.now() - inicio;
    expect(busqueda.status(), await busqueda.text()).toBe(200);
    expect((await busqueda.json()).elementos.map((e) => e.id)).toContain(heroe.id);
    console.log(`INVENTARIO-INFO|busqueda-ms|${milisegundos}`);
    expect(milisegundos).toBeLessThan(2_000);

    // Lo de la jugadora, visto por otra cuenta: ni se renombra ni se retira.
    const suyo = heroe.id;
    const renombrar = await api.patch(`/api/v1/inventario/elementos/${suyo}`, {
      headers: { ...conToken(otra.token), 'Content-Type': 'application/json' },
      data: { nombrePropio: 'No es mío' },
    });
    expect([403, 404], await renombrar.text()).toContain(renombrar.status());
    const retirar = await api.delete(`/api/v1/inventario/elementos/${suyo}`, {
      headers: conToken(otra.token),
    });
    expect([403, 404], await retirar.text()).toContain(retirar.status());
    const equipo = await api.get(`/api/v1/inventario/heroes/${suyo}/equipamiento`, {
      headers: conToken(otra.token),
    });
    expect([403, 404], await equipo.text()).toContain(equipo.status());

    // Un jugador no da de alta elementos (§7.3.1): ni para sí mismo.
    const acunar = await api.post('/api/v1/inventario/elementos', {
      headers: { ...conToken(jugadora.token), 'Content-Type': 'application/json' },
      data: { productoId: heroe.productoId, tipo: 'HEROE', nombrePropio: 'Gratis' },
    });
    expect(acunar.status(), await acunar.text()).toBe(403);

    // Y nada de lo anterior cambió su inventario.
    const despues = await pagina(api, jugadora.token, 'HEROES');
    expect(despues.totalElementos).toBe(heroes.totalElementos);
    expect(despues.elementos[0].nombrePropio).toBe(heroe.nombrePropio);
  });

  test('§7.1.1: desde el inventario la barra lleva a cada sección del jugador', async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    // Solo se comprueba el acceso: lo que cada sección hace es de su módulo.
    const fallos = [];
    page.on('response', (respuesta) => {
      const ruta = new URL(respuesta.url()).pathname;
      if (
        ruta.startsWith('/api/') &&
        respuesta.status() >= 500 &&
        servicioNoDesplegadoDe(ruta) === null
      ) {
        fallos.push(`${respuesta.status()} ${ruta}`);
      }
    });
    await entrar(page, jugadora);
    await irAlInventario(page);
    const visitadas = [];
    for (const seccion of BARRA_DEL_JUGADOR) {
      const enlace = page.locator(`header[data-cabecera-app] a[data-seccion="${seccion.id}"]`);
      // Se espera a la barra de la vista recién abierta (no se mira un instante).
      await expect(enlace).toBeVisible({ timeout: 30_000 });
      await expect(enlace).toContainText(seccion.etiqueta);
      await enlace.click();
      await expect(page).toHaveURL(seccion.ruta, { timeout: 30_000 });
      await expect(
        page.locator(`header[data-cabecera-app] a[data-seccion="${seccion.id}"]`),
      ).toHaveAttribute('aria-current', 'page');
      await conLaPaginaQuieta(page);
      visitadas.push(seccion.etiqueta);
    }
    await capturar(page, testInfo, 'inv-07-barra-mi-cuenta');
    console.log(`INVENTARIO-INFO|barra|${visitadas.join(' → ')}`);
    expect(fallos, 'ninguna petición respondió 5xx').toEqual([]);
  });

  test('interfaz: del login al equipo, con búsqueda, detalle, opinión y persistencia', async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const consultas = [];
    page.on('request', (peticion) => {
      const url = new URL(peticion.url());
      if (url.pathname.startsWith('/api/v1/inventario/elementos')) {
        consultas.push(`${url.pathname}${url.search}`);
      }
    });

    await entrar(page, jugadora);
    await irAlInventario(page);
    await expect(
      page.locator('header[data-cabecera-app] a[data-seccion="inventario"]'),
    ).toHaveAttribute('aria-current', 'page');
    expect(consultas.some((c) => /categoria=HEROES/.test(c))).toBe(true);

    // Héroes: la carta abre su ficha; Escape devuelve el foco a «Ver ficha».
    const carta = page.locator('.inventario__panel--heroes .hero-card').first();
    await expect(carta).toBeVisible({ timeout: 30_000 });
    await carta.locator('.hero-card__nombre').click();
    // La ficha (el diálogo de confirmar también es `role="dialog"`).
    const dialogo = page.locator('[role="dialog"].ficha');
    await expect(dialogo).toBeVisible();
    await expect(dialogo.locator('[data-zona="en-tu-inventario"]')).toBeVisible();
    await capturar(page, testInfo, 'inv-01-ficha-del-heroe');
    await page.keyboard.press('Escape');
    await expect(dialogo).toHaveCount(0);
    await expect(
      carta.getByRole('button', { name: `Ver ficha de ${heroe.nombrePropio}` }),
    ).toBeFocused();

    // Objetos: como mucho dieciséis, la casilla «1» y ningún identificador.
    await page.locator('#pestana-objetos').click();
    const panel = page.locator('.inventario__panel--objetos');
    const tarjetas = panel.locator('.vitrina__producto');
    await expect(tarjetas.first()).toBeVisible({ timeout: 30_000 });
    expect(await tarjetas.count()).toBeLessThanOrEqual(16);
    expect(consultas.some((c) => /categoria=OBJETOS/.test(c))).toBe(true);
    await expect(panel.locator('.paginacion__pagina[aria-current="page"]')).toHaveText('1');
    expect(await panel.innerText()).not.toMatch(UUID);
    // Las imágenes de las tarjetas llegan (ninguna rota).
    await expect
      .poll(
        () =>
          panel.evaluate((nodo) =>
            [...nodo.querySelectorAll('img')]
              .filter((i) => i.getBoundingClientRect().width > 0)
              .every((i) => i.complete && i.naturalWidth > 0),
          ),
        { message: 'las imágenes de la vitrina cargan', timeout: 20_000 },
      )
      .toBe(true);
    await sinBarrerasGraves(page, 'Mi inventario · Objetos');
    await capturar(page, testInfo, 'inv-02-objetos');

    // Búsqueda: con tres letras no se consulta; con una palabra del catálogo
    // (no del nombre propio) aparece el objeto.
    const objetos = await pagina(api, jugadora.token, 'OBJETOS');
    const buscado = objetos.elementos[0];
    const producto = await (await api.get(`/api/v1/productos/${buscado.productoId}`)).json();
    const palabras = String(producto.descripcion ?? '')
      .split(/[^\p{L}]+/u)
      .filter((p) => p.length >= 5);
    const palabra =
      palabras.find((p) => !buscado.nombrePropio.toLowerCase().includes(p.toLowerCase())) ??
      buscado.nombrePropio.slice(0, 6);
    const campo = page.locator('.inventario-busqueda [name="criterio"]');
    const antes = consultas.length;
    await campo.fill(palabra.slice(0, 3));
    await page.locator('.inventario-busqueda__buscar').click();
    await expect(page.locator('.inventario__mensaje')).toContainText(/cuatro caracteres/i);
    expect(consultas.slice(antes).some((c) => c.includes('/busqueda'))).toBe(false);
    await campo.fill(palabra);
    await page.locator('.inventario-busqueda__buscar').click();
    await expect(page.locator('.inventario__mensaje')).toContainText(/resultado/, {
      timeout: 30_000,
    });
    await expect(
      panel.locator('.vitrina__nombre', { hasText: buscado.nombrePropio }).first(),
    ).toBeVisible();
    await capturar(page, testInfo, 'inv-03-busqueda');
    await page.locator('.inventario-busqueda__limpiar').click();
    await expect(campo).toHaveValue('');

    // Detalle del objeto: un clic en la tarjeta.
    const tarjeta = tarjetas.filter({ hasText: buscado.nombrePropio }).first();
    await tarjeta.locator('.vitrina__nombre').click();
    await expect(dialogo).toBeVisible();
    await expect(dialogo.locator('.ficha__nombre')).not.toBeEmpty();
    await expect(dialogo.locator('.ficha__imagen')).toBeVisible();
    await expect(dialogo.locator('[data-zona="en-tu-inventario"]')).toBeVisible();
    // Promedio y control de calificación (sin enviar: ver la cabecera).
    // El promedio en cinco estrellas («N valoraciones» o «sin valoraciones»).
    await expect(dialogo.locator('.resumen-calificacion').first()).toBeVisible({
      timeout: 30_000,
    });
    await expect(dialogo.locator('.calificar-producto').first()).toBeVisible();

    // Opinión con imagen: apodo y fecha; después se retira (la propia).
    const texto = `Opinión de prueba del canario ${MARCA}`;
    await dialogo.getByLabel('Tu opinión').fill(texto);
    await dialogo
      .locator('.redactor-comentario__archivo')
      .setInputFiles({ name: 'prueba.png', mimeType: 'image/png', buffer: PNG });
    await expect(dialogo.locator('.redactor-comentario__miniaturas li')).toHaveCount(1);
    await dialogo.locator('[data-accion="publicar-opinion"]').click();
    const opinion = dialogo.locator('article.comentario', { hasText: texto });
    await expect(opinion).toBeVisible({ timeout: 30_000 });
    await expect(opinion.locator('.comentario__apodo')).toHaveText(jugadora.apodo);
    await expect(opinion.locator('time.comentario__fecha')).not.toBeEmpty();
    await expect(opinion.locator('img.comentario__imagen')).toBeVisible({ timeout: 30_000 });
    await capturar(page, testInfo, 'inv-04-opinion-con-imagen');
    await opinion.locator('[data-accion="eliminar-comentario"]').click();
    await page
      .locator('[role="dialog"]', { hasText: 'Eliminar tu comentario' })
      .getByRole('button', { name: 'Eliminar', exact: true })
      .click();
    await expect(dialogo.locator('article.comentario', { hasText: texto })).toHaveCount(0, {
      timeout: 30_000,
    });
    await page.keyboard.press('Escape');
    await expect(dialogo).toHaveCount(0);

    // Equipamiento: quitar una pieza, salir, volver y que siga quitada.
    await abrirEquipo(page, heroe.nombrePropio);
    await expect(page.locator('.inventario-equipo__estadisticas')).toBeVisible({
      timeout: 30_000,
    });
    const ocupada = page
      .locator('.inventario-equipo .ranura:not(.ranura--vacia):not(.ranura--bloqueada)')
      .first();
    await expect(ocupada).toBeVisible();
    const etiqueta = await ocupada.getAttribute('data-ranura');
    const pieza = (await ocupada.locator('.ranura__etiqueta').textContent()).trim();
    await ocupada.locator('.ranura__caja').click();
    const ranura = page.locator(`.inventario-equipo .ranura[data-ranura="${etiqueta}"]`);
    await expect(ranura).toHaveClass(/ranura--vacia/, { timeout: 30_000 });
    await capturar(page, testInfo, 'inv-05-pieza-quitada');

    await page.locator('header[data-cabecera-app] [data-zona="cuenta"]').click();
    await page.locator('[data-zona="menu-cuenta"] [data-zona="cerrar-sesion"]').click();
    await page.waitForURL(/\/login\?.*motivo=cerrada/, { timeout: 20_000 });

    await entrar(page, jugadora);
    await irAlInventario(page);
    await abrirEquipo(page, heroe.nombrePropio);
    await expect(ranura).toHaveClass(/ranura--vacia/);

    // Se devuelve la pieza a su sitio y se ve en la ranura.
    await ranura.locator('.ranura__caja').click();
    await page.locator('.elegir-objeto__opcion', { hasText: pieza }).first().click();
    await expect(ranura.locator('.ranura__etiqueta')).toHaveText(pieza, { timeout: 30_000 });
    await expect(page.locator('.inventario-equipo__estadisticas')).toBeVisible();
    await capturar(page, testInfo, 'inv-06-equipo-restablecido');
    await sinBarrerasGraves(page, 'Mi inventario · Equipamiento');
  });
});
