// @ts-check
/**
 * Las operaciones del catálogo en la bitácora central — SA-18, §7.2.5
 * («registro de auditoría de todas las operaciones realizadas») y §7.3.5
 * (todas las acciones administrativas, inmutables, solo para el Super
 * Administrador), de punta a punta por el borde con productos 1.12.0 y
 * ms-cumplimiento 1.4.0 reales:
 *
 *   1. un Administrador da de alta un producto y lo suspende con su motivo;
 *      publica un banner y lo retira con el suyo;
 *   2. el banner dice quién lo publicó y su historial lo cuenta, sin la IP;
 *   3. la bitácora central las importa (cada 3 s en el banco) con quién,
 *      cuándo, qué, sobre qué y su nombre, antes y después, motivo e IP, una
 *      sola vez cada una;
 *   4. las operaciones del catálogo no las lee nadie más que la bitácora: ni
 *      un Administrador ni el Super Administrador por esa ruta, ni sin token.
 *
 * Los nombres llevan el sufijo de la corrida. Se espera con `expect.poll`,
 * nunca con pausas fijas.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);
const ACEPTA = 'application/problem+json, application/json';
const IP = /^[0-9a-fA-F:.]{3,45}$/;
const ESPERA = { timeout: 60_000, intervals: [1000, 2000, 3000] };
// Una imagen que ya sirve el propio borde (la misma que usan los demás specs).
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

function como(sesion) {
  return {
    headers: {
      Accept: ACEPTA,
      'Content-Type': 'application/json',
      Authorization: `Bearer ${sesion.token}`,
    },
  };
}

test.describe('SA-18 · las operaciones del catálogo llegan a la bitácora central', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let superAdmin;
  let producto;
  let banner;
  const nombre = `Hoja del registro E2E ${SUFIJO}`;
  const motivoSuspension = `Rompe el balance de la temporada ${SUFIJO}`;
  const contenidoBanner = `Torneo relámpago E2E ${SUFIJO}`;
  const motivoRetiro = `El torneo se aplazó ${SUFIJO}`;

  /** Las entradas de la bitácora sobre `afectado`, como Super Administrador. */
  async function bitacora(afectado) {
    const consulta = new URLSearchParams({ size: '50', sort: 'fechaHora,desc', afectado });
    const r = await api.get(`/api/v1/admin/auditoria?${consulta}`, como(superAdmin));
    expect(r.status(), await r.text()).toBe(200);
    return (await r.json()).content ?? [];
  }

  /** Espera a que la bitácora tenga `cuantas` entradas sobre `afectado` y las devuelve. */
  async function esperarEntradas(afectado, cuantas) {
    let entradas = [];
    await expect
      .poll(
        async () => {
          entradas = await bitacora(afectado);
          return entradas.length;
        },
        { ...ESPERA, message: `la bitácora no importó las operaciones sobre ${afectado}` },
      )
      .toBeGreaterThanOrEqual(cuantas);
    return entradas;
  }

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    superAdmin = await sesionDe(api, cuentaAdministrativaDeRol('SUPER_ADMINISTRADOR').apodo);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · un Administrador da de alta y suspende un producto, publica y retira un banner', async () => {
    const alta = await api.post('/api/v1/productos', {
      ...como(admin),
      data: {
        nombre,
        descripcion: 'Producto del E2E de la bitácora del catálogo.',
        imagen: IMAGEN,
        tipo: 'ARMA',
        tiraje: -1,
        premium: false,
        precioCreditos: 300,
        poderDeAtaque: 9,
        tasaDeCaida: 5,
      },
    });
    expect(alta.status(), await alta.text()).toBe(201);
    producto = await alta.json();

    const suspension = await api.put(
      `/api/v1/productos/${producto.id}/suspender?motivo=${encodeURIComponent(motivoSuspension)}`,
      como(admin),
    );
    expect(suspension.status(), await suspension.text()).toBe(200);

    const publicado = await api.post('/api/v1/banners', {
      ...como(admin),
      data: {
        contenido: contenidoBanner,
        publicarDesde: new Date(Date.now() + 3_600_000).toISOString(),
        vigenteHasta: new Date(Date.now() + 2 * 86_400_000).toISOString(),
      },
    });
    expect(publicado.status(), await publicado.text()).toBe(201);
    banner = await publicado.json();
    expect(banner.creadoPor, 'el uid del token de quien lo publica').toBe(admin.claims.uid);

    const retiro = await api.delete(
      `/api/v1/banners/${banner.id}?motivo=${encodeURIComponent(motivoRetiro)}`,
      como(admin),
    );
    expect(retiro.status(), await retiro.text()).toBe(204);
  });

  test('2 · el banner dice quién lo publicó y su historial lo cuenta, sin la IP', async () => {
    const lista = await api.get('/api/v1/banners', como(admin));
    expect(lista.status()).toBe(200);
    const delE2E = (await lista.json()).find((b) => b.id === banner.id);
    expect(delE2E).toMatchObject({
      retirado: true,
      creadoPor: admin.claims.uid,
      modificadoPor: admin.claims.uid,
    });

    const r = await api.get(`/api/v1/banners/${banner.id}/historial`, como(admin));
    expect(r.status(), await r.text()).toBe(200);
    const historial = await r.json();
    expect(historial.map((c) => c.tipo)).toEqual(['RETIRO_BANNER', 'ALTA_BANNER']);
    expect(historial[0]).toMatchObject({ autor: admin.claims.uid, motivo: motivoRetiro });
    expect(historial[0]).not.toHaveProperty('ipOrigen');
    expect(historial[0].valorNuevo).toContain('Retirado');
  });

  test('3 · la bitácora central las importa con quién, qué, sobre qué, antes, después, motivo e IP', async () => {
    const delProducto = await esperarEntradas(`producto:${producto.id}`, 2);
    const altaDelProducto = delProducto.find((e) => e.tipoAccion === 'CREACION');
    const suspension = delProducto.find((e) => e.tipoAccion === 'SUSPENSION');
    expect(altaDelProducto).toBeTruthy();
    expect(suspension).toBeTruthy();
    expect(altaDelProducto).toMatchObject({
      administrador: admin.claims.uid,
      nombreAfectado: nombre,
      motivo: 'Alta del producto en el catálogo',
      referencia: expect.stringMatching(/^productos:/),
    });
    expect(altaDelProducto.valorNuevo).toContain('Tipo: Arma');
    expect(suspension).toMatchObject({
      administrador: admin.claims.uid,
      nombreAfectado: nombre,
      motivo: motivoSuspension,
      valorAnterior: 'Estado: Activo',
      valorNuevo: 'Estado: Suspendido',
    });
    expect(suspension.ipOrigen, 'la que fija el borde, no «no registrada»').toMatch(IP);
    expect(Date.parse(suspension.ocurridoEn)).toBeLessThanOrEqual(Date.parse(suspension.fechaHora));

    const delBanner = await esperarEntradas(`banner:${banner.id}`, 2);
    const retiro = delBanner.find((e) => e.tipoAccion === 'ELIMINACION_LOGICA');
    expect(retiro).toMatchObject({
      administrador: admin.claims.uid,
      nombreAfectado: contenidoBanner,
      motivo: motivoRetiro,
    });
    expect(delBanner.find((e) => e.tipoAccion === 'CREACION')).toBeTruthy();

    // Las rondas siguientes vuelven a leer con solape: nada se duplica.
    await new Promise((resolver) => setTimeout(resolver, 7_000));
    expect(await bitacora(`producto:${producto.id}`)).toHaveLength(2);
    expect(await bitacora(`banner:${banner.id}`)).toHaveLength(2);
  });

  test('4 · las operaciones del catálogo solo las lee la bitácora central', async () => {
    const ruta = '/api/v1/productos/auditoria/operaciones';
    const sinToken = await api.get(ruta, { headers: { Accept: ACEPTA } });
    expect(sinToken.status()).toBe(401);
    for (const sesion of [admin, superAdmin]) {
      const r = await api.get(ruta, como(sesion));
      expect(r.status(), `${sesion.apodo}: ${await r.text()}`).toBe(403);
    }
  });
});
