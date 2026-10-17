// @ts-check
/**
 * Pieza única y producto premium identificados — SA-13, §7.2.1 («estado de
 * disponibilidad (activo/único/suspendido)») y §7.2.2 («los productos de pago
 * deben estar claramente identificados»), de punta a punta por el borde con
 * productos (1.11.0), ms-ecommerce (ecommerce-carrito.yaml 1.8.0) e inventario
 * reales:
 *
 *   1. el Administrador da de alta una pieza única (pide `estado: UNICO`) y un
 *      premium; un alta que pide nacer suspendida es 400;
 *   2. la vitrina los proyecta con `unico` y `premium`;
 *   3. en la tienda, cada tarjeta lleva su distintivo en palabras;
 *   4. la ficha del inventario, abierta sobre el catálogo, también.
 *
 * Los nombres llevan el sufijo de la corrida.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import {
  CLAVE_DEL_BANCO,
  cuentaAdministrativaDeRol,
  sesionDe as sesionDelBanco,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const SUFIJO = Date.now().toString(36);
const TIENDA = '/frontend/app-web/src/cuentas/tienda.html';
// Una imagen que ya sirve el propio borde (la misma que usan los demás specs).
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE_DEL_BANCO, base: BORDE });
}

const con = (sesion) => ({
  Authorization: `Bearer ${sesion.token}`,
  'Content-Type': 'application/json',
});

test.describe('Pieza única y producto premium (SA-13, §7.2.1, §7.2.2)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let jugadora;
  let unica;
  let premium;

  const base = {
    imagen: IMAGEN,
    tipo: 'ARMA',
    poderDeAtaque: 12,
    tasaDeCaida: 5,
  };

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, cuentaAdministrativaDeRol('ADMINISTRADOR').apodo);
    jugadora = await sesionDe(api, `marcas_${SUFIJO}`);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · el alta de una pieza única y de un premium; nacer suspendido no se admite', async () => {
    const altaUnica = await api.post('/api/v1/productos', {
      headers: con(admin),
      data: {
        ...base,
        nombre: `Corona del Nexo E2E ${SUFIJO}`,
        descripcion: 'Pieza única del E2E de SA-13.',
        tiraje: 1,
        premium: false,
        precioCreditos: 900,
        estado: 'UNICO',
      },
    });
    expect(altaUnica.status(), await altaUnica.text()).toBe(201);
    unica = await altaUnica.json();
    expect(unica.estado).toBe('UNICO');

    const altaPremium = await api.post('/api/v1/productos', {
      headers: con(admin),
      data: {
        ...base,
        nombre: `Hoja de diamante E2E ${SUFIJO}`,
        descripcion: 'Producto premium del E2E de SA-13.',
        tiraje: -1,
        premium: true,
        precioMonedaReal: 25000,
      },
    });
    expect(altaPremium.status(), await altaPremium.text()).toBe(201);
    premium = await altaPremium.json();
    expect(premium.estado).toBe('ACTIVO');
    expect(premium.premium).toBe(true);

    const suspendida = await api.post('/api/v1/productos', {
      headers: con(admin),
      data: {
        ...base,
        nombre: `No debería existir E2E ${SUFIJO}`,
        descripcion: 'Un alta que pide nacer suspendida.',
        tiraje: -1,
        premium: false,
        precioCreditos: 10,
        estado: 'SUSPENDIDO',
      },
    });
    expect(suspendida.status()).toBe(400);
  });

  test('2 · la vitrina los proyecta con sus marcas', async () => {
    // La vitrina guarda una copia del catálogo unos segundos: se espera.
    let tarjetas = [];
    await expect
      .poll(
        async () => {
          const r = await api.get(
            `/api/v1/vitrina?page=0&size=50&busqueda=${encodeURIComponent(`E2E ${SUFIJO}`)}`,
            { headers: con(jugadora) },
          );
          if (r.status() !== 200) {
            return -1;
          }
          tarjetas = (await r.json()).content ?? [];
          return tarjetas.filter((p) => [unica.id, premium.id].includes(p.id)).length;
        },
        { timeout: 60_000, message: 'la vitrina no proyectó los dos productos' },
      )
      .toBe(2);

    const deLaUnica = tarjetas.find((p) => p.id === unica.id);
    const delPremium = tarjetas.find((p) => p.id === premium.id);
    expect(deLaUnica).toMatchObject({ unico: true, premium: false });
    expect(delPremium).toMatchObject({ unico: false, premium: true });
  });

  test('3 · en la tienda, cada tarjeta lleva su distintivo en palabras', async ({ page }) => {
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugadora.token, jugadora.apodo, jugadora.claims.uid],
    );
    await page.goto(`${BORDE}${TIENDA}`);
    await expect(page.locator('#productos-grid .product-card').first()).toBeVisible({
      timeout: 20_000,
    });
    await page.locator('#busqueda-tienda').fill(`E2E ${SUFIJO}`);

    const tarjetaUnica = page.locator(`.product-card[data-id-producto="${unica.id}"]`);
    await expect(tarjetaUnica.locator('.producto-unico')).toHaveText('Pieza única', {
      timeout: 20_000,
    });
    await expect(tarjetaUnica.locator('.producto-premium')).toHaveCount(0);

    const tarjetaPremium = page.locator(`.product-card[data-id-producto="${premium.id}"]`);
    await expect(tarjetaPremium.locator('.producto-premium')).toContainText('Premium');
    await expect(tarjetaPremium.locator('.producto-premium')).toHaveAttribute(
      'title',
      'Se paga en moneda real y no se puede subastar',
    );
  });

  test('4 · el catálogo público dice lo mismo que lee la ficha del inventario', async () => {
    for (const [producto, esperado] of [
      [unica, { estado: 'UNICO', premium: false }],
      [premium, { estado: 'ACTIVO', premium: true }],
    ]) {
      const r = await api.get(`/api/v1/productos/${producto.id}`, { headers: con(jugadora) });
      expect(r.status(), await r.text()).toBe(200);
      expect(await r.json()).toMatchObject(esperado);
    }
  });
});
