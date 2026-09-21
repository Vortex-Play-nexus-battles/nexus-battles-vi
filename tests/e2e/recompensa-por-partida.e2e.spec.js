// @ts-check
/**
 * Recompensa por jugar — HU-JUE-012 (RF-JUE-012), de punta a punta.
 *
 * Una partida contra la maquina SIN apuesta, jugada hasta el final desde la
 * vista real: al terminar, salas-partidas informa el resultado a ms-finanzas
 * con su credencial de servicio y el libro acredita 2 si la anfitriona gano
 * (uno contra uno) o 1 si perdio (participacion). Se comprueba en el saldo
 * bruto, en la operacion del libro (CA-01) y en el texto de la vista.
 *
 * CA-05 (idempotencia) se comprueba contra el libro real: volver a informar
 * la misma partida con un token de servicio responde 409
 * `partida-ya-procesada`, y el saldo no se mueve.
 *
 * No se prueba aqui: CA-04 (sancionados) — exige una sancion activa real en
 * moderacion-sanciones, que se cubre en las pruebas del servicio
 * (`AcreditarRecompensaTest`); y el reintento con el libro caido, que ya
 * tiene su prueba con contenedores apagados en `degradacion.e2e.spec.js`
 * para la apuesta y sigue el mismo mecanismo.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const CLAVE = 'Contrasena-E2E-2026';
// Credencial de servicio del banco de pruebas (tests/e2e/compose.yml, ADR-005).
const BANCO = { id: 'e2e-banco', secreto: 'e2e-secreto-del-banco-de-pruebas' };

const VISTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';

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

async function tokenDeServicio(api) {
  const r = await api.post('/api/v1/auth/token', {
    headers: {
      Authorization: `Basic ${Buffer.from(`${BANCO.id}:${BANCO.secreto}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    data: 'grant_type=client_credentials',
  });
  expect(r.status(), `token de servicio: ${await r.text()}`).toBe(200);
  return (await r.json()).access_token;
}

async function saldoBrutoDe(api, quien) {
  const r = await api.get(`${FINANZAS}/creditos/${quien.claims.uid}/saldo`, {
    headers: conToken(quien.token),
  });
  expect(r.status(), await r.text()).toBe(200);
  return Number((await r.json()).saldoBruto);
}

/** La operacion del libro por su referencia (solo servicios: /creditos/** es ROLE_SERVICIO). */
async function operacionDe(api, servicio, refId) {
  const r = await api.get(`${FINANZAS}/creditos/operaciones/${refId}`, {
    headers: conToken(servicio),
  });
  expect(r.status(), `operacion ${refId}: ${await r.text()}`).toBe(200);
  return r.json();
}

async function partidaDe(api, quien, id) {
  const r = await api.get(`/api/v1/partidas/${id}`, { headers: conToken(quien.token) });
  expect(r.status(), await r.text()).toBe(200);
  return r.json();
}

test.describe('Recompensa por jugar (HU-JUE-012)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;
  const salas = [];

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
  });

  test.afterAll(async () => {
    for (const id of salas) {
      await api.delete(`/api/v1/salas/${id}`, { headers: conToken(anfitriona.token) });
    }
    await api.dispose();
  });

  test('al terminar una partida contra la IA, el libro acredita 2 por ganar o 1 por participar, y la vista lo dice', async ({
    page,
  }) => {
    test.setTimeout(180000);

    const antes = await saldoBrutoDe(api, anfitriona);

    // Sala contra la maquina y sin apuesta: lo unico que puede mover el saldo
    // es la recompensa por jugar.
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: { modalidad: 'CONTRA_IA', maximoParticipantes: 2, recompensaCreditos: 0, heroesIA: 1 },
    });
    expect(creada.status(), await creada.text()).toBe(201);
    const sala = await creada.json();
    salas.push(sala.id);

    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    let partida = await inicio.json();

    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [anfitriona.token, ANFITRION],
    );
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

    let golpes = 0;
    while (partida.estado === 'EN_CURSO' && golpes < 30) {
      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20000 });
      const turnoPrevio = partida.turnoActual.numeroTurno;
      await boton.click();
      await expect
        .poll(
          async () => {
            partida = await partidaDe(api, anfitriona, partida.id);
            return partida.estado !== 'EN_CURSO' || partida.turnoActual.numeroTurno > turnoPrevio;
          },
          { timeout: 25000, message: `golpe ${golpes + 1}: ni rota el turno ni acaba` },
        )
        .toBe(true);
      golpes += 1;
    }
    expect(partida.estado, `no termino en ${golpes} golpes`).toBe('FINALIZADA');

    const humana = partida.participantes.find((p) => !p.esIA);
    const gano = humana.heroe.vidaActual > 0;
    const esperado = gano ? 2 : 1;

    // CA-01: el saldo bruto sube exactamente lo de la ficha (uno contra uno).
    await expect
      .poll(async () => (await saldoBrutoDe(api, anfitriona)) - antes, {
        timeout: 20000,
        message: 'el libro no acredito la recompensa',
      })
      .toBe(esperado);

    // ...y el libro tiene la operacion con la referencia a la partida y el
    // concepto de la ficha. (El «Historial de transacciones» del jugador solo
    // lista pagos, no movimientos de creditos: defecto aparte para Cuentas.)
    const servicio = await tokenDeServicio(api);
    const refId = `partida-${partida.id}-jugador-${anfitriona.claims.uid}`;
    const operacion = await operacionDe(api, servicio, refId);
    expect(Number(operacion.monto)).toBe(esperado);
    expect(operacion.concepto).toBe(gano ? 'recompensa-victoria' : 'recompensa-participacion');

    // La vista lo dice con la coletilla de HU-JUE-012 (contrato 1.4.0).
    await expect(page.locator('[data-zona="resultado"]')).toHaveText(
      gano
        ? /has ganado.*ganas 2 creditos por ganar/i
        : /has perdido.*ganas 1 credito por participar/i,
      { timeout: 20000 },
    );

    // CA-05: informar otra vez la misma partida no acredita dos veces.
    const repetida = await api.post(`${FINANZAS}/partidas/resultado`, {
      headers: conToken(servicio),
      data: {
        partidaId: partida.id,
        tipoPartida: 'UNO_A_UNO',
        ganadoresUid: gano ? [anfitriona.claims.uid] : [],
        participantes: [{ uid: anfitriona.claims.uid, sancionado: false }],
      },
    });
    expect(repetida.status(), await repetida.text()).toBe(409);
    expect((await repetida.json()).type).toMatch(/partida-ya-procesada$/);
    expect((await saldoBrutoDe(api, anfitriona)) - antes).toBe(esperado);
  });

  test('#455 sigue en pie: un jugador no puede informar resultados con su propio token', async () => {
    const r = await api.post(`${FINANZAS}/partidas/resultado`, {
      headers: conToken(anfitriona.token),
      data: {
        partidaId: 'inventada',
        tipoPartida: 'UNO_A_UNO',
        participantes: [{ uid: anfitriona.claims.uid, sancionado: false }],
      },
    });
    expect(r.status()).toBe(403);
  });
});
