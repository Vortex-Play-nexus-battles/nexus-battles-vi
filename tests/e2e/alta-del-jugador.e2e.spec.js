/**
 * R17 · Un jugador nuevo, de punta a punta: se registra, el juego lo prepara
 * solo y puede moverse sin que la sesión le sorprenda.
 *
 * Contra los servicios de verdad del banco (`compose.yml`): la vista de
 * registro real, ms-identidad orquestando el alta (créditos en ms-finanzas,
 * héroe y equipo en inventario), el borde real con las direcciones limpias.
 * Nada simulado.
 *
 *   1. registro desde la vista → entra solo → «Preparando tu cuenta» con los
 *      pasos reales → lista, con los créditos que acreditó finanzas y el héroe
 *      equipado → «Empezar a jugar» lleva al inicio;
 *   2. el alta es idempotente: pedir otro intento no da más créditos;
 *   3. multipestaña: una pestaña nueva usa la sesión abierta; cerrar sesión
 *      cierra en las dos; «Atrás» no enseña la pantalla privada;
 *   4. una dirección privada sin sesión lleva al login y, al entrar, vuelve;
 *   5. fallo parcial: con el inventario caído el alta se queda a medias, la
 *      pantalla lo dice y ofrece reintentar, y al volver el inventario termina
 *      sin duplicar los créditos.
 *
 * Los jugadores son nuevos en cada corrida (apodo con marca de tiempo): el
 * alta solo ocurre una vez por cuenta.
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const CREDITOS_INICIALES = Number(process.env.E2E_CREDITOS_INICIALES ?? 500);
// Una contraseña que cumple la política (RF-AUT-002). Solo existe en el banco.
const CLAVE = 'Alta-Del-Jugador-2026';

const AQUI =
  typeof __dirname === 'undefined' ? path.resolve(process.cwd(), '../../tests/e2e') : __dirname;
const COMPOSE = path.join(AQUI, 'compose.yml');

const EN_PREPARANDO = /\/preparando(?:[?#]|$)|preparando\.html/;
const EN_INICIO = /\/inicio(?:[?#]|$)|index\.html/;
const EN_LOGIN = /\/login(?:[?#]|$)|login\.html/;
const EN_INVENTARIO = /\/inventario(?:[?#]|$)|inventario\.html/;
const EN_JUGAR = /\/jugar(?:[?#]|$)|batallas\.html/;

const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);

// ---------------------------------------------------------------- utilidades

function nuevoApodo(prefijo) {
  return `${prefijo}_${Date.now().toString(36)}${Math.floor(Math.random() * 1e4)}`;
}

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

function conToken(token) {
  return { Authorization: `Bearer ${token}` };
}

async function tokenDe(page) {
  return page.evaluate(() => sessionStorage.getItem('nexus.token'));
}

async function saldoDisponible(api, token) {
  const uid = cuerpoDelToken(token).uid;
  const r = await api.get(`${FINANZAS}/creditos/${uid}/saldo`, { headers: conToken(token) });
  expect(r.status(), `saldo de ${uid}: ${await r.text()}`).toBe(200);
  return Number((await r.json()).saldoDisponible);
}

/** El héroe inicial del jugador y lo que lleva equipado. */
async function heroeEquipado(api, token) {
  const elementos = await api.get('/api/v1/inventario/elementos?pagina=0', {
    headers: conToken(token),
  });
  expect(elementos.status(), await elementos.text()).toBe(200);
  const heroes = ((await elementos.json()).elementos ?? []).filter((e) => e.tipo === 'HEROE');
  if (heroes.length === 0) {
    return null;
  }
  const r = await api.get(`/api/v1/inventario/heroes/${heroes[0].id}/equipamiento`, {
    headers: conToken(token),
  });
  const equipo = await r.json();
  return {
    heroes: heroes.length,
    puestos: [
      ...(equipo.armas ?? []),
      ...(equipo.items ?? []),
      ...Object.values(equipo.armaduras ?? {}),
    ].length,
  };
}

/** axe sobre lo que se ve ahora mismo: sin hallazgos graves. */
async function sinBarrerasGraves(page, donde) {
  const resultado = await new AxeBuilder({ page }).withTags(NORMAS).analyze();
  const graves = resultado.violations.filter((v) => GRAVES.has(v.impact));
  expect(
    graves.map((v) => `${v.id} [${v.impact}] ${v.nodes.length}× — ${v.help}`),
    `axe en ${donde}`,
  ).toEqual([]);
}

async function registrarDesdeLaVista(page, { apodo, email }) {
  await page.goto(`${BORDE}/registro`);
  await page.fill('#nombres', 'Profesora');
  await page.fill('#apellidos', 'De Prueba');
  await page.fill('#apodo', apodo);
  await page.fill('#email', email);
  await page.fill('#password', CLAVE);
  await page.fill('#confirmarPassword', CLAVE);
  await page.click('#botonEnviar');
}

async function entrarDesdeLaVista(page, email) {
  await page.fill('#email', email);
  await page.fill('#password', CLAVE);
  await page.click('#botonEnviar');
}

// -------------------------------------------------------------- docker local

function compose(...args) {
  return execFileSync('docker', ['compose', '-f', COMPOSE, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

function hayComposeLocal() {
  if (!/localhost|127\.0\.0\.1/.test(BORDE)) {
    return false;
  }
  try {
    compose('ps', '--services');
    return true;
  } catch {
    return false;
  }
}

function estadoDe(servicio) {
  const id = compose('ps', '-a', '-q', servicio);
  if (!id) {
    return 'sin-contenedor';
  }
  return execFileSync(
    'docker',
    [
      'inspect',
      '--format',
      '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}-{{end}}',
      id,
    ],
    { encoding: 'utf8' },
  ).trim();
}

// ------------------------------------------------------------------- pruebas

test.describe('R17 · alta del jugador y sesión', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  const nuevo = { apodo: nuevoApodo('alta'), email: '' };
  nuevo.email = `${nuevo.apodo}@nexus.test`;
  let token;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · se registra desde la vista, entra solo y el juego lo prepara', async ({ page }) => {
    await registrarDesdeLaVista(page, nuevo);

    // Entra solo: sin volver a escribir correo y contraseña.
    await page.waitForURL(EN_PREPARANDO, { timeout: 20_000 });
    await expect(page.locator('[data-zona="pasos"] li')).toHaveCount(4);

    // Lista, con lo que de verdad le dieron.
    await expect(page.locator('[data-zona="titulo"]')).toHaveText('¡Tu cuenta está lista!', {
      timeout: 60_000,
    });
    await expect(page.locator('[data-zona="creditos-iniciales"]')).toHaveText(
      `${CREDITOS_INICIALES} créditos`,
    );
    await expect(page.locator('[data-zona="heroe-inicial"]')).toBeVisible();
    await expect(page.locator('[data-zona="pasos"] li[data-estado="HECHO"]')).toHaveCount(4);
    await sinBarrerasGraves(page, 'preparando (lista)');

    await page.click('[data-zona="empezar"]');
    await page.waitForURL(EN_INICIO, { timeout: 20_000 });

    // Lo mismo, preguntado a los dueños y no a la pantalla.
    token = await tokenDe(page);
    expect(token, 'la sesión quedó guardada').toBeTruthy();
    expect(await saldoDisponible(api, token)).toBe(CREDITOS_INICIALES);
    const heroe = await heroeEquipado(api, token);
    expect(heroe, 'el alta dejó un héroe en el inventario').not.toBeNull();
    expect(heroe.heroes).toBe(1);
    expect(heroe.puestos, 'el héroe inicial lleva su equipo').toBeGreaterThan(0);
  });

  test('2 · el alta es idempotente: otro intento no da más créditos ni otro héroe', async () => {
    const r = await api.post('/api/v1/auth/onboarding/reintentos', { headers: conToken(token) });
    expect(r.status()).toBe(202);
    expect((await r.json()).estado).toBe('COMPLETO');

    // Tiempo de sobra para que un reintento indebido hubiera hecho algo.
    await new Promise((resolver) => setTimeout(resolver, 3_000));
    expect(await saldoDisponible(api, token)).toBe(CREDITOS_INICIALES);
    expect((await heroeEquipado(api, token)).heroes).toBe(1);
  });

  test('3 · una pestaña nueva usa la sesión abierta; cerrar sesión cierra las dos', async ({
    context,
  }) => {
    const pestanaA = await context.newPage();
    await pestanaA.goto(`${BORDE}/login`);
    await entrarDesdeLaVista(pestanaA, nuevo.email);
    // La cuenta ya está lista: directo al inicio, sin pasar por la preparación.
    await pestanaA.waitForURL(EN_INICIO, { timeout: 20_000 });

    // Enlace directo en una pestaña nueva: no pide la contraseña otra vez.
    const pestanaB = await context.newPage();
    await pestanaB.goto(`${BORDE}/inventario`);
    await pestanaB.waitForURL(EN_INVENTARIO, { timeout: 20_000 });
    expect(await tokenDe(pestanaB)).toBeTruthy();

    // Cerrar sesión en A cierra también B.
    await pestanaA.click('[data-zona="cuenta"]');
    await pestanaA.click('[data-zona="cerrar-sesion"]');
    await pestanaA.waitForURL(/motivo=cerrada/, { timeout: 15_000 });
    await expect(pestanaA.locator('#avisoMotivo')).toContainText('Cerraste sesión');
    await pestanaB.waitForURL(/motivo=cerrada/, { timeout: 15_000 });
    expect(await tokenDe(pestanaB)).toBeNull();

    // «Atrás» no enseña la pantalla privada: vuelve a pedir la entrada.
    await pestanaA.goBack();
    await pestanaA.waitForURL(EN_LOGIN, { timeout: 15_000 });
    expect(await tokenDe(pestanaA)).toBeNull();
  });

  test('4 · una dirección privada sin sesión lleva al login y, al entrar, vuelve a ella', async ({
    browser,
  }) => {
    const contexto = await browser.newContext();
    const page = await contexto.newPage();
    await page.goto(`${BORDE}/jugar`);
    await page.waitForURL(EN_LOGIN, { timeout: 15_000 });
    expect(new URL(page.url()).searchParams.get('volver')).toMatch(/jugar|batallas\.html/);
    await sinBarrerasGraves(page, 'login');

    await entrarDesdeLaVista(page, nuevo.email);
    await page.waitForURL(EN_JUGAR, { timeout: 20_000 });
    await contexto.close();
  });

  test.describe('fallo parcial', () => {
    test.skip(
      !hayComposeLocal(),
      'el fallo parcial apaga el inventario: hace falta el compose local de tests/e2e',
    );

    test.afterAll(async () => {
      // Pase lo que pase, el inventario queda encendido Y SANO para lo que
      // viene: las pruebas siguientes cuentan con él desde su primera línea.
      if (!hayComposeLocal()) {
        return;
      }
      if (!/^running\//.test(estadoDe('srv-inventario'))) {
        compose('start', 'srv-inventario');
      }
      for (let i = 0; i < 90 && estadoDe('srv-inventario') !== 'running/healthy'; i++) {
        await new Promise((resolver) => setTimeout(resolver, 2_000));
      }
    });

    test('5 · con el inventario caído la preparación lo dice, y al volver termina sin duplicar', async ({
      page,
    }) => {
      test.setTimeout(360_000);
      const cuenta = { apodo: nuevoApodo('parcial'), email: '' };
      cuenta.email = `${cuenta.apodo}@nexus.test`;

      compose('stop', '-t', '5', 'srv-inventario');
      expect(estadoDe('srv-inventario')).toMatch(/^exited\//);

      await registrarDesdeLaVista(page, cuenta);
      await page.waitForURL(EN_PREPARANDO, { timeout: 20_000 });

      // Los créditos no dependen del inventario: salen; el héroe no.
      await expect(page.locator('[data-zona="aviso-titulo"]')).toHaveText(
        'Algo no salió a la primera',
        { timeout: 60_000 },
      );
      await expect(page.locator('li[data-paso="CREDITOS"]')).toHaveAttribute(
        'data-estado',
        'HECHO',
      );
      await expect(page.locator('li[data-paso="HEROE"]')).toHaveAttribute('data-estado', 'ERROR');
      await expect(page.locator('li[data-paso="HEROE"] .alta-paso__motivo')).not.toBeEmpty();
      await expect(page.locator('[data-zona="reintentar"]')).toBeVisible();
      await expect(page.locator('[data-zona="continuar"]')).toBeVisible();
      await sinBarrerasGraves(page, 'preparando (con error)');

      // Vuelve el inventario y se pide otro intento desde la pantalla. El
      // reintento automático puede ganarle al botón (el banco reintenta cada
      // pocos segundos): si ya no está, no se pulsa, y la prueba sigue igual.
      compose('start', 'srv-inventario');
      await expect
        .poll(() => estadoDe('srv-inventario'), { timeout: 180_000, intervals: [2_000] })
        .toBe('running/healthy');
      await page
        .locator('[data-zona="reintentar"]')
        .click({ timeout: 3_000 })
        .catch(() => {});

      await expect(page.locator('[data-zona="titulo"]')).toHaveText('¡Tu cuenta está lista!', {
        timeout: 90_000,
      });

      // Idempotente: los créditos se acreditaron UNA vez, aunque el alta se
      // intentó varias; y hay un solo héroe, con su equipo.
      const tokenParcial = await tokenDe(page);
      expect(await saldoDisponible(api, tokenParcial)).toBe(CREDITOS_INICIALES);
      const heroe = await heroeEquipado(api, tokenParcial);
      expect(heroe.heroes).toBe(1);
      expect(heroe.puestos).toBeGreaterThan(0);
    });
  });
});
