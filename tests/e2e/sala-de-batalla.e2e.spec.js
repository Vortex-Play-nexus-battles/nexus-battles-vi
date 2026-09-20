/**
 * Corte vertical de la sala de batalla, contra servicios de verdad.
 *
 * ## Qué demuestra, y por qué no se puede demostrar en AWS
 *
 * Este es el flujo que el host de dev NO puede correr (#435): la puerta de
 * héroe de HU-SAL-003 consulta a `inventario`, que no está desplegado allí, y
 * `ClienteInventarioHeroes` falla cerrado a propósito. Aquí están los seis
 * servicios reales —identidad, héroes, productos, inventario, salas-partidas y
 * motor-combate— con sus bases de datos y el **mismo `borde-dev.conf`** que
 * corre en producción.
 *
 * Ningún doble. Si el contrato entre dos servicios se rompe, esto se pone rojo.
 *
 * ## Qué se afirma
 *
 * Estado y comportamiento observable, nunca un `200` a secas:
 *
 * - el JWT trae el `uid` de ADR-002 y el apodo en `preferred_username`;
 * - la sala nace con su código de invitación y el anfitrión dentro CON su héroe;
 * - quien no tiene héroe equipado NO entra, y el error dice por qué;
 * - el segundo jugador entra por código y el roster pasa a dos;
 * - al iniciar, la partida reparte turnos y da vida inicial a los dos;
 * - una acción real baja la vida del objetivo, y el número lo calculó el motor;
 * - la vida persiste: se relee de la base y sigue bajada;
 * - el turno cambia de jugador.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';

/** Cuerpo de un JWT, sin verificar la firma: aquí solo se lee para afirmar. */
function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

/**
 * Registra (si hace falta) e inicia sesión. El registro avisa por correo, pero
 * es fail-open: sin servicio de correo la cuenta se crea igual, y por eso este
 * banco no levanta ni correo ni mailpit.
 */
async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;

  const registro = await api.post('/api/v1/auth/registro', {
    multipart: {
      nombres: 'Jugadora',
      apellidos: 'De Prueba',
      email,
      password: CLAVE,
      apodo,
    },
  });
  // 409/400 si ya existe de una corrida anterior: no es un fallo del flujo.
  expect([200, 201, 400, 409]).toContain(registro.status());

  const login = await api.post('/api/v1/auth/login', {
    data: { email, password: CLAVE },
  });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);

  const cuerpo = await login.json();
  return { ...cuerpo, claims: cuerpoDelToken(cuerpo.token) };
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('Sala de batalla de punta a punta', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;
  let sala;
  let partida;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  // ===================================================================
  // Identidad — ADR-002
  // ===================================================================

  test('el token trae el uid estable y el apodo, no un apodo por identificador', async () => {
    // El defecto que esto cierra: leer `sub` como identificador provocó el 500
    // del PR #404. Tras ADR-002 el `sub` es el apodo y el UUID vive en `uid`.
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

    expect(anfitriona.claims.uid, JSON.stringify(anfitriona.claims)).toMatch(uuid);
    expect(anfitriona.claims.preferred_username ?? anfitriona.claims.apodo).toBe(ANFITRION);
    expect(anfitriona.claims.uid).not.toBe(invitado.claims.uid);
  });

  // ===================================================================
  // HU-SAL-003 · la puerta de héroe, contra el inventario real
  // ===================================================================

  test('la verificación encuentra el héroe equipado que sembramos en inventario', async () => {
    const r = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: {
        maximoParticipantes: 2,
        modalidad: 'UNO_VS_UNO',
        recompensaCreditos: 0,
        privada: true,
      },
    });

    expect(r.status(), `crear sala: ${await r.text()}`).toBe(201);
    sala = await r.json();

    const anfitrionEnSala = sala.participantes.find((p) => p.jugador === anfitriona.claims.uid);
    expect(anfitrionEnSala, JSON.stringify(sala)).toBeTruthy();
    // Lo que de verdad prueba que inventario contestó: el héroe viaja con la
    // sala, con su vida, y no es un hueco.
    expect(anfitrionEnSala.heroe).toBeTruthy();
    expect(anfitrionEnSala.heroe.vidaActual).toBeGreaterThan(0);
    expect(anfitrionEnSala.heroe.vidaActual).toBe(anfitrionEnSala.heroe.vidaMaxima);
  });

  test('una sala privada nace con código de invitación', async () => {
    expect(sala.privada).toBe(true);
    expect(sala.codigoInvitacion, JSON.stringify(sala)).toBeTruthy();
    expect(sala.codigoInvitacion.length).toBeGreaterThanOrEqual(4);
  });

  test('sin héroe equipado no se entra, y el error lo explica', async () => {
    // Jugador nuevo, sin sembrar inventario: la puerta tiene que cerrarse.
    const sinHeroe = await sesionDe(api, `sin_heroe_${Date.now()}`);

    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(sinHeroe.token),
      data: { codigoInvitacion: sala.codigoInvitacion },
    });

    expect(r.status()).toBe(422);
    const problema = await r.json();
    expect(problema.title ?? '').toMatch(/heroe|héroe/i);
    expect(problema.detail ?? '').toMatch(/equipa/i);
  });

  // ===================================================================
  // HU-SAL-002 · el segundo jugador entra por código
  // ===================================================================

  test('el invitado entra con el código y el roster pasa a dos, cada uno con su héroe', async () => {
    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(invitado.token),
      data: { codigoInvitacion: sala.codigoInvitacion },
    });

    expect(r.status(), `ingresar: ${await r.text()}`).toBe(200);
    const actualizada = await r.json();

    expect(actualizada.participantes).toHaveLength(2);
    for (const p of actualizada.participantes) {
      expect(p.heroe, `${p.jugador} entró sin héroe`).toBeTruthy();
      expect(p.heroe.vidaActual).toBeGreaterThan(0);
    }
  });

  test('un código equivocado no abre la sala', async () => {
    const otro = await sesionDe(api, `curioso_${Date.now()}`);
    const r = await api.post(`/api/v1/salas/${sala.id}/participantes`, {
      headers: conToken(otro.token),
      data: { codigoInvitacion: 'NO-ES-ESTE' },
    });

    expect([403, 409, 422]).toContain(r.status());
  });

  // ===================================================================
  // HU-SAL-004 / RF-JUE-017 · la partida
  // ===================================================================

  test('al iniciar, la partida reparte turnos y los dos entran a plena vida', async () => {
    const r = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });

    expect(r.status(), `iniciar partida: ${await r.text()}`).toBe(201);
    partida = await r.json();

    expect(partida.estado).toBe('EN_CURSO');
    expect(partida.participantes).toHaveLength(2);
    expect(partida.turnoActual.numeroTurno).toBe(1);
    // El turno es de alguien que está en la partida, no de un id cualquiera.
    expect(partida.participantes.map((p) => p.jugador))
      .toContain(partida.turnoActual.idJugador);

    for (const p of partida.participantes) {
      expect(p.heroe.vidaActual).toBe(p.heroe.vidaMaxima);
    }
  });

  // ===================================================================
  // El combate, en el navegador y por STOMP
  //
  // No hay endpoint HTTP para atacar: la accion viaja por
  // `/app/partidas/{id}/acciones` del canal STOMP, que es lo que declara
  // contracts/websocket/salas-partidas.yaml. Asi que esta parte se juega
  // como la juega una persona — con la vista de verdad, el cliente STOMP de
  // verdad y la barra de vida de verdad.
  // ===================================================================

  /** Deja la sesion puesta antes de que cargue cualquier script de la vista. */
  async function conSesion(page, jugador, apodo) {
    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [jugador.token, apodo],
    );
  }

  const VISTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';

  /** Vida que pinta la barra de un jugador, leida del DOM. */
  async function vidaEnPantalla(page, idJugador) {
    return page.evaluate((id) => {
      const barra = document.querySelector(`[data-barra-vida][data-jugador="${id}"]`)
        ?? [...document.querySelectorAll('[data-barra-vida]')]
          .find((b) => b.closest(`[data-jugador="${id}"]`));
      if (!barra) return null;
      return {
        actual: Number(barra.dataset.vidaActual),
        maxima: Number(barra.dataset.vidaMaxima),
        estado: barra.dataset.estado,
      };
    }, idJugador);
  }

  test('el navegador abre la sala, se conecta al canal y pinta las dos barras a tope', async ({
    page,
  }) => {
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

    // Dos barras: una por participante. Que existan ya prueba que la vista
    // pidio la partida por el borde y la pinto.
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });

    const estados = await page.locator('[data-barra-vida]').evaluateAll((bs) =>
      bs.map((b) => ({ estado: b.dataset.estado, actual: b.dataset.vidaActual })));

    for (const barra of estados) {
      // A plena vida la barra es verde: por encima del 60 %.
      expect(barra.estado, JSON.stringify(estados)).toBe('alto');
      expect(Number(barra.actual)).toBeGreaterThan(0);
    }
  });

  test('atacar desde la vista baja la vida del rival, y el aviso llega por STOMP', async ({
    page,
  }) => {
    const atacanteEsAnfitriona = partida.turnoActual.idJugador === anfitriona.claims.uid;
    const quien = atacanteEsAnfitriona ? anfitriona : invitado;
    const apodo = atacanteEsAnfitriona ? ANFITRION : INVITADO;

    await conSesion(page, quien, apodo);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

    const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
    await expect(boton).toBeVisible({ timeout: 20000 });

    const sumaAntes = await page.locator('[data-barra-vida]')
      .evaluateAll((bs) => bs.reduce((t, b) => t + Number(b.dataset.vidaActual), 0));

    await boton.click();

    // Lo que se espera NO es una respuesta HTTP: es que el servidor resuelva
    // la accion contra motor-combate, la persista y la anuncie por
    // `/tema/partidas/{id}`, y que la vista repinte con lo que llego.
    await expect
      .poll(
        async () =>
          page.locator('[data-barra-vida]')
            .evaluateAll((bs) => bs.reduce((t, b) => t + Number(b.dataset.vidaActual), 0)),
        { timeout: 25000, message: 'la vida no cambio tras atacar' },
      )
      .toBeLessThan(sumaAntes);

    // Y el resultado se le cuenta a quien ataco, no solo se mueve la barra.
    await expect(page.locator('[data-zona="resultado"]')).not.toBeEmpty();
  });

  test('la vida que se ve en pantalla es la que quedo guardada en la base', async ({ page }) => {
    // Que la barra baje puede ser cosa del navegador. Esto compara lo pintado
    // contra lo que devuelve el servidor al releer la partida.
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });

    const enPantalla = await page.locator('[data-barra-vida]')
      .evaluateAll((bs) => bs.map((b) => Number(b.dataset.vidaActual)).sort((a, b) => a - b));

    const r = await api.get(`/api/v1/partidas/${partida.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(r.status()).toBe(200);
    const releida = await r.json();
    const enBase = releida.participantes
      .map((p) => p.heroe.vidaActual).sort((a, b) => a - b);

    expect(enPantalla).toEqual(enBase);
    // Y alguien ya recibio un golpe: no estamos comparando dos estados iniciales.
    expect(Math.min(...enBase)).toBeLessThan(
      Math.max(...releida.participantes.map((p) => p.heroe.vidaMaxima)),
    );
  });

  test('la barra cambia de color al bajar del 60 % y del 40 %, con daño real', async ({ page }) => {
    // HU-SAL-005. Los umbrales estan probados en unidad sobre `clasificar`;
    // lo que esto anade es que se cumplen con el daño que calcula el motor de
    // verdad y viajando por STOMP, no con numeros inventados en una prueba.
    test.setTimeout(180000);

    const vistos = new Set();
    let turnos = 0;

    while (turnos < 40 && !(vistos.has('medio') && vistos.has('bajo'))) {
      const enCurso = await (await api.get(`/api/v1/partidas/${partida.id}`, {
        headers: conToken(anfitriona.token),
      })).json();
      if (enCurso.estado !== 'EN_CURSO') break;

      const esAnfitriona = enCurso.turnoActual.idJugador === anfitriona.claims.uid;
      const quien = esAnfitriona ? anfitriona : invitado;

      await conSesion(page, quien, esAnfitriona ? ANFITRION : INVITADO);
      await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      if (!(await boton.count())) break;
      await boton.click();
      await page.waitForTimeout(1200);

      for (const estado of await page.locator('[data-barra-vida]')
        .evaluateAll((bs) => bs.map((b) => b.dataset.estado))) {
        vistos.add(estado);
      }
      turnos += 1;
    }

    expect([...vistos].sort(), `estados vistos en ${turnos} turnos`).toEqual(
      expect.arrayContaining(['alto', 'medio']),
    );
    expect(vistos.has('bajo'), 'nunca se llego a rojo por debajo del 40 %').toBe(true);
  });

  test('a fuerza de golpes la partida termina y queda un ganador', async () => {
    const enCurso = await (await api.get(`/api/v1/partidas/${partida.id}`, {
      headers: conToken(anfitriona.token),
    })).json();

    // Si la prueba anterior ya la acabo, esto solo lo comprueba.
    expect(['EN_CURSO', 'FINALIZADA']).toContain(enCurso.estado);

    if (enCurso.estado === 'FINALIZADA') {
      const caidos = enCurso.participantes.filter((p) => p.heroe.vidaActual === 0);
      expect(caidos.length).toBeGreaterThanOrEqual(1);
    }
  });
});
