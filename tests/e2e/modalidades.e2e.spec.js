/**
 * HU-SAL-004 (RF-JUE-004) — Modalidades de partida, contra servicios reales.
 *
 * Lo que se afirma, siempre como estado observable y nunca un `200` a secas:
 *
 *   - CA-01/CA-04: contra la IA la maquina va siempre y ocupa el segundo cupo:
 *     la sala nace LLENA, un segundo humano no entra (409 con el motivo), la
 *     partida tiene dos combatientes y uno es la IA... y se juega hasta el
 *     final, porque la maquina responde sola.
 *   - CA-04: fuera de limite se rechaza diciendo el limite (400, campo y
 *     mensaje): maquina en un duelo, mas maquinas que cupos.
 *   - CA-02/CA-03: hasta seis con equipos de dos y dos cupos de la IA: las
 *     maquinas ocupan cupo (la sala se llena con dos personas), la partida
 *     reparte los equipos por orden de entrada y la vista no ofrece atacar al
 *     companero.
 *   - SCRUM-1080: el formulario de creacion se acomoda a la modalidad y crea
 *     la sala contra la IA de verdad.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';

const CREAR = '/frontend/app-web/src/plataforma/salas-partidas/crear-sala.html';
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

async function conSesion(page, jugador, apodo) {
  await page.addInitScript(
    ([token, nombre]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
    },
    [jugador.token, apodo],
  );
}

async function crear(api, quien, cuerpo) {
  return api.post('/api/v1/salas', { headers: conToken(quien.token), data: cuerpo });
}

async function partidaDe(api, quien, id) {
  const r = await api.get(`/api/v1/partidas/${id}`, { headers: conToken(quien.token) });
  expect(r.status(), await r.text()).toBe(200);
  return r.json();
}

test.describe('Modalidades de partida (HU-SAL-004)', () => {
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

  // ===================================================================
  // CA-04 · fuera de limites se rechaza diciendo el limite
  // ===================================================================

  test('un duelo 1 contra 1 con maquina se rechaza: la modalidad para eso es contra la IA', async () => {
    const r = await crear(api, anfitriona, {
      maximoParticipantes: 2,
      modalidad: 'UNO_CONTRA_UNO',
      recompensaCreditos: 0,
      incluirHeroeIA: true,
    });

    expect(r.status(), await r.text()).toBe(400);
    const problema = await r.json();
    expect(problema.errores[0].campo).toBe('heroesIA');
    expect(problema.errores[0].mensaje).toMatch(/contra la IA/i);
  });

  test('mas maquinas que cupos libres: 400 con el limite exacto', async () => {
    const r = await crear(api, anfitriona, {
      maximoParticipantes: 4,
      modalidad: 'HASTA_SEIS',
      recompensaCreditos: 0,
      heroesIA: 4,
    });

    expect(r.status(), await r.text()).toBe(400);
    const problema = await r.json();
    expect(problema.errores[0].campo).toBe('heroesIA');
    expect(problema.errores[0].mensaje).toContain('como maximo 3');
  });

  test('contra la IA con siete cupos: el limite es dos, y se dice', async () => {
    const r = await crear(api, anfitriona, {
      maximoParticipantes: 7,
      modalidad: 'CONTRA_IA',
      recompensaCreditos: 0,
    });

    expect(r.status(), await r.text()).toBe(400);
    const problema = await r.json();
    expect(problema.errores.map((e) => e.campo)).toContain('maximoParticipantes');
    expect(problema.errores[0].mensaje).toContain('exactamente 2');
  });

  // ===================================================================
  // CA-02/CA-03 · hasta seis: maquinas que ocupan cupo y equipos
  // ===================================================================

  test('hasta seis con dos maquinas y equipos de dos: se llena con dos personas y los equipos salen por orden', async ({
    page,
  }) => {
    const creada = await crear(api, anfitriona, {
      maximoParticipantes: 4,
      modalidad: 'HASTA_SEIS',
      recompensaCreditos: 0,
      heroesIA: 2,
      tamanoEquipo: 2,
    });
    expect(creada.status(), await creada.text()).toBe(201);
    const sala = await creada.json();
    expect(sala.heroesIA).toBe(2);
    expect(sala.incluirHeroeIA).toBe(true);
    expect(sala.ocupacion, 'anfitriona + 2 maquinas').toBe(3);
    expect(sala.estado).toBe('ABIERTA');

    const entrada = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
    });
    expect(entrada.status(), await entrada.text()).toBe(200);
    const llena = await entrada.json();
    expect(llena.ocupacion).toBe(4);
    expect(llena.estado, 'las maquinas cuentan: con dos personas ya no cabe nadie').toBe('LLENA');

    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    const partida = await inicio.json();

    // Anfitriona y invitado en el equipo 1; las dos maquinas en el 2.
    expect(partida.participantes).toHaveLength(4);
    expect(partida.participantes.map((p) => p.equipo)).toEqual([1, 1, 2, 2]);
    expect(partida.participantes.map((p) => p.esIA)).toEqual([false, false, true, true]);
    expect(partida.participantes[0].jugador).toBe(anfitriona.claims.uid);
    expect(partida.participantes[1].jugador).toBe(invitado.claims.uid);

    // La vista de la anfitriona: dos botones, los dos contra maquinas. Al
    // companero no se le puede apuntar.
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-barra-vida]')).toHaveCount(4);
    const objetivos = await page
      .locator('[data-zona="acciones"] [data-atacar]')
      .evaluateAll((bs) => bs.map((b) => b.dataset.atacar));
    const maquinas = partida.participantes.filter((p) => p.esIA).map((p) => p.jugador);
    expect(objetivos.sort()).toEqual(maquinas.sort());
    expect(objetivos).not.toContain(invitado.claims.uid);
    await expect(
      page.locator(`[data-barra-vida][data-jugador="${invitado.claims.uid}"]`),
    ).toHaveAttribute('data-equipo', '1');
    await expect(page.locator(`[data-barra-vida][data-jugador="${maquinas[0]}"]`)).toHaveAttribute(
      'data-equipo',
      '2',
    );
  });

  // ===================================================================
  // CA-01 · contra la IA, desde el formulario hasta el final del combate
  // ===================================================================

  test('el formulario se acomoda a la modalidad y crea la sala contra la IA', async ({ page }) => {
    // El combate del final tarda lo que tarde la maquina en caer.
    test.setTimeout(180000);
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${CREAR}`);

    const participantes = page.locator('[name="maximoParticipantes"]');
    // Por defecto 1 contra 1: aforo fijo en 2, sin opciones de hasta seis.
    await expect(participantes).toHaveValue('2');
    await expect(participantes).toHaveAttribute('readonly', '');
    await expect(page.locator('[data-zona="opciones-hasta-seis"]')).toBeHidden();

    // El radio va transparente ENCIMA de su tarjeta (`.modalidad__entrada`:
    // opacity 0 al 100 %): es el que recibe el clic de una persona, y por eso
    // Playwright no deja pulsar la tarjeta «tapada». Se marca el radio.
    await page.locator('#modalidad-seis').check();
    await expect(participantes).toHaveAttribute('max', '6');
    await expect(page.locator('[data-zona="opciones-hasta-seis"]')).toBeVisible();

    await page.locator('#modalidad-ia').check();
    await expect(page.locator('[data-zona="nota-contra-ia"]')).toBeVisible();
    await expect(participantes).toHaveValue('2');

    const respuesta = page.waitForResponse(
      (r) => r.url().includes('/api/v1/salas') && r.request().method() === 'POST',
    );
    await page.click('[type="submit"]');
    const creada = await respuesta;
    expect(creada.status()).toBe(201);
    const sala = await creada.json();
    expect(sala.modalidad).toBe('CONTRA_IA');
    expect(sala.heroesIA).toBe(1);
    expect(sala.estado, 'la maquina ya ocupa el segundo cupo').toBe('LLENA');
    expect(sala.ocupacion).toBe(2);
    // R17 — creada la sala, la vista lleva a la anfitriona a su sala, donde
    // esta «Iniciar combate». Una sala contra la IA nace completa: sin esto
    // no habia forma de llegar a ella desde la interfaz.
    await page.waitForURL(new RegExp(`sala-batalla\\.html\\?sala=${sala.id}`), {
      timeout: 20000,
    });
    await expect(page.locator('[data-accion="iniciar-partida"]')).toBeVisible({ timeout: 20000 });

    // Un segundo humano no cabe: seria 2 contra la IA.
    const intruso = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
    });
    expect(intruso.status(), await intruso.text()).toBe(409);
    expect((await intruso.json()).detail).toMatch(/maximo de participantes/i);

    // Y se juega hasta el final: la anfitriona golpea desde la vista y la
    // maquina responde sola, turno tras turno, hasta que alguien cae.
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    let partida = await inicio.json();
    expect(partida.participantes.map((p) => p.esIA)).toEqual([false, true]);

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
      // Tras el golpe humano y la respuesta de la maquina, vuelve a tocar a la
      // anfitriona: la maquina nunca deja el turno colgado.
      if (partida.estado === 'EN_CURSO') {
        expect(partida.turnoActual.idJugador).toBe(anfitriona.claims.uid);
      }
      golpes += 1;
    }

    expect(partida.estado, `no termino en ${golpes} golpes`).toBe('FINALIZADA');
    await expect(page.locator('[data-zona="resultado"]')).toHaveText(
      /has ganado|has perdido|empate/i,
      {
        timeout: 20000,
      },
    );
  });
});
