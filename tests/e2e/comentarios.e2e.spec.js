// @ts-check
/**
 * Comentarios sobre productos — HU-COM-002 (calificacion unica, D-07),
 * HU-COM-003 (promedio) y HU-COM-004 (eliminar el propio), de punta a punta
 * por el borde, con el servicio real, la lista negra y las sanciones reales.
 *
 * Dos jugadoras sobre un producto nuevo (id unico por corrida):
 *   1. anfitriona comenta con 5 estrellas -> 201, promedio 5.00 (1)
 *   2. anfitriona vuelve a calificar (3)   -> 201 SIN estrellas, calificacionDescartada
 *      (D-07: no es 409), promedio sigue 5.00 (1)
 *   3. invitado califica con 3            -> promedio 4.00 (2)
 *   4. invitado intenta borrar el de la anfitriona -> 403 comentario-ajeno
 *   5. anfitriona borra el suyo (el de 5) -> 204; promedio 3.00 (1); borrar
 *      otra vez -> 204 (idempotente); un id inventado -> 404
 *   6. la vista: promedio arriba y «Eliminar» solo en los mios
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/plataforma/comentarios/publicar-comentario.html';

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

test.describe('Comentarios: calificacion unica, promedio y eliminar (HU-COM-002/003/004)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;
  let invitado;
  const producto = `producto-e2e-${Date.now()}`;
  const ruta = () => `/api/v1/products/${producto}/comments`;
  let comentarioDeAnfitriona;

  async function hilo() {
    const r = await api.get(ruta());
    expect(r.status(), await r.text()).toBe(200);
    return r.json();
  }

  async function comentar(quien, texto, estrellas) {
    const r = await api.post(ruta(), {
      headers: conToken(quien.token),
      data: estrellas === undefined ? { texto } : { texto, estrellas },
    });
    return r;
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('un producto nuevo tiene el hilo vacio y «sin calificaciones» (no un 0)', async () => {
    const h = await hilo();
    expect(h.comentarios).toEqual([]);
    expect(h.calificacionPromedio).toBeNull();
    expect(h.totalCalificaciones).toBe(0);
  });

  test('la primera calificacion entra y hace promedio; la segunda del mismo jugador entra sin estrellas (D-07)', async () => {
    const primera = await comentar(anfitriona, 'Excelente espada, aguanta toda la temporada', 5);
    expect(primera.status(), await primera.text()).toBe(201);
    comentarioDeAnfitriona = await primera.json();
    expect(comentarioDeAnfitriona.autorId).toBe(anfitriona.claims.uid);
    expect(comentarioDeAnfitriona.estrellas).toBe(5);
    expect(comentarioDeAnfitriona.calificacionDescartada).toBe(false);

    let h = await hilo();
    expect(h.calificacionPromedio).toBe(5);
    expect(h.totalCalificaciones).toBe(1);

    const segunda = await comentar(anfitriona, 'Segunda opinion, sigue igual de buena', 3);
    expect(segunda.status(), 'ya no es 409: entra sin estrellas').toBe(201);
    const cuerpo = await segunda.json();
    expect(cuerpo.estrellas ?? null, 'sin estrellas').toBeNull();
    expect(cuerpo.calificacionDescartada).toBe(true);

    h = await hilo();
    expect(h.total).toBe(2);
    expect(h.calificacionPromedio, 'la segunda no movio el promedio').toBe(5);
    expect(h.totalCalificaciones).toBe(1);
  });

  test('otra jugadora califica y el promedio se recalcula con las dos', async () => {
    const r = await comentar(invitado, 'Correcta, sin mas', 3);
    expect(r.status(), await r.text()).toBe(201);
    const h = await hilo();
    expect(h.calificacionPromedio).toBe(4);
    expect(h.totalCalificaciones).toBe(2);
  });

  test('eliminar: ajeno 403, propio 204 y sale del promedio, repetir 204, inexistente 404', async () => {
    const ajeno = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`, {
      headers: conToken(invitado.token),
    });
    expect(ajeno.status()).toBe(403);
    expect((await ajeno.json()).type).toMatch(/comentario-ajeno$/);

    const propio = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(propio.status(), await propio.text()).toBe(204);

    const h = await hilo();
    expect(h.comentarios.map((c) => c.id)).not.toContain(comentarioDeAnfitriona.id);
    expect(h.calificacionPromedio, 'solo queda la de invitado').toBe(3);
    expect(h.totalCalificaciones).toBe(1);

    const otraVez = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect(otraVez.status(), 'idempotente').toBe(204);

    const inventado = await api.delete(`${ruta()}/no-existe`, {
      headers: conToken(anfitriona.token),
    });
    expect(inventado.status()).toBe(404);
    expect((await inventado.json()).type).toMatch(/comentario-no-encontrado$/);

    const sinToken = await api.delete(`${ruta()}/${comentarioDeAnfitriona.id}`);
    expect(sinToken.status()).toBe(401);
  });

  test('la vista muestra el promedio y solo pone «Eliminar» en mis comentarios', async ({
    page,
  }) => {
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [anfitriona.token, ANFITRION, anfitriona.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}?producto=${producto}`);

    await expect(page.locator('[data-zona="promedio"]')).toHaveText(
      /3\.00 de 5 \(1 calificación\)/,
      {
        timeout: 20000,
      },
    );
    // Quedan: el segundo de la anfitriona (sin estrellas, suyo) y el de invitado.
    const articulos = page.locator('[data-zona="hilo-lista"] article');
    await expect(articulos).toHaveCount(2);
    const mios = page.locator('[data-zona="hilo-lista"] article:has([data-accion="eliminar"])');
    await expect(mios).toHaveCount(1);
    await expect(mios.first().locator('[data-campo="apodo"]')).toHaveText(ANFITRION);
  });
});
