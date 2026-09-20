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
    expect(partida.participantes.map((p) => p.jugador)).toContain(partida.turnoActual.idJugador);

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

  /**
   * Vida de cada barra, leida del DOM.
   *
   * Se lee de `aria-valuenow` / `aria-valuemax` y no de `data-vida-actual`:
   * `barra-vida.js#actualizar` escribe los atributos ARIA y `data-estado`,
   * pero NO reescribe los `data-vida-*` —esos solo los lee `inicializar` para
   * el primer pintado—. Ademas ARIA es el contrato accesible de la barra, que
   * es lo que de verdad ve quien usa un lector de pantalla.
   */
  function barras(page) {
    return page.locator('[data-barra-vida]').evaluateAll((bs) =>
      bs.map((b) => ({
        jugador: b.dataset.jugador,
        actual: Number(b.getAttribute('aria-valuenow')),
        maxima: Number(b.getAttribute('aria-valuemax')),
        estado: b.dataset.estado,
        texto: b.querySelector('.barra-vida__valor')?.textContent ?? '',
      })),
    );
  }

  /** Suma de la vida en pantalla: baja en cuanto alguien recibe un golpe. */
  async function vidaTotal(page) {
    return (await barras(page)).reduce((t, b) => t + b.actual, 0);
  }

  test('el navegador abre la sala, se conecta al canal y pinta las dos barras a tope', async ({
    page,
  }) => {
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

    // Dos barras: una por participante. Que existan ya prueba que la vista
    // pidio la partida por el borde y la pinto.
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });

    const pintadas = await barras(page);

    for (const barra of pintadas) {
      // A plena vida la barra es verde: por encima del 60 %.
      expect(barra.estado, JSON.stringify(pintadas)).toBe('alto');
      expect(barra.actual, JSON.stringify(barra)).toBe(barra.maxima);
      // El color nunca es el unico indicador: el valor tambien se escribe.
      expect(barra.texto).toBe(`${barra.actual}/${barra.maxima}`);
    }

    // Y el canal esta vivo de verdad: la vista solo pinta «conectado» cuando
    // recibio una funcion de suscripcion, y eso solo pasa si el CONNECT de
    // STOMP con el JWT prospero.
    await expect(page.locator('[data-zona="conexion"]')).toHaveText(/conectado/i);
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

    // El defecto que esto cierra: los botones nacian deshabilitados y solo los
    // abria un `partida.turno.cambiado`, mensaje que el servidor unicamente
    // emite DESPUES de que alguien juegue. En el turno 1 nadie ha jugado, asi
    // que el combate no se podia arrancar desde el navegador.
    await expect(boton, 'a quien le toca no puede atacar en el turno 1').toBeEnabled();
    // Y solo hay boton para el rival: a uno mismo no se ataca.
    await expect(page.locator('[data-atacar]')).toHaveCount(1);

    const sumaAntes = await vidaTotal(page);

    await boton.click();

    // Lo que se espera NO es una respuesta HTTP: es que el servidor resuelva
    // la accion contra motor-combate, la persista y la anuncie por
    // `/tema/partidas/{id}`, y que la vista repinte con lo que llego.
    await expect
      .poll(() => vidaTotal(page), {
        timeout: 25000,
        message: 'la vida no cambio tras atacar',
      })
      .toBeLessThan(sumaAntes);

    // El golpe cayo sobre el rival, no sobre quien ataco.
    const despues = await barras(page);
    const rival = despues.find((b) => b.jugador !== quien.claims.uid);
    expect(rival.actual, JSON.stringify(despues)).toBeLessThan(rival.maxima);

    // Y el turno paso a la otra parte: el boton se cierra solo, por el aviso
    // `partida.turno.cambiado` que llego por el canal.
    await expect(boton).toBeDisabled({ timeout: 15000 });
  });

  test('la vida que se ve en pantalla es la que quedo guardada en la base', async ({ page }) => {
    // Que la barra baje puede ser cosa del navegador. Esto compara lo pintado
    // contra lo que devuelve el servidor al releer la partida.
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    await expect(page.locator('[data-barra-vida]')).toHaveCount(2, { timeout: 20000 });

    const enPantalla = (await barras(page)).map((b) => b.actual).sort((a, b) => a - b);

    const r = await api.get(`/api/v1/partidas/${partida.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(r.status()).toBe(200);
    const releida = await r.json();
    const enBase = releida.participantes.map((p) => p.heroe.vidaActual).sort((a, b) => a - b);

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

    const vistos = new Set(['alto']); // ya comprobado a plena vida, mas arriba
    const recorrido = [];
    let turnos = 0;

    while (turnos < 40 && !vistos.has('bajo')) {
      const enCurso = await (
        await api.get(`/api/v1/partidas/${partida.id}`, {
          headers: conToken(anfitriona.token),
        })
      ).json();
      if (enCurso.estado !== 'EN_CURSO') break;

      const esAnfitriona = enCurso.turnoActual.idJugador === anfitriona.claims.uid;
      const quien = esAnfitriona ? anfitriona : invitado;

      // Se recarga la vista con la sesion de quien tiene el turno. Recargar a
      // mitad de partida y poder seguir jugando es, en si mismo, lo que el
      // defecto de `turnoDe` rompia.
      await conSesion(page, quien, esAnfitriona ? ANFITRION : INVITADO);
      await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton, `turno ${turnos + 1}: el boton no se abrio`).toBeEnabled({
        timeout: 20000,
      });
      await boton.click();

      // Esperar al repintado en vez de dormir un rato fijo: el evento llega
      // por STOMP y tarda lo que tarde el motor.
      const objetivo = enCurso.participantes
        .filter((p) => p.jugador !== enCurso.turnoActual.idJugador)
        .map((p) => p.heroe.vidaActual)
        .reduce((a, b) => a + b, 0);
      await expect
        .poll(
          async () =>
            (await barras(page))
              .filter((b) => b.jugador !== enCurso.turnoActual.idJugador)
              .reduce((t, b) => t + b.actual, 0),
          { timeout: 25000, message: `turno ${turnos + 1}: la vida del rival no bajo` },
        )
        .toBeLessThan(objetivo);

      for (const barra of await barras(page)) {
        vistos.add(barra.estado);
        recorrido.push(`${barra.actual}/${barra.maxima}=${barra.estado}`);
      }
      turnos += 1;
    }

    // Los tres colores de RF-JUE-009, alcanzados con el daño que calculo el
    // motor de verdad: verde por encima del 60 %, amarillo entre 60 y 40, rojo
    // por debajo del 40 %.
    expect([...vistos].sort(), `recorrido en ${turnos} turnos: ${recorrido.join(' ')}`).toEqual([
      'alto',
      'bajo',
      'medio',
    ]);
  });

  test('a fuerza de golpes alguien cae, y la vista lo dice', async ({ page }) => {
    // HU-JUE-005 / RF-JUE-017: el combate acaba de verdad. Se sigue golpeando
    // desde donde lo dejo la prueba anterior hasta que la partida cierre.
    test.setTimeout(180000);

    let enCurso = await (
      await api.get(`/api/v1/partidas/${partida.id}`, {
        headers: conToken(anfitriona.token),
      })
    ).json();

    let turnos = 0;
    while (enCurso.estado === 'EN_CURSO' && turnos < 40) {
      const esAnfitriona = enCurso.turnoActual.idJugador === anfitriona.claims.uid;
      const quien = esAnfitriona ? anfitriona : invitado;

      await conSesion(page, quien, esAnfitriona ? ANFITRION : INVITADO);
      await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);

      const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
      await expect(boton).toBeEnabled({ timeout: 20000 });
      await boton.click();

      // El ultimo golpe cierra la partida: se espera a que el servidor lo
      // haya persistido, no a un tiempo fijo.
      await expect
        .poll(
          async () =>
            (
              await (
                await api.get(`/api/v1/partidas/${partida.id}`, {
                  headers: conToken(anfitriona.token),
                })
              ).json()
            ).turnoActual.numeroTurno,
          { timeout: 25000, message: `turno ${turnos + 1}: el turno no avanzo` },
        )
        .toBeGreaterThan(enCurso.turnoActual.numeroTurno);

      enCurso = await (
        await api.get(`/api/v1/partidas/${partida.id}`, {
          headers: conToken(anfitriona.token),
        })
      ).json();
      turnos += 1;
    }

    expect(enCurso.estado, `no termino en ${turnos} turnos`).toBe('FINALIZADA');

    // Alguien quedo en cero: no es un final por abandono ni por tiempo.
    const caidos = enCurso.participantes.filter((p) => p.heroe.vidaActual === 0);
    expect(caidos.length).toBeGreaterThanOrEqual(1);
    // Y su barra es la roja.
    const barrasFinales = await barras(page);
    for (const caido of caidos) {
      expect(barrasFinales.find((b) => b.jugador === caido.jugador)?.estado).toBe('bajo');
    }

    // La vista de quien dio el ultimo golpe cuenta el desenlace y retira los
    // botones: el aviso `partida.finalizada` llego por el canal.
    await expect(page.locator('[data-zona="resultado"]')).toHaveText(
      /has ganado|has perdido|empate/i,
      { timeout: 20000 },
    );
    await expect(page.locator('[data-zona="acciones"]')).toBeHidden();
  });
});
