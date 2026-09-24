/**
 * HU-DIS-003 · Degradacion controlada ante la caida de un microservicio,
 * con INYECCION DE FALLOS DE VERDAD (SCRUM-1148): se apagan contenedores del
 * `compose.yml` de este directorio con `docker compose stop` y se vuelven a
 * encender, y lo que se afirma es lo que ve el jugador y lo que contesta el
 * servicio mientras tanto.
 *
 *   - CP-01: con el inventario apagado la aplicacion sigue operativa: el
 *     listado de salas responde, el catalogo de heroes responde, el borde
 *     responde. No hay caida total.
 *   - CP-02: quien intenta crear una sala recibe el aviso explicito de la
 *     funcion limitada: 503 `seccion-no-disponible` con `seccion` y
 *     `Retry-After` por la API, y «Inventario no disponible temporalmente» con
 *     un boton de reintentar en la vista, sin pantalla en blanco.
 *   - CP-03: los demas modulos siguen respondiendo con normalidad.
 *   - Recuperacion: al encender el inventario, el corta circuitos deja pasar
 *     su llamada de prueba y la sala se crea SIN reiniciar nada.
 *   - Durante una partida: se apaga el motor de combate a mitad de combate; la
 *     accion vuelve rechazada por la cola privada, la vista pinta «Motor de
 *     combate no disponible temporalmente» sobre los controles (que siguen
 *     ahi: el turno sigue siendo del jugador), y al encender el motor,
 *     Reintentar resuelve el golpe.
 *
 * Los umbrales del corta circuitos van acortados en el compose
 * (`RESILIENCIA_FALLOS_PARA_ABRIR=2`, `RESILIENCIA_REINTENTAR_EN_SEGUNDOS=2`)
 * para observar la recuperacion sin esperar medio minuto.
 *
 * Deja todo encendido PASE LO QUE PASE (`afterAll`): las pruebas que vienen
 * despues en orden alfabetico cuentan con los dos servicios.
 */

import { execFileSync } from 'node:child_process';
import path from 'node:path';

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const CLAVE = 'Contrasena-E2E-2026';
// Playwright transpila estos specs a CommonJS (no hay package.json con
// "type": "module" en tests/), asi que `import.meta` no existe aqui:
// `__dirname` si. Si algun dia se ejecutan como ESM, se cae al directorio
// desde el que se lanza Playwright (`frontend/app-web`).
const AQUI =
  typeof __dirname === 'undefined' ? path.resolve(process.cwd(), '../../tests/e2e') : __dirname;
const COMPOSE = path.join(AQUI, 'compose.yml');

const CREAR = '/frontend/app-web/src/plataforma/salas-partidas/crear-sala.html';
const BATALLAS = '/frontend/app-web/src/plataforma/salas-partidas/batallas.html';
const VISTA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';

const TIPO_SECCION_NO_DISPONIBLE = 'https://nexusbattles.local/errores/seccion-no-disponible';

// ---------------------------------------------------------------- docker

function compose(...args) {
  return execFileSync('docker', ['compose', '-f', COMPOSE, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

/**
 * Solo tiene sentido contra el compose local: apagar contenedores de un
 * entorno remoto no es esta prueba. Sin docker, se omite con el motivo.
 */
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

/** `running/healthy`, `exited/healthy`, `running/starting`... */
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

function apagar(servicio) {
  compose('stop', '-t', '5', servicio);
  expect(estadoDe(servicio), `${servicio} debia quedar apagado`).toMatch(/^exited\//);
}

async function encender(servicio) {
  compose('start', servicio);
  await expect
    .poll(() => estadoDe(servicio), {
      timeout: 180_000,
      intervals: [2_000],
      message: `${servicio} no volvio a estar sano tras encenderlo`,
    })
    .toBe('running/healthy');
}

// ------------------------------------------------------------------ api

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

const SALA_SIN_APUESTA = {
  maximoParticipantes: 4,
  modalidad: 'HASTA_SEIS',
  recompensaCreditos: 0,
  privada: false,
};

async function crear(api, quien, cuerpo = SALA_SIN_APUESTA) {
  return api.post('/api/v1/salas', { headers: conToken(quien.token), data: cuerpo });
}

async function partidaDe(api, quien, id) {
  const r = await api.get(`/api/v1/partidas/${id}`, { headers: conToken(quien.token) });
  expect(r.status(), await r.text()).toBe(200);
  return r.json();
}

// ---------------------------------------------------------------- pruebas

test.describe('Degradacion controlada con inyeccion de fallos (HU-DIS-003)', () => {
  test.describe.configure({ mode: 'serial' });
  test.skip(
    !hayComposeLocal(),
    'la inyeccion de fallos apaga contenedores: hace falta el compose local de tests/e2e',
  );

  let api;
  let anfitriona;
  /** Sala creada ANTES de la caida: la prueba de que el listado sigue. */
  let salaPrevia;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    const previa = await crear(api, anfitriona);
    expect(previa.status(), `sala previa: ${await previa.text()}`).toBe(201);
    salaPrevia = await previa.json();
  });

  test.afterAll(async () => {
    // Todo encendido, siempre: el resto de la suite lo necesita.
    for (const servicio of ['srv-inventario', 'srv-motor-combate']) {
      try {
        await encender(servicio);
      } catch (error) {
        console.warn(`No se pudo dejar ${servicio} encendido:`, error?.message);
      }
    }
    if (salaPrevia) {
      await api.delete(`/api/v1/salas/${salaPrevia.id}`, { headers: conToken(anfitriona.token) });
    }
    await api?.dispose();
  });

  test('inventario apagado: 503 con la seccion y Retry-After, el resto sigue, y al encenderlo se recupera solo', async ({
    page,
  }) => {
    test.setTimeout(300_000);
    apagar('srv-inventario');

    // ---- CP-02 por la API: aviso explicito, no un 500 ni un cuerpo vacio.
    const primera = await crear(api, anfitriona);
    expect(primera.status(), await primera.text()).toBe(503);
    const problema = await primera.json();
    expect(problema.type).toBe(TIPO_SECCION_NO_DISPONIBLE);
    expect(problema.seccion).toBe('Inventario');
    expect(problema.title).toBe('Inventario no disponible temporalmente');
    expect(problema.detail).toContain('El resto del juego sigue funcionando');
    expect(problema.reintentarEnSegundos).toBe(2);
    expect(primera.headers()['retry-after']).toBe('2');

    // Segundo fallo seguido: el circuito se abre; la respuesta es la misma.
    const segunda = await crear(api, anfitriona);
    expect(segunda.status()).toBe(503);
    expect((await segunda.json()).type).toBe(TIPO_SECCION_NO_DISPONIBLE);

    // ---- CP-01 y CP-03: nada mas se cayo.
    const listado = await api.get('/api/v1/salas', { headers: conToken(anfitriona.token) });
    expect(listado.status(), 'el listado no depende del inventario').toBe(200);
    expect((await listado.json()).contenido.map((s) => s.id)).toContain(salaPrevia.id);
    const catalogo = await api.get('/api/v1/heroes/Guerrero%20Tanque');
    expect(catalogo.status(), 'el catalogo de heroes es otro modulo y sigue').toBe(200);
    const borde = await api.get('/salud-borde');
    expect(borde.status()).toBe(200);

    // ---- CP-02 en la vista: «Seccion degradada», no una pantalla en blanco.
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${CREAR}`);
    await page.click('[type="submit"]');
    const degradada = page.locator('[data-zona="degradacion"] .seccion-degradada');
    await expect(degradada).toBeVisible();
    await expect(degradada).toContainText('Inventario no disponible temporalmente');
    await expect(degradada).toContainText('El resto del juego sigue funcionando');
    await expect(degradada).toHaveAttribute('role', 'status');
    await expect(page.locator('.aviso--error')).toHaveCount(0);
    // El formulario sigue ahi, con lo que la persona eligio, y se puede reintentar.
    await expect(page.locator('[name="maximoParticipantes"]')).toHaveValue('2');
    await expect(page.locator('[type="submit"]')).toBeEnabled();

    // ---- CP-03 en la vista: el listado de batallas se pinta con normalidad.
    const listadoPagina = await page.context().newPage();
    await conSesion(listadoPagina, anfitriona, ANFITRION);
    await listadoPagina.goto(`${BORDE}${BATALLAS}`);
    await expect(listadoPagina.locator(`[data-sala="${salaPrevia.id}"]`)).toBeVisible();
    await listadoPagina.close();

    // ---- Recuperacion: el inventario vuelve, el corta circuitos deja pasar
    // la llamada de prueba, y Reintentar crea la sala sin recargar nada.
    await encender('srv-inventario');
    await expect
      .poll(
        async () => {
          const intento = await crear(api, anfitriona);
          if (intento.status() === 201) {
            const sala = await intento.json();
            await api.delete(`/api/v1/salas/${sala.id}`, { headers: conToken(anfitriona.token) });
          }
          return intento.status();
        },
        {
          timeout: 60_000,
          intervals: [2_500],
          message: 'el servicio no se recupero al volver el inventario',
        },
      )
      .toBe(201);

    const respuesta = page.waitForResponse(
      (r) => r.url().includes('/api/v1/salas') && r.request().method() === 'POST',
    );
    await degradada.locator('.seccion-degradada__reintentar').click();
    expect((await respuesta).status()).toBe(201);
    // R17 — creada la sala, la vista lleva a la anfitriona a ella: el
    // reintento termina donde termina crear a la primera, en la sala de espera.
    await page.waitForURL(/sala-batalla\.html\?sala=/, { timeout: 20000 });
    await expect(page.locator('[data-accion="iniciar-partida"]')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.seccion-degradada')).toHaveCount(0);
  });

  test('motor de combate apagado a mitad de partida: la accion se rechaza con el aviso, los controles siguen, y al encenderlo Reintentar resuelve el golpe', async ({
    page,
  }) => {
    test.setTimeout(300_000);

    const creada = await crear(api, anfitriona, {
      maximoParticipantes: 2,
      modalidad: 'CONTRA_IA',
      recompensaCreditos: 0,
      privada: false,
    });
    expect(creada.status(), await creada.text()).toBe(201);
    const sala = await creada.json();
    const inicio = await api.post(`/api/v1/salas/${sala.id}/partida`, {
      headers: conToken(anfitriona.token),
    });
    expect(inicio.status(), await inicio.text()).toBe(201);
    let partida = await inicio.json();

    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${BORDE}${VISTA}?sala=${sala.id}&partida=${partida.id}`);
    const boton = page.locator('[data-zona="acciones"] [data-atacar]').first();
    await expect(boton).toBeEnabled({ timeout: 20_000 });

    apagar('srv-motor-combate');

    await boton.click();
    const degradada = page.locator('[data-zona="degradacion"] .seccion-degradada');
    await expect(degradada).toBeVisible({ timeout: 20_000 });
    await expect(degradada).toContainText('Motor de combate no disponible temporalmente');
    // La accion no se aplico y el turno sigue siendo de la anfitriona.
    await expect(boton).toBeEnabled();
    partida = await partidaDe(api, anfitriona, partida.id);
    expect(partida.estado).toBe('EN_CURSO');
    expect(partida.turnoActual.numeroTurno).toBe(1);
    expect(partida.turnoActual.idJugador).toBe(anfitriona.claims.uid);
    expect(partida.participantes[1].heroe.vidaActual).toBe(
      partida.participantes[1].heroe.vidaMaxima,
    );

    await encender('srv-motor-combate');
    // Retry-After ya paso de sobra mientras el motor arrancaba.
    await degradada.locator('.seccion-degradada__reintentar').click();

    await expect
      .poll(
        async () => {
          partida = await partidaDe(api, anfitriona, partida.id);
          return partida.estado !== 'EN_CURSO' || partida.turnoActual.numeroTurno > 1;
        },
        { timeout: 30_000, message: 'el golpe reintentado no se resolvio' },
      )
      .toBe(true);
    await expect(page.locator('[data-zona="degradacion"] .seccion-degradada')).toHaveCount(0);
  });
});
