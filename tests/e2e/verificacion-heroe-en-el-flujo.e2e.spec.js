/**
 * RF-JUE-003 · La verificacion de heroe esta en el camino — FI-R6.
 *
 * Con el navegador de verdad, contra los servicios de verdad. Lo que se
 * demuestra aqui NO es que el endpoint funcione —eso ya lo prueba
 * `sala-de-batalla.e2e.spec.js`— sino que la persona pasa por el, que es lo
 * que faltaba.
 *
 * ## El hueco que esto cierra
 *
 * `validacion-heroe.html` existia, funcionaba, estaba en la matriz de acceso
 * y **no la enlazaba nadie**: el unico sitio del repositorio que la nombraba
 * era la propia matriz de acceso. Del listado se iba directo a
 * `ingresarASala`, asi que:
 *
 * - quien no tenia heroe equipado recibia el 422 de `PuertaDeHeroe` pintado
 *   como un aviso rojo en el listado, sin ninguna salida;
 * - quien entraba a una sala con apuesta comprometia sus creditos sin ver
 *   antes con que heroe iba a jugar ni cuanto le iba a costar, aunque el
 *   dialogo tenia escrito ese texto desde el principio.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';
/** Cabe en lo que `sembrar.sh` acredita (E2E_SALDO_INICIAL, 500 por defecto). */
const APUESTA = 100;
const LISTADO = '/frontend/app-web/src/plataforma/salas-partidas/batallas.html';
const VERIFICACION = '/frontend/app-web/src/plataforma/salas-partidas/validacion-heroe.html';

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  return login.json();
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function conSesion(page, jugador, apodo) {
  await page.addInitScript(
    ([token, nombre]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
    },
    [jugador.token, apodo],
  );
}

async function salaConApuesta(api, anfitriona) {
  const creada = await api.post('/api/v1/salas', {
    headers: conToken(anfitriona.token),
    data: {
      maximoParticipantes: 4,
      modalidad: 'HASTA_SEIS',
      recompensaCreditos: APUESTA,
      privada: false,
    },
  });
  expect(creada.status(), `crear sala con apuesta: ${await creada.text()}`).toBe(201);
  return creada.json();
}

test.describe('La verificacion de heroe esta en el flujo (RF-JUE-003)', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('una sala con apuesta manda a confirmar antes de comprometer creditos', async ({ page }) => {
    const sala = await salaConApuesta(api, anfitriona);

    await conSesion(page, invitado, INVITADO);
    await page.goto(LISTADO);
    const tarjeta = page.locator(`[data-sala="${sala.id}"]`);
    await expect(tarjeta).toBeVisible();
    await tarjeta.click();

    // Se va a la verificacion, no a la sala.
    await page.waitForURL(new RegExp(`validacion-heroe\\.html\\?sala=${sala.id}`), {
      timeout: 20_000,
    });

    // Y la sala sigue con una sola persona dentro: no se reservo nada.
    const antes = await api.get(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect((await antes.json()).ocupacion).toBe(1);
  });

  test('el dialogo ensena el veredicto del servicio y cuanto va a costar', async ({ page }) => {
    const sala = await salaConApuesta(api, anfitriona);

    await conSesion(page, invitado, INVITADO);
    await page.goto(`${VERIFICACION}?sala=${sala.id}`);

    const dialogo = page.locator('#validacion-heroe');
    // El veredicto lo pone el servicio; la vista solo lo pinta.
    await expect(dialogo).toHaveAttribute('data-resultado', 'DISPONIBLE', { timeout: 20_000 });
    // El nombre del heroe que `inventario` reporto, no un circulo gris.
    await expect(dialogo.locator('.dialogo__encabezado')).toContainText(/\w/);
    // Y el coste, que es la razon de que este paso exista.
    await expect(dialogo).toContainText(new RegExp(`${APUESTA}`));
    await expect(dialogo.locator('[data-accion="confirmar"]')).toHaveText(/Entrar a la sala/i);
  });

  test('Confirmar entra de verdad a la sala', async ({ page }) => {
    // Es lo que este boton no hacia: escribia en la consola. UX-R4.5 lo
    // conecto y #648 lo borro sin querer; FI-R0 lo devolvio. Aqui queda
    // comprobado contra el servicio, no contra un doble.
    const sala = await salaConApuesta(api, anfitriona);

    await conSesion(page, invitado, INVITADO);
    await page.goto(`${VERIFICACION}?sala=${sala.id}`);

    const dialogo = page.locator('#validacion-heroe');
    await expect(dialogo).toHaveAttribute('data-resultado', 'DISPONIBLE', { timeout: 20_000 });
    await dialogo.locator('[data-accion="confirmar"]').click();

    await page.waitForURL(/sala-batalla\.html/, { timeout: 20_000 });

    const despues = await api.get(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect((await despues.json()).ocupacion, 'el invitado entro de verdad').toBe(2);
  });

  test('sin heroe equipado el listado lleva al dialogo, que dice como arreglarlo', async ({
    page,
  }) => {
    // Jugador nuevo: `sembrar.sh` no le puso inventario, asi que
    // `PuertaDeHeroe` va a cerrarse. Sala sin apuesta, para que el camino
    // probado sea el del 422 y no el de la confirmacion.
    const sinHeroe = await sesionDe(api, `sin_heroe_r6_${Date.now()}`);
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: {
        maximoParticipantes: 4,
        modalidad: 'HASTA_SEIS',
        recompensaCreditos: 0,
        privada: false,
      },
    });
    expect(creada.status()).toBe(201);
    const sala = await creada.json();

    await conSesion(page, sinHeroe, 'sin_heroe_r6');
    await page.goto(LISTADO);
    const tarjeta = page.locator(`[data-sala="${sala.id}"]`);
    await expect(tarjeta).toBeVisible();
    await tarjeta.click();

    await page.waitForURL(new RegExp(`validacion-heroe\\.html\\?sala=${sala.id}`), {
      timeout: 20_000,
    });

    const dialogo = page.locator('#validacion-heroe');
    await expect(dialogo).toHaveAttribute('data-resultado', 'SIN_HEROE_EQUIPADO', {
      timeout: 20_000,
    });
    // Lo que le faltaba al aviso rojo del listado: una salida.
    await expect(dialogo.locator('[data-accion="confirmar"]')).toHaveText(/Ir al inventario/i);
  });

  test('Cancelar devuelve al listado con el estado que tenia', async ({ page }) => {
    const sala = await salaConApuesta(api, anfitriona);

    await conSesion(page, invitado, INVITADO);
    await page.goto(LISTADO);
    await page.locator(`[data-sala="${sala.id}"]`).click();
    await page.waitForURL(/validacion-heroe\.html/, { timeout: 20_000 });

    await page.locator('[data-accion="cancelar"]').click();

    // Vuelve atras en el historial, no a un listado recien cargado: quien
    // tenia filtros puestos los conserva.
    await page.waitForURL(/batallas\.html/, { timeout: 20_000 });
    await expect(page.locator(`[data-sala="${sala.id}"]`)).toBeVisible();
  });

  test('recargar la verificacion vuelve a preguntar, no se queda en blanco', async ({ page }) => {
    const sala = await salaConApuesta(api, anfitriona);

    await conSesion(page, invitado, INVITADO);
    await page.goto(`${VERIFICACION}?sala=${sala.id}`);
    await expect(page.locator('#validacion-heroe')).toHaveAttribute(
      'data-resultado',
      'DISPONIBLE',
      { timeout: 20_000 },
    );

    await page.reload();

    await expect(page.locator('#validacion-heroe')).toHaveAttribute(
      'data-resultado',
      'DISPONIBLE',
      { timeout: 20_000 },
    );
  });

  test('un enlace sin sala no pide nada al servicio y ofrece una salida', async ({ page }) => {
    await conSesion(page, invitado, INVITADO);

    const peticiones = [];
    await page.route('**/api/v1/salas/**', (ruta) => {
      peticiones.push(ruta.request().url());
      return ruta.continue();
    });

    await page.goto(VERIFICACION);

    const dialogo = page.locator('#validacion-heroe');
    await expect(dialogo).toHaveAttribute('data-resultado', 'SIN_SALA', { timeout: 20_000 });
    // No se pide `/salas/null/verificacion-heroe`.
    expect(peticiones.filter((u) => u.includes('verificacion-heroe'))).toHaveLength(0);
    await expect(dialogo.locator('[data-accion="salida"]')).toHaveText(/Ver salas abiertas/i);
  });
});
