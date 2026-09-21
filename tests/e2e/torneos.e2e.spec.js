// @ts-check
/**
 * Torneos — HU-TOR-001 (creacion, un torneo cada 91 dias), HU-TOR-003
 * (equipos de dos con nombre validado en la lista negra), HU-TOR-002
 * (inscripcion pagando en creditos por el libro), HU-TOR-005 (relleno con la
 * maquina), HU-TOR-004 (arbol de ocho: ganadores 1-6, 11 y final; secundarios
 * 7-10, 12 y 13), HU-ADM-005 (resultado a mano con motivo) y HU-TOR-008 (la
 * vista «Torneo»). De punta a punta por el borde con los servicios reales:
 * torneos, ms-finanzas, moderacion-sanciones y ms-identidad.
 *
 *   1. una jugadora no crea torneos (403); el administrador si (201, 0/8)
 *   2. un segundo torneo dentro de 91 dias -> 409 con proximaFechaPosible
 *   3. equipo anfitriona+invitado; nombre prohibido -> 422; invitado en otro
 *      equipo -> 409
 *   4. inscripcion: el pobre (0 creditos) -> 422 CREDITOS_INSUFICIENTES;
 *      la anfitriona paga 10 -> 201 posicion 1 y su saldo reservado sube
 *   5. inicio por el administrador -> EN_CURSO, 8 equipos (7 maquinas), 14
 *      encuentros, 1-4 listos; la reserva se cobro (saldo bruto -10)
 *   6. resultados: el servicio (e2e-banco) registra el 1; un jugador 403; el
 *      administrador sin motivo 400 y con motivo el resto -> FINALIZADO con
 *      campeon
 *   7. la vista muestra el torneo, el campeon y el arbol
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CURIOSO = process.env.E2E_CURIOSO ?? 'curioso_e2e';
const POBRE = process.env.E2E_POBRE ?? 'pobre_e2e';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const BANCO_CLIENTE = process.env.E2E_BANCO_CLIENTE ?? 'e2e-banco';
const BANCO_SECRETO = process.env.E2E_BANCO_SECRETO ?? 'e2e-secreto-del-banco-de-pruebas';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/torneos/torneos.html';
const COSTO = 10;

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
  return { ...cuerpo, apodo, claims: cuerpoDelToken(cuerpo.token) };
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('Torneos (HU-TOR-001..005, HU-ADM-005, HU-TOR-008)', () => {
  // Sin reintentos: el torneo creado aqui ocupa la ventana de 91 dias y una
  // segunda vuelta no podria crear otro.
  test.describe.configure({ mode: 'serial', retries: 0 });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;
  let invitado;
  let curioso;
  let pobre;
  let admin;
  let tokenBanco;
  let torneo;
  let equipo;
  let saldoAntes;

  async function saldoDe(quien) {
    const r = await api.get(`${FINANZAS}/creditos/${quien.claims.uid}/saldo`, {
      headers: conToken(quien.token),
    });
    expect(r.status(), await r.text()).toBe(200);
    const s = await r.json();
    return { bruto: Number(s.saldoBruto), reservado: Number(s.saldoReservado), disponible: Number(s.saldoDisponible) };
  }

  async function detalle() {
    const r = await api.get(`/api/v1/torneos/${torneo.id}`);
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
    curioso = await sesionDe(api, CURIOSO);
    pobre = await sesionDe(api, POBRE);
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol).toBe('ADMINISTRADOR');
    const credencial = await api.post('/api/v1/auth/token', {
      headers: {
        Authorization: `Basic ${Buffer.from(`${BANCO_CLIENTE}:${BANCO_SECRETO}`).toString('base64')}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      form: { grant_type: 'client_credentials' },
    });
    expect(credencial.status(), await credencial.text()).toBe(200);
    tokenBanco = (await credencial.json()).access_token;
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('crear: una jugadora no puede (403); el administrador abre las inscripciones (201)', async () => {
    const cierre = new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString();
    const negado = await api.post('/api/v1/torneos', {
      headers: conToken(anfitriona.token),
      data: { nombre: 'Copa pirata', inscripcionesCierranEn: cierre },
    });
    expect(negado.status()).toBe(403);
    expect((await negado.json()).motivo).toBe('PERMISO_INSUFICIENTE');

    const sinToken = await api.post('/api/v1/torneos', { data: { nombre: 'x', inscripcionesCierranEn: cierre } });
    expect(sinToken.status()).toBe(401);

    const creado = await api.post('/api/v1/torneos', {
      headers: conToken(admin.token),
      data: { nombre: 'Copa E2E', inscripcionesCierranEn: cierre, costoInscripcion: COSTO },
    });
    expect(creado.status(), await creado.text()).toBe(201);
    torneo = await creado.json();
    expect(torneo.estado).toBe('INSCRIPCIONES_ABIERTAS');
    expect(torneo.cupos).toBe(8);
    expect(torneo.equiposInscritos).toBe(0);
    expect(torneo.costoInscripcion).toBe(COSTO);

    const lista = await api.get('/api/v1/torneos');
    expect(lista.status()).toBe(200);
    expect((await lista.json()).map((t) => t.id)).toContain(torneo.id);
  });

  test('un segundo torneo dentro de los 91 dias se rechaza con la proxima fecha posible (CA-02)', async () => {
    const r = await api.post('/api/v1/torneos', {
      headers: conToken(admin.token),
      data: { nombre: 'Copa repetida', inscripcionesCierranEn: new Date().toISOString() },
    });
    expect(r.status()).toBe(409);
    const problema = await r.json();
    expect(problema.motivo).toBe('VENTANA_DE_91_DIAS');
    expect(Date.parse(problema.proximaFechaPosible)).toBeGreaterThan(Date.now() + 90 * 24 * 3600 * 1000);
  });

  test('equipos: nombre prohibido 422; un jugador en un solo equipo 409; el equipo queda registrado sin inscribir', async () => {
    // La lista negra del banco trae terminos sembrados por R1 (guion de demo);
    // se prueba con uno que el propio moderador acaba de anadir.
    const termino = `zorrudo${Date.now()}`;
    const alta = await api.post('/api/v1/lista-negra/terminos', {
      headers: conToken(admin.token),
      data: { termino },
    });
    expect([200, 201]).toContain(alta.status());

    const prohibido = await api.post(`/api/v1/torneos/${torneo.id}/equipos`, {
      headers: conToken(anfitriona.token),
      data: { nombre: `Los ${termino}`, avatar: 'avatar-1', companeroUid: invitado.claims.uid },
    });
    expect(prohibido.status(), await prohibido.text()).toBe(422);
    expect((await prohibido.json()).motivo).toBe('NOMBRE_RECHAZADO');

    const creado = await api.post(`/api/v1/torneos/${torneo.id}/equipos`, {
      headers: conToken(anfitriona.token),
      data: { nombre: 'Los Valientes', avatar: 'avatar-1', companeroUid: invitado.claims.uid },
    });
    expect(creado.status(), await creado.text()).toBe(201);
    equipo = await creado.json();
    expect(equipo.integrantes).toEqual([anfitriona.claims.uid, invitado.claims.uid]);
    expect(equipo.inscrito).toBe(false);

    const repetido = await api.post(`/api/v1/torneos/${torneo.id}/equipos`, {
      headers: conToken(curioso.token),
      data: { nombre: 'Los Otros', avatar: 'avatar-2', companeroUid: invitado.claims.uid },
    });
    expect(repetido.status()).toBe(409);
    expect((await repetido.json()).motivo).toBe('JUGADOR_YA_EN_EQUIPO');
  });

  test('inscripcion: sin creditos 422 y sin cobro; con creditos 201, posicion 1 y reserva en el libro', async () => {
    const delPobre = await api.post(`/api/v1/torneos/${torneo.id}/equipos`, {
      headers: conToken(pobre.token),
      data: { nombre: 'Los Sin Blanca', avatar: 'avatar-3', companeroUid: curioso.claims.uid },
    });
    expect(delPobre.status(), await delPobre.text()).toBe(201);
    const equipoPobre = await delPobre.json();
    const sinCreditos = await api.post(`/api/v1/torneos/${torneo.id}/equipos/${equipoPobre.id}/inscripcion`, {
      headers: conToken(pobre.token),
    });
    expect(sinCreditos.status(), await sinCreditos.text()).toBe(422);
    expect((await sinCreditos.json()).motivo).toBe('CREDITOS_INSUFICIENTES');

    const ajeno = await api.post(`/api/v1/torneos/${torneo.id}/equipos/${equipo.id}/inscripcion`, {
      headers: conToken(curioso.token),
    });
    expect(ajeno.status()).toBe(403);

    saldoAntes = await saldoDe(anfitriona);
    const inscrito = await api.post(`/api/v1/torneos/${torneo.id}/equipos/${equipo.id}/inscripcion`, {
      headers: conToken(anfitriona.token),
    });
    expect(inscrito.status(), await inscrito.text()).toBe(201);
    const cuerpo = await inscrito.json();
    expect(cuerpo.inscrito).toBe(true);
    expect(cuerpo.posicion).toBe(1);
    expect(cuerpo.pagadoPor).toBe(anfitriona.claims.uid);
    expect(cuerpo.reservaId).toBeTruthy();

    const saldo = await saldoDe(anfitriona);
    expect(saldo.reservado).toBe(saldoAntes.reservado + COSTO);
    expect(saldo.bruto, 'reservado, todavia no cobrado').toBe(saldoAntes.bruto);

    const otraVez = await api.post(`/api/v1/torneos/${torneo.id}/equipos/${equipo.id}/inscripcion`, {
      headers: conToken(invitado.token),
    });
    expect(otraVez.status()).toBe(409);
    expect((await otraVez.json()).motivo).toBe('YA_INSCRITO');
    expect((await detalle()).equiposInscritos).toBe(1);
  });

  test('inicio: se cobra la inscripcion, la maquina completa el arbol y los encuentros 1-4 quedan listos', async () => {
    const porJugadora = await api.post(`/api/v1/torneos/${torneo.id}/inicio`, {
      headers: conToken(anfitriona.token),
    });
    expect(porJugadora.status()).toBe(403);

    const r = await api.post(`/api/v1/torneos/${torneo.id}/inicio`, { headers: conToken(admin.token) });
    expect(r.status(), await r.text()).toBe(200);
    const enCurso = await r.json();
    expect(enCurso.estado).toBe('EN_CURSO');
    expect(enCurso.equipos.filter((e) => e.inscrito)).toHaveLength(8);
    expect(enCurso.equipos.filter((e) => e.ia)).toHaveLength(7);
    expect(enCurso.encuentros).toHaveLength(14);
    expect(enCurso.encuentros.filter((e) => e.estado === 'LISTO').map((e) => e.numero)).toEqual([1, 2, 3, 4]);
    expect(enCurso.encuentros[0].equipoA).toBe(equipo.id);

    const saldo = await saldoDe(anfitriona);
    expect(saldo.bruto, 'la reserva se cobro al iniciar').toBe(saldoAntes.bruto - COSTO);
    expect(saldo.reservado).toBe(saldoAntes.reservado);

    const tarde = await api.post(`/api/v1/torneos/${torneo.id}/equipos`, {
      headers: conToken(curioso.token),
      data: { nombre: 'Tarde', avatar: 'a', companeroUid: pobre.claims.uid },
    });
    expect(tarde.status()).toBe(409);
    expect((await tarde.json()).motivo).toBe('ESTADO_NO_PERMITE');
  });

  test('resultados: los aporta un servicio o un administrador con motivo; el arbol avanza hasta el campeon', async () => {
    let actual = await detalle();
    const porJugadora = await api.post(`/api/v1/torneos/${torneo.id}/encuentros/1/resultado`, {
      headers: conToken(anfitriona.token),
      data: { ganadorEquipoId: equipo.id },
    });
    expect(porJugadora.status()).toBe(403);

    const sinMotivo = await api.post(`/api/v1/torneos/${torneo.id}/encuentros/1/resultado`, {
      headers: conToken(admin.token),
      data: { ganadorEquipoId: equipo.id },
    });
    expect(sinMotivo.status()).toBe(400);
    expect((await sinMotivo.json()).motivo).toBe('SOLICITUD_INVALIDA');

    const noListo = await api.post(`/api/v1/torneos/${torneo.id}/encuentros/5/resultado`, {
      headers: conToken(tokenBanco),
      data: { ganadorEquipoId: equipo.id },
    });
    expect(noListo.status()).toBe(409);
    expect((await noListo.json()).motivo).toBe('ENCUENTRO_NO_LISTO');

    const partidaId = '00000000-0000-4000-8000-000000000001';
    const primero = await api.post(`/api/v1/torneos/${torneo.id}/encuentros/1/resultado`, {
      headers: conToken(tokenBanco),
      data: { ganadorEquipoId: equipo.id, partidaId },
    });
    expect(primero.status(), await primero.text()).toBe(200);
    actual = await primero.json();
    expect(actual.encuentros[0].estado).toBe('JUGADO');
    expect(actual.encuentros[0].registradoPor).toBe(BANCO_CLIENTE);
    expect(actual.encuentros[0].partidaId).toBe(partidaId);
    expect(actual.encuentros[4].equipoA).toBe(equipo.id);

    for (let numero = 2; numero <= 14; numero++) {
      const encuentro = actual.encuentros[numero - 1];
      expect(encuentro.estado, `encuentro ${numero} listo`).toBe('LISTO');
      const r = await api.post(`/api/v1/torneos/${torneo.id}/encuentros/${numero}/resultado`, {
        headers: conToken(admin.token),
        data: { ganadorEquipoId: encuentro.equipoA, motivo: 'Incomparecencia del rival (E2E)' },
      });
      expect(r.status(), `encuentro ${numero}: ${await r.text()}`).toBe(200);
      actual = await r.json();
    }
    expect(actual.estado).toBe('FINALIZADO');
    expect(actual.campeonEquipoId).toBe(equipo.id);
    expect(actual.encuentros.every((e) => e.estado === 'JUGADO')).toBe(true);
    expect(actual.encuentros[13].motivo).toMatch(/Incomparecencia/);
    expect(actual.equipos.filter((e) => e.eliminado)).toHaveLength(7);
  });

  test('la vista «Torneo» muestra el torneo, el campeon y el arbol (HU-TOR-008)', async ({ page }) => {
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [anfitriona.token, ANFITRION, anfitriona.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}?torneo=${torneo.id}`);
    await expect(page.locator('.cabecera [data-seccion="torneo"]')).toHaveAttribute('aria-current', 'page');
    await expect(page.locator(`[data-zona="listado"] [data-torneo-id="${torneo.id}"]`)).toBeVisible({
      timeout: 20000,
    });
    const detalleVista = page.locator('[data-zona="detalle"]');
    await expect(detalleVista).toHaveAttribute('data-estado', 'FINALIZADO');
    await expect(detalleVista.locator('[data-zona="campeon"]')).toHaveText('Campeon: Los Valientes');
    await expect(detalleVista.locator('[data-llave="GANADORES"] li')).toHaveCount(7);
    await expect(detalleVista.locator('[data-llave="SECUNDARIOS"] li')).toHaveCount(6);
    await expect(detalleVista.locator('[data-llave="FINAL"] li')).toHaveCount(1);
    await expect(detalleVista.locator('[data-llave="FINAL"] li')).toContainText('gana Los Valientes');
    await expect(detalleVista.locator('[data-zona="equipos"] article[data-ia="true"]')).toHaveCount(7);
    await expect(detalleVista.locator(`[data-equipo-id="${equipo.id}"]`)).toContainText('(tu equipo)');
  });
});
