// @ts-check
/**
 * D-44 (auditoría del 4-oct, cambio autorizado n.º 5) — comprar con los
 * créditos del juego, de punta a punta por el borde, con productos,
 * ms-ecommerce, ms-finanzas e inventario reales (ecommerce-carrito.yaml 1.6.0).
 *
 * Lo que se afirma:
 *
 *   1. la vitrina enseña el precio en créditos del catálogo (`precioCreditos`)
 *      y la cotización del carrito (`GET /checkout/creditos`) lo suma en el
 *      servidor con el saldo real de ms-finanzas;
 *   2. pagar con créditos: 201 COMPLETA, el saldo baja UNA vez y exactamente
 *      el precio, el producto llega al inventario y el libro de créditos tiene
 *      un solo débito, con el `refId` de la orden;
 *   3. la misma clave otra vez (un reintento) y dos clics a la vez con la
 *      misma clave: una sola orden y un solo cobro;
 *   4. sin saldo suficiente: 402 `saldo-insuficiente`, nada cobrado y el
 *      carrito intacto;
 *   5. la vista: «¿Cómo quieres pagar?» → Créditos del Nexo → saldo actual,
 *      precio y saldo después → «Confirmar compra» → «Compra realizada» →
 *      «Ver inventario».
 *   6. G3 (1.7.0): un producto que solo tiene precio en créditos se vende:
 *      sale en la vitrina con `precioFinal`/`moneda` a null y su precio en
 *      créditos, entra al carrito sin sumar al total en dinero real, con
 *      tarjeta es 422 `producto-sin-precio-en-moneda-real` sin orden, y con
 *      créditos se paga una vez y llega al inventario; en la vista, «Pagar»
 *      abre ya en créditos con el pago simulado apagado.
 *
 * El saldo de cada jugadora se siembra con la credencial de servicio del
 * banco (`POST /creditos/acreditar`, como en subastas.e2e.spec.js): un
 * jugador no se acredita solo. Las cifras se comparan contra el saldo leído
 * antes de comprar, no contra un número fijo: así no dependen de los
 * créditos de bienvenida del alta.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/cuentas/tienda.html';
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';
const BANCO = { id: 'e2e-banco', secreto: 'e2e-secreto-del-banco-de-pruebas' };
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function tokenDeServicio(api) {
  const r = await api.post('/api/v1/auth/token', {
    headers: {
      Authorization: `Basic ${Buffer.from(`${BANCO.id}:${BANCO.secreto}`).toString('base64')}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    data: 'grant_type=client_credentials',
  });
  expect(r.status(), `token de servicio: ${await r.text()}`).toBe(200);
  return (await r.json()).access_token;
}

async function acreditar(api, servicio, quien, monto, refId) {
  const r = await api.post(`${FINANZAS}/creditos/acreditar`, {
    headers: conToken(servicio),
    data: { uid: quien.claims.uid, monto, refId, concepto: 'semilla-compra-con-creditos-e2e' },
  });
  expect(r.status(), `acreditar a ${quien.claims.uid}: ${await r.text()}`).toBe(200);
}

/** El saldo disponible que ve la propia jugadora (ms-finanzas por el borde). */
async function saldoDe(api, quien) {
  const r = await api.get(`/api/v1/creditos/${quien.claims.uid}/saldo`, {
    headers: conToken(quien.token),
  });
  expect(r.status(), `saldo de ${quien.claims.uid}: ${await r.text()}`).toBe(200);
  return Number((await r.json()).saldoDisponible);
}

async function alCarrito(api, quien, productoId) {
  const r = await api.post('/api/v1/carrito/items', {
    headers: conToken(quien.token),
    data: { productoId, cantidad: 1 },
  });
  expect(r.status(), await r.text()).toBe(200);
}

function pagarConCreditos(api, quien, clave) {
  return api.post('/api/v1/checkout/creditos', {
    headers: { Authorization: `Bearer ${quien.token}`, 'Idempotency-Key': clave },
  });
}

/**
 * La vitrina entera, página a página (la pública, sin token). Una sola página
 * no basta en un banco que se reutiliza: cada corrida añade productos.
 */
async function vitrinaCompleta(api) {
  const productos = [];
  for (let pagina = 0; pagina < 20; pagina++) {
    const r = await api.get(`/api/v1/vitrina?page=${pagina}&size=50`);
    expect(r.status(), `vitrina, página ${pagina}: ${await r.text()}`).toBe(200);
    const cuerpo = await r.json();
    productos.push(...cuerpo.content);
    if (cuerpo.last) {
      break;
    }
  }
  return productos;
}

async function inventarioDe(api, quien) {
  const r = await api.get('/api/v1/inventario/elementos', { headers: conToken(quien.token) });
  expect(r.status(), await r.text()).toBe(200);
  return (await r.json()).elementos;
}

test.describe('D-44 — comprar con créditos del juego', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let servicio;
  const sufijo = Date.now();
  /** Precios en créditos de esta corrida, como los del catálogo (precioCreditos). */
  const PRECIOS = { hacha: 120, maza: 90, lanza: 60, corona: 900_000 };
  const ids = {};

  const producto = (nombre, precioCreditos) => ({
    nombre: `${nombre} con créditos E2E ${sufijo}`,
    imagen: IMAGEN,
    descripcion: 'Arma de prueba del E2E de la compra con créditos (D-44).',
    tipo: 'ARMA',
    tiraje: -1,
    premium: false,
    precioCreditos,
    precioMonedaReal: 9000,
    poderDeAtaque: 10,
    tasaDeCaida: 50,
  });

  /**
   * G3 (ecommerce-carrito 1.7.0): «vendible si tiene precio en dinero real O
   * en créditos». Este no tiene precio en dinero real: solo se vende en
   * créditos (no es premium, así que el alta lo admite sin él).
   */
  const PRECIO_SOLO_CREDITOS = 70;
  const soloEnCreditos = () => ({
    ...producto('amuleto', PRECIO_SOLO_CREDITOS),
    nombre: `Amuleto solo en créditos E2E ${sufijo}`,
    descripcion: 'Arma de prueba del E2E (G3): solo tiene precio en créditos.',
    // Sin precio en dinero real: JSON no escribe un campo undefined.
    precioMonedaReal: undefined,
  });

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, process.env.E2E_ADMIN ?? ADMIN);
    servicio = await tokenDeServicio(api);
    for (const [clave, precio] of Object.entries(PRECIOS)) {
      const r = await api.post('/api/v1/productos', {
        headers: conToken(admin.token),
        data: producto(clave, precio),
      });
      expect(r.status(), await r.text()).toBe(201);
      ids[clave] = (await r.json()).id;
      expect(ids[clave]).toMatch(UUID);
    }
    const alta = await api.post('/api/v1/productos', {
      headers: conToken(admin.token),
      data: soloEnCreditos(),
    });
    expect(alta.status(), await alta.text()).toBe(201);
    ids.soloCreditos = (await alta.json()).id;
    expect(ids.soloCreditos).toMatch(UUID);
    // La vitrina guarda una copia del catálogo 30 s: se espera a que estén.
    await expect
      .poll(
        async () => {
          const vitrina = await vitrinaCompleta(api);
          return Object.values(ids).every((id) => vitrina.some((p) => p.id === id));
        },
        { timeout: 45_000, intervals: [1_000, 2_000, 5_000] },
      )
      .toBe(true);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('la vitrina enseña el precio en créditos del catálogo', async () => {
    const hacha = (await vitrinaCompleta(api)).find((p) => p.id === ids.hacha);

    expect(hacha.precioCreditos).toBe(PRECIOS.hacha);
    expect(Number(hacha.precioFinal)).toBe(9000);
  });

  test('pagar con créditos: el saldo baja una vez y exactamente el precio; el producto llega al inventario', async () => {
    const jugadora = await sesionDe(api, `creditos_${sufijo}`);
    await acreditar(api, servicio, jugadora, 1000, `e2e-semilla-creditos-${sufijo}`);
    const antes = await saldoDe(api, jugadora);
    await alCarrito(api, jugadora, ids.hacha);

    // La cotización del servidor: lo que se enseña antes de confirmar.
    const cotizacion = await api.get('/api/v1/checkout/creditos', {
      headers: conToken(jugadora.token),
    });
    expect(cotizacion.status(), await cotizacion.text()).toBe(200);
    expect(await cotizacion.json()).toMatchObject({
      pagable: true,
      totalCreditos: PRECIOS.hacha,
      saldoDisponible: antes,
      saldoDespues: antes - PRECIOS.hacha,
      alcanza: true,
    });

    const clave = `e2e-creditos-compra-${sufijo}`;
    const pago = await pagarConCreditos(api, jugadora, clave);
    expect(pago.status(), await pago.text()).toBe(201);
    const orden = await pago.json();
    expect(orden).toMatchObject({
      estado: 'COMPLETA',
      formaDePago: 'CREDITOS',
      moneda: 'CREDITOS',
      total: PRECIOS.hacha,
      medioDePago: null,
    });
    expect(orden.lineas).toEqual([
      expect.objectContaining({
        productoId: ids.hacha,
        cantidad: 1,
        precioUnitario: PRECIOS.hacha,
      }),
    ]);
    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIOS.hacha);

    // La misma clave es la misma compra: 200, la misma orden, nada se cobra otra vez.
    const repetida = await pagarConCreditos(api, jugadora, clave);
    expect(repetida.status(), await repetida.text()).toBe(200);
    expect((await repetida.json()).id).toBe(orden.id);
    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIOS.hacha);

    // Entregado una vez.
    const elementos = await inventarioDe(api, jugadora);
    expect(elementos.filter((e) => e.productoId === ids.hacha)).toHaveLength(1);

    // Un solo débito en el libro de créditos, con el refId de la orden.
    const movimientos = await api.get(
      `/api/v1/creditos/${jugadora.claims.uid}/movimientos?size=50`,
      {
        headers: conToken(jugadora.token),
      },
    );
    expect(movimientos.status(), await movimientos.text()).toBe(200);
    const deLaOrden = (await movimientos.json()).content.filter(
      (m) => m.referenciaId === `tienda-orden-${orden.id}`,
    );
    expect(deLaOrden).toHaveLength(1);
    expect(deLaOrden[0]).toMatchObject({ tipo: 'DEBITO', signo: 'RESTA' });
    expect(Number(deLaOrden[0].monto)).toBe(PRECIOS.hacha);
  });

  test('dos clics a la vez con la misma clave: una orden y un solo cobro', async () => {
    const jugadora = await sesionDe(api, `creditos_doble_${sufijo}`);
    await acreditar(api, servicio, jugadora, 1000, `e2e-semilla-doble-${sufijo}`);
    const antes = await saldoDe(api, jugadora);
    await alCarrito(api, jugadora, ids.maza);

    const clave = `e2e-creditos-doble-${sufijo}`;
    const respuestas = await Promise.all([
      pagarConCreditos(api, jugadora, clave),
      pagarConCreditos(api, jugadora, clave),
    ]);
    const estados = respuestas.map((r) => r.status());
    expect(
      estados.filter((e) => e === 201),
      `estados: ${estados}`,
    ).toHaveLength(1);
    expect(
      estados.every((e) => [200, 201, 409].includes(e)),
      `estados: ${estados}`,
    ).toBe(true);

    // Si la otra llegó mientras se procesaba (409 compra-en-curso), reintentar
    // con la misma clave devuelve la misma orden.
    const final = await pagarConCreditos(api, jugadora, clave);
    expect(final.status(), await final.text()).toBe(200);
    expect((await final.json()).estado).toBe('COMPLETA');

    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIOS.maza);
    const ordenes = await (
      await api.get('/api/v1/ordenes', { headers: conToken(jugadora.token) })
    ).json();
    expect(ordenes.filter((o) => o.formaDePago === 'CREDITOS')).toHaveLength(1);
    const elementos = await inventarioDe(api, jugadora);
    expect(elementos.filter((e) => e.productoId === ids.maza)).toHaveLength(1);
  });

  test('sin saldo suficiente: 402 saldo-insuficiente, nada cobrado y el carrito intacto', async () => {
    const jugadora = await sesionDe(api, `creditos_pobre_${sufijo}`);
    const antes = await saldoDe(api, jugadora);
    expect(antes, 'la corona cuesta más de lo que tiene cualquier jugadora del banco').toBeLessThan(
      PRECIOS.corona,
    );
    await alCarrito(api, jugadora, ids.corona);

    const pago = await pagarConCreditos(api, jugadora, `e2e-creditos-pobre-${sufijo}`);

    expect(pago.status(), await pago.text()).toBe(402);
    const problema = await pago.json();
    expect(problema).toMatchObject({
      type: 'urn:nexus:problema:saldo-insuficiente',
      estado: 'RECHAZADA',
      totalCreditos: PRECIOS.corona,
    });
    expect(await saldoDe(api, jugadora)).toBe(antes);
    const carrito = await (
      await api.get('/api/v1/carrito', { headers: conToken(jugadora.token) })
    ).json();
    expect(carrito.items.map((i) => i.producto?.id)).toEqual([ids.corona]);
    const elementos = await inventarioDe(api, jugadora);
    expect(elementos.some((e) => e.productoId === ids.corona)).toBe(false);
  });

  test('la vista: «¿Cómo quieres pagar?» → Créditos del Nexo → saldo, precio y saldo después → «Ver inventario»', async ({
    page,
  }) => {
    const jugadora = await sesionDe(api, `creditos_ui_${sufijo}`);
    await acreditar(api, servicio, jugadora, 1000, `e2e-semilla-ui-${sufijo}`);
    const antes = await saldoDe(api, jugadora);
    await alCarrito(api, jugadora, ids.lanza);
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugadora.token, jugadora.apodo, jugadora.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);

    const pagar = page.locator('#btn-pagar');
    await expect(pagar).toBeEnabled({ timeout: 20_000 });
    await pagar.click();

    const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
    await expect(dialogo.getByText('¿Cómo quieres pagar?')).toBeVisible();
    await dialogo.getByLabel(/Créditos del Nexo/).check();

    const zona = dialogo.locator('[data-zona="creditos"]');
    const formato = (n) => `${n.toLocaleString('es-CO')} créditos`;
    await expect(zona.locator('[data-zona="saldo-actual"] dd')).toHaveText(formato(antes));
    await expect(zona.locator('[data-zona="precio"] dd')).toHaveText(formato(PRECIOS.lanza));
    await expect(zona.locator('[data-zona="saldo-despues"] dd')).toHaveText(
      formato(antes - PRECIOS.lanza),
    );

    const confirmar = dialogo.locator('[data-accion="confirmar-pago"]');
    await expect(confirmar).toHaveText(`Confirmar compra por ${formato(PRECIOS.lanza)}`);
    const cobro = page.waitForResponse(
      (r) => r.url().includes('/api/v1/checkout/creditos') && r.request().method() === 'POST',
    );
    await confirmar.click();
    const respuesta = await cobro;
    expect(respuesta.status()).toBe(201);
    expect(respuesta.request().headers()['idempotency-key']).toMatch(/^pago-/);

    await expect(dialogo.locator('.pago__resultado')).toContainText('Compra realizada');
    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIOS.lanza);

    await dialogo.locator('[data-accion="ver-inventario"]').click();
    await expect(page).toHaveURL(/inventario/);
    const elementos = await inventarioDe(api, jugadora);
    expect(elementos.filter((e) => e.productoId === ids.lanza)).toHaveLength(1);
  });

  test('G3 — solo en créditos: en la vitrina con su precio en créditos y sin precio en dinero real', async () => {
    const amuleto = (await vitrinaCompleta(api)).find((p) => p.id === ids.soloCreditos);

    expect(amuleto, 'se vende: tiene precio en créditos').toBeTruthy();
    expect(amuleto.precioCreditos).toBe(PRECIO_SOLO_CREDITOS);
    // Nunca «0 COP»: sin precio en dinero real los campos van a null.
    expect(amuleto.precioFinal).toBeNull();
    expect(amuleto.precioOriginal).toBeNull();
    expect(amuleto.moneda).toBeNull();
  });

  test('G3 — solo en créditos: al carrito; con tarjeta 422 y sin orden; con créditos, un cobro y al inventario', async () => {
    const jugadora = await sesionDe(api, `creditos_solo_${sufijo}`);
    await acreditar(api, servicio, jugadora, 1000, `e2e-semilla-solo-${sufijo}`);
    const antes = await saldoDe(api, jugadora);
    await alCarrito(api, jugadora, ids.soloCreditos);

    const carrito = await (
      await api.get('/api/v1/carrito', { headers: conToken(jugadora.token) })
    ).json();
    expect(carrito.items).toEqual([
      expect.objectContaining({
        disponible: true,
        soloEnCreditos: true,
        precioCreditos: PRECIO_SOLO_CREDITOS,
        subtotalCreditos: PRECIO_SOLO_CREDITOS,
        precioUnitario: null,
        subtotal: null,
      }),
    ]);
    expect(Number(carrito.total), 'no suma en dinero real').toBe(0);

    // Con tarjeta no hay nada que cobrar por él: 422, sin orden y sin cobro.
    const conTarjeta = await api.post('/api/v1/checkout', {
      headers: { ...conToken(jugadora.token), 'Idempotency-Key': `e2e-solo-tarjeta-${sufijo}` },
      data: {
        titular: 'Compradora E2E',
        numeroTarjeta: '4242 4242 4242 4242',
        vencimiento: '12/39',
        codigoSeguridad: '123',
        moneda: 'COP',
      },
    });
    expect(conTarjeta.status(), await conTarjeta.text()).toBe(422);
    expect((await conTarjeta.json()).type).toBe(
      'urn:nexus:problema:producto-sin-precio-en-moneda-real',
    );
    const sinOrdenes = await (
      await api.get('/api/v1/ordenes', { headers: conToken(jugadora.token) })
    ).json();
    expect(sinOrdenes).toHaveLength(0);

    // Con créditos: el precio del catálogo, una vez.
    const pago = await pagarConCreditos(api, jugadora, `e2e-solo-creditos-${sufijo}`);
    expect(pago.status(), await pago.text()).toBe(201);
    expect(await pago.json()).toMatchObject({
      estado: 'COMPLETA',
      formaDePago: 'CREDITOS',
      total: PRECIO_SOLO_CREDITOS,
    });
    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIO_SOLO_CREDITOS);
    const elementos = await inventarioDe(api, jugadora);
    expect(elementos.filter((e) => e.productoId === ids.soloCreditos)).toHaveLength(1);
  });

  test('G3 — la vista: «N créditos» en la tarjeta, al carrito, y «Pagar» abre ya en créditos', async ({
    page,
  }) => {
    const jugadora = await sesionDe(api, `creditos_solo_ui_${sufijo}`);
    await acreditar(api, servicio, jugadora, 1000, `e2e-semilla-solo-ui-${sufijo}`);
    const antes = await saldoDe(api, jugadora);
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [jugadora.token, jugadora.apodo, jugadora.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);
    const formato = (n) => `${n.toLocaleString('es-CO')} créditos`;

    await expect(page.locator('#productos-grid .product-card').first()).toBeVisible({
      timeout: 20_000,
    });
    await page.locator('#busqueda-tienda').fill(`Amuleto solo en créditos E2E ${sufijo}`);
    const tarjeta = page.locator(`.product-card[data-id-producto="${ids.soloCreditos}"]`);
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    await expect(tarjeta.locator('.price')).toHaveText(formato(PRECIO_SOLO_CREDITOS));
    await expect(tarjeta).not.toContainText('Precio no disponible');
    await expect(tarjeta).not.toContainText('COP');

    const alta = page.waitForResponse(
      (r) => r.url().includes('/api/v1/carrito/items') && r.request().method() === 'POST',
    );
    await tarjeta.locator('.btn-add').click();
    expect((await alta).status()).toBe(200);
    const linea = page.locator(`#cart-items .cart-item[data-solo-en-creditos="si"]`);
    await expect(linea.locator('.item-price')).toHaveText(formato(PRECIO_SOLO_CREDITOS));
    await expect(page.locator('#cart-total')).toHaveText('Con créditos del juego');

    const pagar = page.locator('#btn-pagar');
    await expect(pagar).toBeEnabled();
    await pagar.click();
    const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
    await expect(dialogo.getByLabel(/Créditos del Nexo/)).toBeChecked();
    await expect(dialogo.getByLabel(/Pago simulado/)).toBeDisabled();
    await expect(dialogo.locator('[data-motivo="solo-creditos"]')).toContainText(
      'solo se vende con créditos del juego',
    );
    const confirmar = dialogo.locator('[data-accion="confirmar-pago"]');
    await expect(confirmar).toHaveText(`Confirmar compra por ${formato(PRECIO_SOLO_CREDITOS)}`);

    const cobro = page.waitForResponse(
      (r) => r.url().includes('/api/v1/checkout/creditos') && r.request().method() === 'POST',
    );
    await confirmar.click();
    expect((await cobro).status()).toBe(201);
    await expect(dialogo.locator('.pago__resultado')).toContainText('Compra realizada');
    expect(await saldoDe(api, jugadora)).toBe(antes - PRECIO_SOLO_CREDITOS);
  });
});
