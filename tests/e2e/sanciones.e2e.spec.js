// @ts-check
/**
 * Sanciones y apelaciones — HU-USR-004 (advertencia), HU-USR-005 (suspension
 * temporal), HU-USR-006 (baneo, solo administrador y con confirmacion),
 * HU-USR-007 (apelacion con decision motivada) y HU-NOT-005 (el jugador se
 * entera por su bandeja), de punta a punta por el borde con los servicios
 * reales: moderacion-sanciones, notificaciones, comentarios y ms-identidad.
 *
 * Tres cuentas: una jugadora (`sancionada_e2e`, solo de este spec para no
 * silenciar a las de los demas), la moderadora y el administrador que
 * sembrar.sh deja con su rol en la base de identidad.
 *
 *   1. una jugadora no emite ni revisa: 403 en /sanciones y /apelaciones
 *   2. advertencia -> 201, no restringe (activa=false), puede comentar,
 *      y el aviso SANCION_ADVERTENCIA llega a su bandeja
 *   3. baneo: la moderadora no puede (403); el administrador sin
 *      confirmacion tampoco (400)
 *   4. suspension de 2 h -> activa=true tipo SUSPENSION y el comentario se
 *      rechaza con 403 AUTOR_SILENCIADO
 *   5. apelar -> PENDIENTE (repetir: 422); el panel la ve; el administrador
 *      la REVIERTE con motivo -> activa=false, vuelve a comentar, y el aviso
 *      APELACION_REVERTIDA llega a la bandeja
 *   6. las vistas: «Mis sanciones» con las dos tarjetas y la apelacion
 *      revertida; el panel de la moderadora con el historial
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const MODERADORA = process.env.E2E_MODERADORA ?? 'moderadora_e2e';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const JUGADORA = process.env.E2E_SANCIONADA ?? 'sancionada_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTAS = '/frontend/app-web/src/plataforma/moderacion-sanciones';

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

async function conSesion(page, sesion) {
  await page.addInitScript(
    ([token, nombre, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [sesion.token, sesion.apodo, sesion.claims.uid],
  );
}

test.describe('Sanciones y apelaciones (HU-USR-004/005/006/007, HU-NOT-005)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let moderadora;
  let admin;
  let jugadora;
  let advertencia;
  let suspension;
  let apelacion;
  const producto = `producto-sanciones-${Date.now()}`;

  async function activa() {
    const r = await api.get(`/api/v1/sanciones/usuarios/${jugadora.claims.uid}/activa`);
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  async function comentar(texto) {
    return api.post(`/api/v1/products/${producto}/comments`, {
      headers: conToken(jugadora.token),
      data: { texto },
    });
  }

  async function emitir(quien, cuerpo) {
    return api.post('/api/v1/sanciones', { headers: conToken(quien.token), data: cuerpo });
  }

  /** La bandeja se llena por un entregador con reintento: se espera un poco. */
  async function esperarAviso(tipo) {
    await expect
      .poll(
        async () => {
          const r = await api.get(`/api/v1/users/${jugadora.claims.uid}/notifications`, {
            headers: conToken(jugadora.token),
          });
          if (r.status() !== 200) {
            return `HTTP ${r.status()}`;
          }
          const bandeja = await r.json();
          return (bandeja.avisos ?? []).some((a) => a.tipo === tipo) ? tipo : 'todavia no';
        },
        { timeout: 20000, intervals: [500, 1000, 2000] },
      )
      .toBe(tipo);
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    moderadora = await sesionDe(api, MODERADORA);
    admin = await sesionDe(api, ADMIN);
    jugadora = await sesionDe(api, JUGADORA);
    expect(moderadora.claims.rol, 'sembrar.sh deja a la moderadora con su rol').toBe('MODERADOR');
    expect(admin.claims.rol, 'sembrar.sh deja al administrador con su rol').toBe('ADMINISTRADOR');
    expect(jugadora.claims.rol).toBe('JUGADOR');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('una jugadora no emite sanciones ni revisa apelaciones', async () => {
    const emitida = await emitir(jugadora, {
      usuarioId: moderadora.claims.uid,
      tipo: 'ADVERTENCIA',
      motivo: 'Venganza',
    });
    expect(emitida.status()).toBe(403);
    expect((await emitida.json()).motivo).toBe('PERMISO_INSUFICIENTE');

    const pendientes = await api.get('/api/v1/apelaciones', { headers: conToken(jugadora.token) });
    expect(pendientes.status()).toBe(403);

    const sinToken = await api.get(`/api/v1/sanciones/usuarios/${jugadora.claims.uid}`);
    expect(sinToken.status()).toBe(401);
  });

  test('advertencia: queda en el historial, no restringe, y llega a la bandeja', async () => {
    const r = await emitir(moderadora, {
      usuarioId: jugadora.claims.uid,
      tipo: 'ADVERTENCIA',
      motivo: 'Lenguaje ofensivo en el chat',
      politica: 'Normas de convivencia 2.1',
    });
    expect(r.status(), await r.text()).toBe(201);
    advertencia = await r.json();
    expect(advertencia.tipo).toBe('ADVERTENCIA');
    expect(advertencia.rolEmisor).toBe('MODERADOR');
    expect(advertencia.vigenteHasta ?? null).toBeNull();

    const estado = await activa();
    expect(estado.sancionActiva, 'una advertencia no restringe (D-14)').toBe(false);

    const comentario = await comentar('Sigo pudiendo comentar tras la advertencia');
    expect(comentario.status(), await comentario.text()).toBe(201);

    const historial = await api.get(`/api/v1/sanciones/usuarios/${jugadora.claims.uid}`, {
      headers: conToken(jugadora.token),
    });
    expect(historial.status()).toBe(200);
    expect((await historial.json()).map((s) => s.id)).toContain(advertencia.id);

    await esperarAviso('SANCION_ADVERTENCIA');
  });

  test('baneo: la moderadora no puede y el administrador necesita confirmarlo', async () => {
    const porModeradora = await emitir(moderadora, {
      usuarioId: jugadora.claims.uid,
      tipo: 'BANEO',
      motivo: 'Fraude',
      confirmacion: true,
    });
    expect(porModeradora.status()).toBe(403);
    expect((await porModeradora.json()).motivo).toBe('PERMISO_INSUFICIENTE');

    const sinConfirmar = await emitir(admin, {
      usuarioId: jugadora.claims.uid,
      tipo: 'BANEO',
      motivo: 'Fraude',
    });
    expect(sinConfirmar.status()).toBe(400);
    expect((await sinConfirmar.json()).motivo).toBe('SOLICITUD_INVALIDA');

    expect((await activa()).sancionActiva, 'nada de lo anterior sanciono').toBe(false);
  });

  test('suspension de 2 horas: activa, con fecha fin, y el comentario se rechaza', async () => {
    const r = await emitir(moderadora, {
      usuarioId: jugadora.claims.uid,
      tipo: 'SUSPENSION',
      motivo: 'Reincidencia tras la advertencia',
      duracionHoras: 2,
    });
    expect(r.status(), await r.text()).toBe(201);
    suspension = await r.json();
    expect(suspension.tipo).toBe('SUSPENSION');
    expect(suspension.vigente).toBe(true);
    const fin = Date.parse(suspension.vigenteHasta);
    expect(fin - Date.parse(suspension.emitidaEn)).toBe(2 * 60 * 60 * 1000);

    const estado = await activa();
    expect(estado.sancionActiva).toBe(true);
    expect(estado.tipo).toBe('SUSPENSION');
    expect(estado.vigenteHasta).toBeTruthy();

    const comentario = await comentar('Esto no deberia entrar');
    expect(comentario.status(), await comentario.text()).toBe(403);
    expect((await comentario.json()).motivo).toBe('AUTOR_SILENCIADO');

    await esperarAviso('SANCION_SUSPENSION');
  });

  test('apelar: una sola abierta, el panel la ve, y la reversion motivada levanta la sancion', async () => {
    const r = await api.post(`/api/v1/sanciones/${suspension.id}/apelaciones`, {
      headers: conToken(jugadora.token),
      data: { argumento: 'El mensaje era una cita, no un insulto' },
    });
    expect(r.status(), await r.text()).toBe(201);
    apelacion = await r.json();
    expect(apelacion.estado).toBe('PENDIENTE');
    expect(apelacion.sancionId).toBe(suspension.id);

    const repetida = await api.post(`/api/v1/sanciones/${suspension.id}/apelaciones`, {
      headers: conToken(jugadora.token),
      data: { argumento: 'Otra vez' },
    });
    expect(repetida.status()).toBe(422);
    expect((await repetida.json()).motivo).toBe('APELACION_NO_PROCEDE');

    const ajena = await api.post(`/api/v1/sanciones/${advertencia.id}/apelaciones`, {
      headers: conToken(moderadora.token),
      data: { argumento: 'No es mia' },
    });
    expect(ajena.status(), 'solo el sancionado apela').toBe(422);

    const pendientes = await api.get('/api/v1/apelaciones', { headers: conToken(moderadora.token) });
    expect(pendientes.status()).toBe(200);
    expect((await pendientes.json()).map((a) => a.id)).toContain(apelacion.id);

    const mias = await api.get('/api/v1/apelaciones?mias=true', { headers: conToken(jugadora.token) });
    expect(mias.status()).toBe(200);
    expect((await mias.json()).map((a) => a.id)).toContain(apelacion.id);

    const sinMotivo = await api.post(`/api/v1/apelaciones/${apelacion.id}/resolucion`, {
      headers: conToken(admin.token),
      data: { decision: 'REVERTIDA', motivo: '' },
    });
    expect(sinMotivo.status(), 'toda decision lleva motivo (CA-03)').toBe(400);

    const resuelta = await api.post(`/api/v1/apelaciones/${apelacion.id}/resolucion`, {
      headers: conToken(admin.token),
      data: { decision: 'REVERTIDA', motivo: 'Revisado el contexto: era una cita' },
    });
    expect(resuelta.status(), await resuelta.text()).toBe(200);
    const decidida = await resuelta.json();
    expect(decidida.estado).toBe('REVERTIDA');
    expect(decidida.decisionMotivo).toBe('Revisado el contexto: era una cita');

    const otraVez = await api.post(`/api/v1/apelaciones/${apelacion.id}/resolucion`, {
      headers: conToken(admin.token),
      data: { decision: 'MANTENIDA', motivo: 'Cambio de opinion' },
    });
    expect(otraVez.status(), 'una apelacion se resuelve una vez').toBe(409);

    expect((await activa()).sancionActiva, 'revertida: ya no restringe').toBe(false);
    const comentario = await comentar('De vuelta tras la apelacion');
    expect(comentario.status(), await comentario.text()).toBe(201);

    await esperarAviso('APELACION_REVERTIDA');
  });

  test('«Mis sanciones» muestra las dos y la apelacion revertida', async ({ page }) => {
    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${VISTAS}/mis-sanciones.html`);
    const tarjetas = page.locator('[data-zona="sanciones"] article');
    await expect(tarjetas).toHaveCount(2, { timeout: 20000 });
    await expect(page.locator(`[data-sancion-id="${suspension.id}"]`)).toContainText(/revertida/);
    await expect(page.locator(`[data-sancion-id="${advertencia.id}"]`)).toContainText(
      /no restringe tu acceso/,
    );
    await expect(page.locator(`[data-apelacion-id="${apelacion.id}"]`)).toContainText(
      /revertida/,
    );
    await expect(page.locator('[data-zona="sanciones"] [data-accion="apelar"]')).toHaveCount(0);
  });

  test('el panel de la moderadora carga el historial del usuario buscado', async ({ page }) => {
    await conSesion(page, moderadora);
    await page.goto(`${BORDE}${VISTAS}/sanciones-admin.html`);
    await expect(page.locator('[name="tipo"] option[value="BANEO"]')).toBeDisabled();
    await page.fill('[data-zona="buscar"] [name="usuarioId"]', jugadora.claims.uid);
    await page.click('[data-zona="buscar"] button[type="submit"]');
    await expect(page.locator('[data-zona="historial"] article')).toHaveCount(2, {
      timeout: 20000,
    });
    await expect(page.locator('[data-zona="emitir"] [name="usuarioId"]')).toHaveValue(
      jugadora.claims.uid,
    );
  });
});
