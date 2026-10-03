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

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';

const CREAR = '/frontend/app-web/src/plataforma/salas-partidas/crear-sala.html';
const VISTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';

/**
 * B1 — la cuenta nace pendiente de verificar su correo. Registrar, leer el
 * codigo del buzon, confirmarlo y entrar viven en un solo sitio
 * (`ayudantes/cuentas.js`); aqui solo se fija la contrasena de este spec.
 */
function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
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
    expect(problema.errores[0].mensaje).toContain('como máximo 3');
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

    // Anfitriona y invitado en el equipo 1; las dos maquinas en el 2. Los
    // equipos salen del orden de ENTRADA; la lista de participantes viene en
    // el orden de los TURNOS, que desde B7 se sortea (§6.1.3). Por eso se
    // busca a cada uno por quien es, no por su posicion.
    expect(partida.participantes).toHaveLength(4);
    const equipoDe = (uid) => partida.participantes.find((p) => p.jugador === uid)?.equipo;
    expect(equipoDe(anfitriona.claims.uid)).toBe(1);
    expect(equipoDe(invitado.claims.uid)).toBe(1);
    const deLaMaquina = partida.participantes.filter((p) => p.esIA);
    expect(deLaMaquina).toHaveLength(2);
    expect(deLaMaquina.map((p) => p.equipo)).toEqual([2, 2]);
    // El turno es de alguien de la partida, persona o maquina.
    expect(partida.participantes.map((p) => p.jugador)).toContain(partida.turnoActual.idJugador);

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
    test.setTimeout(480000);
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
    await expect(page.locator('.aviso--exito')).toBeVisible();

    // Un segundo humano no cabe: seria 2 contra la IA.
    const intruso = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
    });
    expect(intruso.status(), await intruso.text()).toBe(409);
    expect((await intruso.json()).detail).toMatch(/máximo de participantes/i);

    // Y se juega hasta el final: la anfitriona golpea desde la vista y la
    // maquina responde sola, turno tras turno, hasta que alguien cae.
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    let partida = await inicio.json();
    // Una persona y una maquina, en el orden de turnos que haya salido del
    // sorteo (§6.1.3, B7): la maquina tambien puede abrir.
    expect(partida.participantes.map((p) => p.esIA).sort()).toEqual([false, true]);

    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    let golpes = 0;
    // B7: con las reglas del documento (Tablas 21-23, D-B7-01) un combate dura
    // mucho mas que el simplificado; el tope es de la prueba, no de la regla.
    while (partida.estado === 'EN_CURSO' && golpes < 150) {
      // Se espera el turno propio segun el SERVICIO antes de pulsar: si la
      // maquina abrio, juega sola y devuelve el turno. La maquina nunca deja el
      // turno colgado (mismo patron que recompensa-por-partida.e2e.spec.js).
      await expect
        .poll(
          async () => {
            partida = await partidaDe(api, anfitriona, partida.id);
            return (
              partida.estado !== 'EN_CURSO' ||
              partida.turnoActual.idJugador === anfitriona.claims.uid
            );
          },
          { timeout: 25000, message: `golpe ${golpes + 1}: la maquina no devuelve el turno` },
        )
        .toBe(true);
      if (partida.estado !== 'EN_CURSO') {
        break;
      }
      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20000 });
      const turnoPrevio = partida.turnoActual.numeroTurno;
      // Si justo en este instante la partida cambia, el clic no entra y lo ve
      // el sondeo de abajo (mismo patron que torneos.e2e.spec.js).
      await boton.click({ timeout: 5000 }).catch(() => {});
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
    await expect(page.locator('[data-zona="resultado"]')).toHaveText(
      /has ganado|has perdido|empate/i,
      {
        timeout: 20000,
      },
    );
  });
});
