// @ts-check
/**
 * HU-NOT-001 (#532) — los cambios del catálogo llegan a la bandeja del
 * jugador, de punta a punta por el borde y contra los servicios reales del
 * banco (`tests/e2e/compose.yml`): productos registra el cambio y
 * notificaciones lo trae con su credencial de servicio
 * (`GET /api/v1/productos/alertas/cambios`, productos.yaml 1.6.0) al entregar
 * lo pendiente a una sesión (`POST .../sessions/{sesionId}/pending`,
 * notificaciones.yaml 1.4.0).
 *
 * Lo que se afirma, en orden y sin SQL:
 *
 *   1. el primer ingreso de una jugadora nueva deja la línea base: el producto
 *      que un administrador dio de alta ANTES no le llega (no se vuelca el
 *      historial del catálogo);
 *   2. un administrador modifica ese producto (PATCH, el de siempre) y, en la
 *      siguiente sesión, la jugadora tiene en su bandeja un `CAMBIO_CATALOGO`
 *      con la descripción del cambio y su fecha de implementación;
 *   3. volver a entregar lo pendiente no lo duplica;
 *   4. la vista de notificaciones lo pinta con su título y su descripción.
 *
 * El banco pone el intervalo mínimo entre consultas a productos en 1 s
 * (NOTIFICACIONES_CATALOGO_INTERVALO_MINIMO_S, en DEV 60 s): por eso las
 * entregas de abajo esperan algo más de un segundo entre sí.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const SUFIJO = Date.now();
const NOTIFICACIONES = '/frontend/app-web/src/plataforma/notificaciones/notificaciones.html';
// Una imagen que ya sirve el propio borde (la misma que usa el spec de la tienda).
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';
/** Algo más que el intervalo mínimo del banco (1 s). */
const PASA_EL_INTERVALO_MS = 1_300;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token, extra = {}) {
  return { Authorization: `Bearer ${token}`, ...extra };
}

const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));

/** Deja la sesión en el navegador como la deja login.js. */
async function conSesion(page, jugador) {
  await page.addInitScript(
    ([token, apodo, uid]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', apodo);
      sessionStorage.setItem('nexus.usuarioId', uid);
    },
    [jugador.token, jugador.apodo, jugador.claims.uid],
  );
}

test.describe('avisos de cambios del catálogo en la bandeja (HU-NOT-001)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugadora;
  let productoId;
  const nombre = `Lanza de los avisos E2E ${SUFIJO}`;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
    // Una jugadora que no existía: no tiene punto de lectura del catálogo.
    jugadora = await sesionDe(api, `catalogo_${SUFIJO}`);
    expect(jugadora.claims.rol).toBe('JUGADOR');

    // El producto nace ANTES de la línea base de la jugadora.
    const alta = await api.post('/api/v1/productos', {
      headers: conToken(admin.token, { 'Content-Type': 'application/json' }),
      data: {
        nombre,
        imagen: IMAGEN,
        descripcion: 'Arma de prueba del E2E de los avisos del catálogo.',
        tipo: 'ARMA',
        tiraje: -1,
        premium: false,
        precioCreditos: 120,
        poderDeAtaque: 9,
        tasaDeCaida: 50,
      },
    });
    expect(alta.status(), await alta.text()).toBe(201);
    productoId = (await alta.json()).id;
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  /** Lo que hace la interfaz al anunciar una sesión sin canal en tiempo real. */
  async function entregarPendientes(sesion) {
    const respuesta = await api.post(
      `/api/v1/users/${jugadora.claims.uid}/sessions/${sesion}/pending`,
      { headers: conToken(jugadora.token) },
    );
    expect(respuesta.status(), await respuesta.text()).toBe(200);
    return respuesta.json();
  }

  async function bandeja() {
    const respuesta = await api.get(`/api/v1/users/${jugadora.claims.uid}/notifications`, {
      headers: conToken(jugadora.token),
    });
    expect(respuesta.status(), await respuesta.text()).toBe(200);
    return respuesta.json();
  }

  /** Los avisos del catálogo que hablan de ESTE producto (su nombre lleva el sufijo de la corrida). */
  const delProducto = (avisos) =>
    (avisos ?? []).filter((a) => a.tipo === 'CAMBIO_CATALOGO' && a.cuerpo.includes(nombre));

  test('1 · el primer ingreso deja la línea base: el alta anterior no llega a la bandeja', async () => {
    const entregados = await entregarPendientes(`linea-base-${SUFIJO}`);

    expect(delProducto(entregados)).toEqual([]);
    expect(delProducto((await bandeja()).avisos)).toEqual([]);
  });

  test('2 · un administrador modifica el producto y la jugadora lo ve en su bandeja con la descripción y la fecha', async () => {
    const cambio = await api.patch(`/api/v1/productos/${productoId}`, {
      headers: conToken(admin.token, { 'Content-Type': 'application/json' }),
      data: { descripcion: `Arma de prueba del E2E, descripción nueva ${SUFIJO}.` },
    });
    expect(cambio.status(), await cambio.text()).toBe(200);

    let aviso = null;
    await expect
      .poll(
        async () => {
          await esperar(PASA_EL_INTERVALO_MS);
          await entregarPendientes(`despues-del-cambio-${Date.now()}`);
          aviso = delProducto((await bandeja()).avisos)[0] ?? null;
          return aviso ? 'en la bandeja' : 'todavía no';
        },
        {
          timeout: 30_000,
          message:
            'notificaciones no trajo el cambio: mirar PRODUCTOS_BASE_URL y la credencial ' +
            '«notificaciones» (AUTH_CLIENTES_SERVICIO) del banco',
        },
      )
      .toBe('en la bandeja');

    expect(aviso).toMatchObject({
      tipo: 'CAMBIO_CATALOGO',
      titulo: 'Producto modificado',
      leida: false,
    });
    expect(aviso.id).toMatch(/^catalogo:/);
    expect(aviso.cuerpo).toContain(`El producto ${nombre} fue modificado.`);
    expect(aviso.cuerpo).toMatch(
      /Fecha de implementación: \d{1,2} de [a-z]+ de \d{4}, \d{2}:\d{2}\.$/,
    );
    expect(Number.isNaN(Date.parse(aviso.creadaEn)), 'creadaEn es la fecha del cambio').toBe(false);
  });

  test('3 · volver a entregar lo pendiente no duplica el aviso', async () => {
    for (let vuelta = 0; vuelta < 2; vuelta++) {
      await esperar(PASA_EL_INTERVALO_MS);
      await entregarPendientes(`repite-${vuelta}-${SUFIJO}`);
    }

    // Uno solo: el de la modificación. El alta, anterior a la línea base, tampoco.
    expect(delProducto((await bandeja()).avisos)).toHaveLength(1);
  });

  test('4 · la vista de notificaciones lo pinta con su título y su descripción', async ({
    page,
  }) => {
    await conSesion(page, jugadora);
    await page.goto(`${BORDE}${NOTIFICACIONES}`);

    const item = page.locator('[data-zona="lista"] li', { hasText: nombre });
    await expect(item).toBeVisible({ timeout: 20_000 });
    await expect(item.locator('.tarjeta__titulo')).toHaveText('Producto modificado');
    await expect(item).toContainText('Fecha de implementación:');
  });
});
