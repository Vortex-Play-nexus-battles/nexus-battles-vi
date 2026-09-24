/**
 * HU-SAL-006 · Abandonar o cancelar una sala antes de que empiece.
 *
 * Con el navegador de verdad, contra los servicios de verdad: un participante
 * pulsa «Salir de la sala» y vuelve al listado con el aviso; la anfitriona
 * pulsa «Cancelar sala», confirma, y a quien estaba dentro lo devuelve el
 * canal STOMP con el motivo. Y lo que NO se ofrece: al anfitrion no se le
 * ofrece salir, al participante no se le ofrece cancelar, y con la partida
 * en curso no se ofrece nada.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';
const LISTADO = '/frontend/app-web/src/plataforma/salas-partidas/batallas.html';
// R17.3 — detrás del borde el listado vive en /jugar; la ruta antigua redirige
// allí. Se acepta cualquiera de las dos para no atar la prueba al borde.
const EN_EL_LISTADO = /\/jugar(?:[?#]|$)|batallas\.html/;

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  const cuerpo = await login.json();
  return { ...cuerpo, claims: cuerpoDelToken(cuerpo.token) };
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

/** Sala publica de cuatro, sin recompensa, con el invitado ya dentro. */
async function salaConLosDos(api, anfitriona, invitado) {
  const creada = await api.post('/api/v1/salas', {
    headers: conToken(anfitriona.token),
    data: {
      maximoParticipantes: 4,
      modalidad: 'HASTA_SEIS',
      recompensaCreditos: 0,
      privada: false,
    },
  });
  expect(creada.status(), `crear sala: ${await creada.text()}`).toBe(201);
  const sala = await creada.json();
  const entrada = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
    headers: conToken(invitado.token),
  });
  expect(entrada.status(), `entrar: ${await entrada.text()}`).toBe(200);
  return sala;
}

async function salaActual(api, quien, id) {
  const r = await api.get(`/api/v1/salas/${id}`, { headers: conToken(quien.token) });
  expect(r.status()).toBe(200);
  return r.json();
}

test.describe('Salir y cancelar una sala (HU-SAL-006)', () => {
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

  test('CA-01: el participante ve «Salir», sale, y vuelve al listado con el aviso', async ({
    page,
  }) => {
    const sala = await salaConLosDos(api, anfitriona, invitado);
    await conSesion(page, invitado, INVITADO);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}`);

    // Lo que se ofrece a un participante: salir, y no cancelar ni iniciar.
    const salir = page.locator('[data-accion="salir-de-sala"]');
    await expect(salir).toBeVisible({ timeout: 20000 });
    await expect(page.locator('[data-accion="cancelar-sala"]')).toBeHidden();
    await expect(page.locator('[data-accion="iniciar-partida"]')).toBeHidden();
    await expect(page.locator('[data-zona="ocupacion"]')).toHaveText('2 de 4 jugadores en la sala');

    await salir.click();

    await page.waitForURL(EN_EL_LISTADO, { timeout: 20000 });
    await expect(page.locator('[data-zona="aviso-sala"]')).toBeVisible();
    await expect(page.locator('[data-zona="aviso-sala-titulo"]')).toHaveText('Saliste de la sala.');
    // Y en el servidor ya no esta.
    const despues = await salaActual(api, anfitriona, sala.id);
    expect(despues.ocupacion).toBe(1);
    expect(despues.participantes).not.toContain(invitado.claims.uid);

    await api.delete(`/api/v1/salas/${sala.id}`, { headers: conToken(anfitriona.token) });
  });

  test('CA-02 y CA-05: la anfitriona cancela con confirmacion, y al invitado lo devuelve el canal con el motivo', async ({
    browser,
  }) => {
    const sala = await salaConLosDos(api, anfitriona, invitado);

    // Dos navegadores: el invitado espera en la sala, la anfitriona cancela.
    const contextoInvitado = await browser.newContext();
    const paginaInvitado = await contextoInvitado.newPage();
    await conSesion(paginaInvitado, invitado, INVITADO);
    await paginaInvitado.goto(`${BORDE}${VISTA}?sala=${sala.id}`);
    await expect(paginaInvitado.locator('[data-accion="salir-de-sala"]')).toBeVisible({
      timeout: 20000,
    });
    // Hasta que la sala de espera no escuche el canal, cancelar seria
    // adelantarse: el aviso llegaria a nadie.
    await expect(paginaInvitado.locator('[data-zona="espera"][data-canal="suscrito"]')).toHaveCount(
      1,
      { timeout: 20000 },
    );

    const contextoAnfitriona = await browser.newContext();
    const paginaAnfitriona = await contextoAnfitriona.newPage();
    await conSesion(paginaAnfitriona, anfitriona, ANFITRION);
    await paginaAnfitriona.goto(`${BORDE}${VISTA}?sala=${sala.id}`);

    const cancelar = paginaAnfitriona.locator('[data-accion="cancelar-sala"]');
    await expect(cancelar).toBeVisible({ timeout: 20000 });
    await expect(paginaAnfitriona.locator('[data-accion="salir-de-sala"]')).toBeHidden();
    await expect(paginaAnfitriona.locator('[data-accion="iniciar-partida"]')).toBeVisible();

    // CA-05: se pregunta, y el texto dice a cuantos se expulsa.
    let pregunta = null;
    paginaAnfitriona.once('dialog', async (dialogo) => {
      pregunta = dialogo.message();
      await dialogo.accept();
    });
    await cancelar.click();

    await paginaAnfitriona.waitForURL(EN_EL_LISTADO, { timeout: 20000 });
    expect(pregunta).toBe('¿Cancelar la sala? Se expulsará a 1 participante.');
    await expect(paginaAnfitriona.locator('[data-zona="aviso-sala-titulo"]')).toHaveText(
      'Cancelaste la sala.',
    );

    // CA-02: al invitado lo devuelve el aviso `sala.cancelada` del canal.
    await paginaInvitado.waitForURL(EN_EL_LISTADO, { timeout: 20000 });
    await expect(paginaInvitado.locator('[data-zona="aviso-sala-titulo"]')).toHaveText(
      'La sala se cerró',
    );
    await expect(paginaInvitado.locator('[data-zona="aviso-sala-detalle"]')).toHaveText(
      'El anfitrión canceló la sala.',
    );

    expect((await salaActual(api, anfitriona, sala.id)).estado).toBe('CANCELADA');

    await contextoInvitado.close();
    await contextoAnfitriona.close();
  });

  test('CA-05: si no se confirma, la sala sigue abierta', async ({ page }) => {
    const sala = await salaConLosDos(api, anfitriona, invitado);
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}`);
    const cancelar = page.locator('[data-accion="cancelar-sala"]');
    await expect(cancelar).toBeVisible({ timeout: 20000 });

    page.once('dialog', (dialogo) => dialogo.dismiss());
    await cancelar.click();

    await expect(cancelar).toBeEnabled();
    expect(page.url()).toContain('sala-batalla.html');
    expect((await salaActual(api, anfitriona, sala.id)).estado).toBe('ABIERTA');

    await api.delete(`/api/v1/salas/${sala.id}`, { headers: conToken(anfitriona.token) });
  });

  test('CA-03: con la partida en curso no se ofrece salir ni cancelar, y la API responde 409', async ({
    page,
  }) => {
    const sala = await salaConLosDos(api, anfitriona, invitado);
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), `iniciar: ${await inicio.text()}`).toBe(201);
    const partida = await inicio.json();

    await conSesion(page, invitado, INVITADO);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-zona="panel"]')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('[data-accion="salir-de-sala"]')).toBeHidden();
    await expect(page.locator('[data-accion="cancelar-sala"]')).toBeHidden();

    const salida = await api.delete(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
    });
    expect(salida.status()).toBe(409);
    const cancelacion = await api.delete(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(cancelacion.status()).toBe(409);
  });
});
