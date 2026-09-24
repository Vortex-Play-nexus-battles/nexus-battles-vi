// @ts-check
/**
 * La tienda sobre el catálogo maestro — R16 (#421), de punta a punta por el
 * borde, con productos, ms-ecommerce y ms-identidad reales.
 *
 * ## Por qué existe
 *
 * La tienda no enseñaba ningún producto: ms-ecommerce leía una tabla propia
 * que nace vacía y no está conectada a nada, mientras el catálogo maestro del
 * servicio `productos` tenía los suyos. Desde R16 la vitrina proyecta ese
 * catálogo (`GET /api/v1/vitrina`, ecommerce-carrito.yaml 1.2.0) y el carrito
 * busca en él el producto que se añade. Ningún banco lo comprobaba: la tienda
 * ni siquiera estaba en este compose.
 *
 * Lo que se afirma, en orden:
 *
 *   1. un administrador da de alta dos productos por la API real del catálogo
 *      (`POST /api/v1/productos` por el borde, que desde R16 es de
 *      `productos` para todos los métodos): uno con precio en créditos Y en
 *      dinero real, otro solo en créditos;
 *   2. una jugadora recién registrada ve el primero en la vitrina, con su
 *      UUID, su precio en dinero real y COP; el segundo no aparece, porque la
 *      tienda solo vende lo que tiene precio en dinero real;
 *   3. `POST /api/v1/carrito/items` con ese UUID responde 200 y una línea con
 *      su nombre y su subtotal;
 *   4. la vista: la tarjeta lleva el nombre y el precio en COP, y «Añadir» lo
 *      pone en el panel del carrito.
 *
 * Los nombres llevan `Date.now()`: repetir la corrida contra el mismo banco
 * crea productos nuevos en vez de chocar con los de la anterior. La API se
 * recorre página a página, pero la vista pinta solo la primera página de la
 * vitrina (16, la tienda aún no pagina): en un banco recién levantado —CI, o
 * `levantar.sh` tras un `down -v`— el producto de esta corrida está en ella;
 * después de muchas corridas sin desmontar, dejaría de estarlo.
 *
 * Depende del listado del catálogo (`GET /api/v1/productos`, productos.yaml
 * 1.2.0, #687): sin él la vitrina no tiene de dónde leer y responde 503.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/cuentas/tienda.html';
// Una imagen que ya sirve el propio borde: la tarjeta la pide al pintarse, y
// una URL de fuera haría salir al navegador del banco a internet.
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PRECIO_EN_DINERO_REAL = 45000;

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

/**
 * La vitrina entera, página a página, con el máximo de 50 que admite el
 * contrato. Una sola página no basta en un banco que se reutiliza: cada
 * corrida añade productos, y el catálogo los lista por fecha de alta.
 */
async function vitrinaCompleta(api, token) {
  const productos = [];
  for (let pagina = 0; pagina < 20; pagina++) {
    const r = await api.get(`/api/v1/vitrina?page=${pagina}&size=50`, {
      headers: conToken(token),
    });
    expect(r.status(), `vitrina, página ${pagina}: ${await r.text()}`).toBe(200);
    const cuerpo = await r.json();
    productos.push(...cuerpo.content);
    if (cuerpo.last) {
      break;
    }
  }
  return productos;
}

test.describe('Tienda sobre el catálogo maestro (R16, #421)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let admin;
  let compradora;
  const sufijo = Date.now();
  const enDineroReal = {
    nombre: `Espada de la tienda E2E ${sufijo}`,
    imagen: IMAGEN,
    descripcion: 'Arma de prueba del E2E de la tienda: se vende en créditos y en dinero real.',
    tipo: 'ARMA',
    tiraje: -1,
    premium: false,
    precioCreditos: 150,
    precioMonedaReal: PRECIO_EN_DINERO_REAL,
    poderDeAtaque: 12,
    tasaDeCaida: 50,
  };
  const soloCreditos = {
    nombre: `Daga solo en créditos E2E ${sufijo}`,
    imagen: IMAGEN,
    descripcion: 'Arma de prueba del E2E de la tienda: solo tiene precio en créditos.',
    tipo: 'ARMA',
    tiraje: -1,
    premium: false,
    precioCreditos: 80,
    poderDeAtaque: 7,
    tasaDeCaida: 50,
  };
  let idEnDineroReal;
  let idSoloCreditos;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    admin = await sesionDe(api, ADMIN);
    // Una jugadora que no existía: su carrito empieza vacío y es solo suyo.
    compradora = await sesionDe(api, `tienda_${sufijo}`);
    expect(admin.claims.rol, 'sembrar.sh deja a admin_e2e como ADMINISTRADOR').toBe(
      'ADMINISTRADOR',
    );
    expect(compradora.claims.rol).toBe('JUGADOR');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('un administrador da de alta en el catálogo uno en dinero real y otro solo en créditos', async () => {
    // Por el borde y con todos los métodos yendo a `productos`: hasta R16 el
    // borde repartía /api/v1/productos por método y el GET exacto se lo
    // llevaba la vitrina de ms-ecommerce.
    const primero = await api.post('/api/v1/productos', {
      headers: conToken(admin.token),
      data: enDineroReal,
    });
    expect(primero.status(), await primero.text()).toBe(201);
    idEnDineroReal = (await primero.json()).id;
    expect(idEnDineroReal).toMatch(UUID);

    const segundo = await api.post('/api/v1/productos', {
      headers: conToken(admin.token),
      data: soloCreditos,
    });
    expect(segundo.status(), await segundo.text()).toBe(201);
    idSoloCreditos = (await segundo.json()).id;
    expect(idSoloCreditos).toMatch(UUID);

    // Una jugadora no da de alta nada en el catálogo.
    const jugadoraCrea = await api.post('/api/v1/productos', {
      headers: conToken(compradora.token),
      data: { ...enDineroReal, nombre: `Intruso E2E ${sufijo}` },
    });
    expect(jugadoraCrea.status()).toBe(403);
  });

  test('la vitrina proyecta el catálogo: el de dinero real está, con su UUID y en COP; el de créditos no', async () => {
    // La vitrina guarda una copia del catálogo 30 s (VitrinaDelCatalogoService):
    // es lo que puede tardar en verse un alta. Se pregunta hasta que aparece,
    // con un techo que cubre la copia entera y algo de margen.
    let vitrina = [];
    await expect
      .poll(
        async () => {
          vitrina = await vitrinaCompleta(api, compradora.token);
          return vitrina.some((p) => p.id === idEnDineroReal);
        },
        { timeout: 45_000, intervals: [1_000, 2_000, 5_000] },
      )
      .toBe(true);

    const enVenta = vitrina.find((p) => p.id === idEnDineroReal);
    expect(enVenta.nombre).toBe(enDineroReal.nombre);
    expect(enVenta.id).toMatch(UUID);
    expect(Number(enVenta.precioFinal)).toBe(PRECIO_EN_DINERO_REAL);
    expect(Number(enVenta.precioOriginal)).toBe(PRECIO_EN_DINERO_REAL);
    expect(enVenta.moneda).toBe('COP');
    expect(enVenta.enPromocion).toBe(false);
    expect(enVenta.tipo).toBe('ARMA');

    // Misma copia, así que si el de créditos se vendiera estaría aquí.
    expect(vitrina.some((p) => p.id === idSoloCreditos)).toBe(false);
    expect(vitrina.some((p) => p.nombre === soloCreditos.nombre)).toBe(false);
  });

  test('añadir al carrito por el UUID: 200 y una línea con su nombre y su subtotal', async () => {
    const r = await api.post('/api/v1/carrito/items', {
      headers: conToken(compradora.token),
      data: { productoId: idEnDineroReal, cantidad: 1 },
    });
    expect(r.status(), await r.text()).toBe(200);
    const carrito = await r.json();

    expect(carrito.usuarioId).toBe(compradora.claims.uid);
    const linea = carrito.items.find((i) => i.producto?.id === idEnDineroReal);
    expect(linea, JSON.stringify(carrito)).toBeTruthy();
    expect(linea.producto.nombre).toBe(enDineroReal.nombre);
    expect(linea.cantidad).toBe(1);
    expect(Number(linea.precioUnitario)).toBe(PRECIO_EN_DINERO_REAL);
    expect(Number(linea.subtotal)).toBe(PRECIO_EN_DINERO_REAL);
    expect(carrito.moneda).toBe('COP');

    // Y el que solo se vende en créditos no entra por la puerta de atrás.
    const deCreditos = await api.post('/api/v1/carrito/items', {
      headers: conToken(compradora.token),
      data: { productoId: idSoloCreditos, cantidad: 1 },
    });
    expect(deCreditos.status()).toBe(422);
    expect((await deCreditos.json()).type).toBe(
      'urn:nexus:problema:producto-sin-precio-en-moneda-real',
    );
  });

  test('la vista: la tarjeta con su nombre y precio en COP, y «Añadir» la lleva al carrito', async ({
    page,
  }) => {
    // Otra jugadora recién creada: su carrito empieza vacío, así que lo que
    // aparezca en el panel lo puso este clic y no la prueba anterior.
    const visitante = await sesionDe(api, `tienda_ui_${sufijo}`);
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [visitante.token, visitante.apodo, visitante.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);

    const tarjeta = page.locator('.product-card', { hasText: enDineroReal.nombre });
    await expect(tarjeta).toBeVisible({ timeout: 20_000 });
    await expect(tarjeta.locator('.price')).toHaveText(/45\.000 COP/);
    // El id viaja en el botón tal cual: el UUID en texto, no un número.
    await expect(tarjeta.locator('[data-producto]')).toHaveAttribute(
      'data-producto',
      idEnDineroReal,
    );
    // El de créditos no se ofrece en la tienda.
    await expect(page.locator('.product-card', { hasText: soloCreditos.nombre })).toHaveCount(0);

    // Antes de pulsar, el carrito ya cargó y está vacío.
    await expect(page.locator('#cart-items')).toContainText('vacío');

    const alta = page.waitForResponse(
      (r) => r.url().includes('/api/v1/carrito/items') && r.request().method() === 'POST',
    );
    await tarjeta.getByRole('button', { name: 'Añadir' }).click();
    const respuesta = await alta;
    expect(respuesta.status()).toBe(200);
    expect(respuesta.request().postDataJSON()).toEqual({
      productoId: idEnDineroReal,
      cantidad: 1,
    });

    const linea = page.locator('#cart-items .cart-item', { hasText: enDineroReal.nombre });
    await expect(linea).toBeVisible();
    await expect(linea).toContainText('x1');
    await expect(linea.locator('.item-price')).toHaveText(/45\.000 COP/);
    await expect(page.locator('#cart-total')).toHaveText(/45\.000 COP/);
    // Salió bien: ningún aviso de fallo en el carrito.
    await expect(page.locator('#aviso-carrito')).toBeHidden();
  });
});
