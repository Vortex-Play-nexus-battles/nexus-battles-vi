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
 *   5. (UXC-3, B3) el detalle trae la calificación y las opiniones del servicio
 *      real: se opina con una imagen de verdad (se sube al elegirla y el
 *      comentario viaja con su id) y la imagen se ve en el hilo; se califica
 *      SIN comentar, una sola vez, y el segundo intento no se ofrece ni lo
 *      admite el servicio (7.1: «solo pueden calificar un producto una vez»).
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
 *
 * ## B5 — la compra (ecommerce-carrito.yaml 1.4.0)
 *
 * Las últimas pruebas pagan de verdad contra la pasarela simulada: una
 * compra aprobada (tarjeta 4242) que termina COMPLETA con el producto en el
 * inventario y el carrito vacío, la misma clave que devuelve la misma orden,
 * y un rechazo (tarjeta 0002) que deja el carrito intacto; y la compra desde
 * la vista, con el formulario de §7.5.
 *
 * **Necesitan B4 fusionado**: la reserva de tiraje (productos 1.4.0,
 * `POST /productos/{id}/adquisiciones`) y la entrega (inventario 1.5.0,
 * `POST /inventario/entregas`). Sin ellas la compra se compensa (409
 * `compra-reembolsada`) y estas pruebas se ponen en rojo, que es justo la
 * señal que tienen que dar.
 *
 * El correo de confirmación no se comprueba aquí: este banco no tiene
 * servicio de correo ni Mailpit, así que ms-ecommerce corre con
 * `TIENDA_CORREO_HABILITADO=false` (compose.yml) y la orden termina con el
 * correo OMITIDO. El único ayudante de Mailpit de la rama (`correosPara`, en
 * `activacion-del-jugador.smoke.spec.js`) mira el entorno desplegado, no este
 * banco. Quien añada el correo al banco cambia esa aserción por la del buzón.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ADMIN = process.env.E2E_ADMIN ?? 'admin_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/cuentas/tienda.html';
// Una imagen que ya sirve el propio borde: la tarjeta la pide al pintarse, y
// una URL de fuera haría salir al navegador del banco a internet.
const IMAGEN = '/frontend/app-web/src/cuentas/avatares/arquero-cazador.jpg';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PRECIO_EN_DINERO_REAL = 45000;
/** Un PNG de verdad de 3x2 (el mismo que usan las pruebas del servicio de comentarios). */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAMAAAACCAIAAAASFvFNAAAAHElEQVR42mNkYGBQZTBVZTBlYYg2ZWAwZWAwBQAPAgG01W6s7gAAAABJRU5ErkJggg==',
  'base64',
);

/**
 * B1 — la cuenta nace pendiente de verificar su correo. Registrar, leer el
 * codigo del buzon, confirmarlo y entrar viven en un solo sitio
 * (`ayudantes/cuentas.js`); aqui solo se fija la contrasena de este spec.
 */
function sesionDe(api, apodo) {
  return sesionDelBanco(api, apodo, { clave: CLAVE, base: BORDE });
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/** B5 — una petición de pago: el token y la clave de idempotencia del intento. */
function conClave(token, clave) {
  return { ...conToken(token), 'Idempotency-Key': clave };
}

/**
 * Tarjetas de la pasarela simulada (README de ms-ecommerce): cualquiera que
 * pase Luhn con vencimiento futuro se aprueba, salvo la terminada en 0002.
 */
const TARJETA_APROBADA = {
  titular: 'Compradora E2E',
  numeroTarjeta: '4242 4242 4242 4242',
  vencimiento: '12/39',
  codigoSeguridad: '123',
};
const TARJETA_RECHAZADA = { ...TARJETA_APROBADA, numeroTarjeta: '4000 0000 0000 0002' };

/** Pone un producto en el carrito de una sesión y devuelve el carrito. */
async function alCarrito(api, sesion, productoId) {
  const alta = await api.post('/api/v1/carrito/items', {
    headers: conToken(sesion.token),
    data: { productoId, cantidad: 1 },
  });
  expect(alta.status(), await alta.text()).toBe(200);
  return alta.json();
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

    // UXC-4 — la tienda pagina de dieciséis en dieciséis y ya se puede buscar:
    // en un banco que se reutiliza, los productos de esta corrida pueden no
    // estar en la primera página. Se buscan por su sufijo, que es irrepetible
    // y comparten los dos, como lo haría una persona.
    await expect(page.locator('#productos-grid .product-card').first()).toBeVisible({
      timeout: 20_000,
    });
    await page.locator('#busqueda-tienda').fill(`E2E ${sufijo}`);

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
  test('el detalle del producto trae su calificación y sus opiniones, del servicio real (UXC-3)', async ({
    page,
  }) => {
    // Una opinión con estrellas publicada por la API, como la dejaría otra
    // jugadora desde su propia ficha.
    const opinion = await api.post(`/api/v1/products/${idEnDineroReal}/comments`, {
      headers: conToken(compradora.token),
      data: { texto: `Filo excelente, la recomiendo ${sufijo}`, estrellas: 4 },
    });
    expect(opinion.status(), await opinion.text()).toBe(201);

    const lectora = await sesionDe(api, `tienda_lee_${sufijo}`);
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [lectora.token, lectora.apodo, lectora.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);
    await page.locator('#busqueda-tienda').fill(`E2E ${sufijo}`);
    await page.locator(`[data-ver-producto="${idEnDineroReal}"]`).click();

    const ficha = page.locator('[role="dialog"].ficha');
    await expect(ficha.locator('.ficha__nombre')).toHaveText(enDineroReal.nombre);
    // El promedio lo calcula el servicio; la ficha lo pinta bajo el tipo.
    await expect(ficha.locator('.ficha__valoracion')).toContainText('1 valoración', {
      timeout: 20_000,
    });
    const ajena = ficha.locator('.hilo-comentarios .comentario', {
      hasText: `Filo excelente, la recomiendo ${sufijo}`,
    });
    await expect(ajena).toBeVisible();
    await expect(ajena.locator('[data-accion="reportar-comentario"]')).toBeVisible();
    await expect(ficha.locator('.compra-producto')).toContainText('45.000 COP');

    // Y se opina desde el mismo detalle, con una imagen de verdad (B3): se
    // sube al elegirla, el comentario viaja con su id y entra en el hilo,
    // marcada como propia y con la imagen a la vista.
    await ficha.locator('.redactor-comentario textarea').fill(`Llegó rápido ${sufijo}`);
    const subida = page.waitForResponse(
      (r) => r.url().includes('/api/v1/comentarios/imagenes') && r.request().method() === 'POST',
    );
    await ficha
      .locator('.redactor-comentario input[type="file"]')
      .setInputFiles({ name: 'captura.png', mimeType: 'image/png', buffer: PNG });
    expect((await subida).status()).toBe(201);
    await expect(ficha.locator('.redactor-comentario__miniatura[data-estado="lista"]')).toHaveCount(
      1,
    );

    const publicada = page.waitForResponse(
      (r) => r.url().includes('/comments') && r.request().method() === 'POST',
    );
    await ficha.locator('[data-accion="publicar-opinion"]').click();
    const respuesta = await publicada;
    expect(respuesta.status()).toBe(201);
    const cuerpo = respuesta.request().postDataJSON();
    expect(cuerpo.texto).toBe(`Llegó rápido ${sufijo}`);
    // El redactor ya no califica: las estrellas van por su cuenta.
    expect(cuerpo).not.toHaveProperty('estrellas');
    expect(cuerpo.imagenes).toHaveLength(1);
    expect(cuerpo.imagenes[0]).toMatch(UUID);

    const propia = ficha.locator('.hilo-comentarios .comentario', {
      hasText: `Llegó rápido ${sufijo}`,
    });
    await expect(propia).toBeVisible({ timeout: 20_000 });
    await expect(propia.locator('.comentario__propio')).toHaveText('Tú');
    const imagen = propia.locator('img.comentario__imagen');
    await expect(imagen).toBeVisible();
    await expect(imagen).toHaveAttribute(
      'src',
      `/api/v1/comentarios/imagenes/${cuerpo.imagenes[0]}`,
    );
    // Se ve de verdad: el navegador la descargó y la decodificó.
    await expect
      .poll(() => imagen.evaluate((img) => (img.complete ? img.naturalWidth : 0)))
      .toBeGreaterThan(0);
  });

  test('se califica sin comentar, una sola vez: la segunda ni se ofrece ni la admite el servicio (7.1, B3)', async ({
    page,
  }) => {
    const calificadora = await sesionDe(api, `tienda_califica_${sufijo}`);
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [calificadora.token, calificadora.apodo, calificadora.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);
    await page.locator('#busqueda-tienda').fill(`E2E ${sufijo}`);
    await page.locator(`[data-ver-producto="${idEnDineroReal}"]`).click();

    const ficha = page.locator('[role="dialog"].ficha');
    // Sin calificación propia (`rating/mia` 404): se ofrecen las estrellas.
    const control = ficha.locator('.calificar-producto');
    await expect(control).toHaveAttribute('data-estado', 'pendiente', { timeout: 20_000 });
    await expect(control.getByRole('group', { name: 'Califica este producto' })).toBeVisible();

    // Cuatro estrellas, y «Calificar»: sin escribir ninguna opinión.
    await control.locator('label.selector-estrellas__opcion').nth(3).click();
    const calificada = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/products/${idEnDineroReal}/rating`) && r.request().method() === 'POST',
    );
    await control.locator('[data-accion="calificar"]').click();
    const respuesta = await calificada;
    expect(respuesta.status()).toBe(201);
    expect(respuesta.request().postDataJSON()).toEqual({ estrellas: 4 });
    const { resumen } = await respuesta.json();

    await expect(control).toHaveAttribute('data-estado', 'calificado');
    await expect(control).toContainText('Tu calificación: 4 de 5');
    await expect(control.locator('input[type="radio"]')).toHaveCount(0);
    // El resumen es el que devolvió el servicio, no una cuenta de la vista.
    expect(resumen.total).toBeGreaterThanOrEqual(2);
    await expect(ficha.locator('.ficha__valoracion')).toContainText(
      `${resumen.total} valoraciones`,
    );

    // El segundo intento no se ofrece: al volver a abrir, la suya, sin estrellas.
    // El botón de cerrar vive en la capa de la ficha, no dentro del diálogo
    // (`ficha-producto.js`): se busca por su nombre accesible.
    await page.getByRole('button', { name: 'Cerrar la ficha del producto' }).click();
    await expect(ficha).toHaveCount(0);
    await page.locator(`[data-ver-producto="${idEnDineroReal}"]`).click();
    await expect(control).toHaveAttribute('data-estado', 'calificado', { timeout: 20_000 });
    await expect(control).toContainText('Tu calificación: 4 de 5');
    await expect(control.locator('input, button')).toHaveCount(0);

    // Y el servicio tampoco lo admite (ni cambia la que había).
    const otra = await api.post(`/api/v1/products/${idEnDineroReal}/rating`, {
      headers: conToken(calificadora.token),
      data: { estrellas: 1 },
    });
    expect(otra.status()).toBe(409);
    expect((await otra.json()).type).toMatch(/ya-calificado$/);
    const mia = await api.get(`/api/v1/products/${idEnDineroReal}/rating/mia`, {
      headers: conToken(calificadora.token),
    });
    expect((await mia.json()).estrellas).toBe(4);
  });

  test('si otra pestaña calificó primero, «Calificar» lo explica y enseña la que cuenta (409, B3)', async ({
    page,
  }) => {
    const jugadora = await sesionDe(api, `tienda_dos_${sufijo}`);
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
    await page.locator('#busqueda-tienda').fill(`E2E ${sufijo}`);
    await page.locator(`[data-ver-producto="${idEnDineroReal}"]`).click();
    const control = page.locator('[role="dialog"].ficha .calificar-producto');
    await expect(control).toHaveAttribute('data-estado', 'pendiente', { timeout: 20_000 });

    // Mientras la ficha está abierta, la misma jugadora califica por otro lado.
    const primera = await api.post(`/api/v1/products/${idEnDineroReal}/rating`, {
      headers: conToken(jugadora.token),
      data: { estrellas: 2 },
    });
    expect(primera.status(), await primera.text()).toBe(201);

    await control.locator('label.selector-estrellas__opcion').nth(4).click();
    const segunda = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/products/${idEnDineroReal}/rating`) && r.request().method() === 'POST',
    );
    await control.locator('[data-accion="calificar"]').click();
    expect((await segunda).status()).toBe(409);

    // No es un error suyo: se dice, y se enseña la calificación que cuenta.
    await expect(control).toContainText('Ya habías calificado este producto');
    await expect(control).toContainText('Tu calificación: 2 de 5');
    await expect(control.locator('input[type="radio"]')).toHaveCount(0);
  });

  test('la portada pública enseña la tienda, su detalle y lleva a entrar para comprar (UXC-4)', async ({
    browser,
  }) => {
    // Sin sesión: la vitrina y las opiniones son públicas.
    const contexto = await browser.newContext();
    const page = await contexto.newPage();
    try {
      await page.goto(`${BORDE}/login`);
      const tarjetas = page.locator('.vitrina-publica .product-card');
      await expect(tarjetas.first()).toBeVisible({ timeout: 20_000 });
      // Sin carrito que llenar: la portada no ofrece «Añadir».
      await expect(page.locator('.vitrina-publica .btn-add')).toHaveCount(0);

      await tarjetas.first().locator('[data-ver-producto]').click();
      const ficha = page.locator('[role="dialog"].ficha');
      await expect(ficha.locator('.hilo-comentarios')).toBeVisible({ timeout: 20_000 });
      await expect(ficha.locator('.redactor-comentario')).toHaveCount(0);
      await expect(ficha.locator('[data-accion="entrar-para-opinar"]')).toBeVisible();

      await ficha.locator('[data-accion="entrar-para-comprar"]').click();
      await expect(page.locator('#email')).toBeFocused();
      // La vuelta queda escrita: al entrar, la tienda.
      expect(new URL(page.url()).searchParams.get('volver')).toMatch(/tienda\.html$/);
    } finally {
      await contexto.close();
    }
  });

  test('B5 — compra con la 4242: COMPLETA, el producto en el inventario y el carrito vacío', async () => {
    const compradoraB5 = await sesionDe(api, `tienda_paga_${sufijo}`);
    const carrito = await alCarrito(api, compradoraB5, idEnDineroReal);
    const linea = carrito.items.find((i) => i.producto?.id === idEnDineroReal);

    // La cantidad se cambia en su línea (1.4.0) y el total lo recalcula el servidor.
    const cambio = await api.put(`/api/v1/carrito/items/${linea.id}/cantidad`, {
      headers: conToken(compradoraB5.token),
      data: { cantidad: 2 },
    });
    expect(cambio.status(), await cambio.text()).toBe(200);
    expect(Number((await cambio.json()).total)).toBe(2 * PRECIO_EN_DINERO_REAL);

    const clave = `e2e-compra-${sufijo}`;
    const pago = await api.post('/api/v1/checkout', {
      headers: conClave(compradoraB5.token, clave),
      data: { ...TARJETA_APROBADA, moneda: 'COP' },
    });
    expect(pago.status(), await pago.text()).toBe(201);
    const orden = await pago.json();
    expect(orden.estado).toBe('COMPLETA');
    expect(Number(orden.total)).toBe(2 * PRECIO_EN_DINERO_REAL);
    expect(orden.moneda).toBe('COP');
    expect(orden.lineas).toEqual([
      expect.objectContaining({ productoId: idEnDineroReal, cantidad: 2 }),
    ]);
    // De la tarjeta solo quedan la marca y los cuatro últimos: ni el número,
    // ni el titular, ni el código (que se busca por el nombre del campo: tres
    // cifras sueltas pueden salir en un UUID o en una hora).
    expect(orden.medioDePago).toEqual({ marca: 'VISA', ultimos4: '4242' });
    const comoTexto = JSON.stringify(orden);
    expect(comoTexto).not.toContain('4242 4242');
    expect(comoTexto).not.toContain('4242424242424242');
    expect(comoTexto).not.toContain(TARJETA_APROBADA.titular);
    expect(comoTexto).not.toMatch(/numeroTarjeta|codigoSeguridad|titular/);
    // Este banco no tiene servicio de correo (ver la cabecera).
    expect(orden.correoConfirmacion).toBe('OMITIDO');

    // La misma clave es la misma compra: 200, la misma orden, nada se repite.
    const repetida = await api.post('/api/v1/checkout', {
      headers: conClave(compradoraB5.token, clave),
      data: { ...TARJETA_APROBADA, moneda: 'COP' },
    });
    expect(repetida.status(), await repetida.text()).toBe(200);
    expect((await repetida.json()).id).toBe(orden.id);

    // Entregado: dos unidades en el inventario, ni una más.
    const inventario = await (
      await api.get('/api/v1/inventario/elementos', { headers: conToken(compradoraB5.token) })
    ).json();
    expect(inventario.elementos.filter((e) => e.productoId === idEnDineroReal)).toHaveLength(2);

    // Lo comprado salió del carrito.
    const despues = await (
      await api.get('/api/v1/carrito', { headers: conToken(compradoraB5.token) })
    ).json();
    expect(despues.items).toHaveLength(0);

    // «Mis compras»: la suya sí; la de otra jugadora, no existe para ella.
    const ordenes = await (
      await api.get('/api/v1/ordenes', { headers: conToken(compradoraB5.token) })
    ).json();
    expect(ordenes.map((o) => o.id)).toContain(orden.id);
    const ajena = await api.get(`/api/v1/ordenes/${orden.id}`, {
      headers: conToken(compradora.token),
    });
    expect(ajena.status()).toBe(404);

    // Y la vitrina con su sesión ya lo marca como propio.
    const vitrina = await vitrinaCompleta(api, compradoraB5.token);
    expect(vitrina.find((p) => p.id === idEnDineroReal)?.esPropio).toBe(true);
  });

  test('B5 — la 0002 se rechaza: 402, orden RECHAZADA con motivo y el carrito intacto', async () => {
    const rechazada = await sesionDe(api, `tienda_rechazo_${sufijo}`);
    await alCarrito(api, rechazada, idEnDineroReal);

    const pago = await api.post('/api/v1/checkout', {
      headers: conClave(rechazada.token, `e2e-rechazo-${sufijo}`),
      data: { ...TARJETA_RECHAZADA, moneda: 'COP' },
    });

    expect(pago.status(), await pago.text()).toBe(402);
    const problema = await pago.json();
    expect(problema.type).toBe('urn:nexus:problema:pago-rechazado');
    expect(problema.estado).toBe('RECHAZADA');
    expect(problema.motivo).toMatch(/fondos insuficientes/);
    expect(problema.ordenId).toMatch(UUID);

    const carrito = await (
      await api.get('/api/v1/carrito', { headers: conToken(rechazada.token) })
    ).json();
    expect(carrito.items.map((i) => i.producto?.id)).toEqual([idEnDineroReal]);
    const inventario = await (
      await api.get('/api/v1/inventario/elementos', { headers: conToken(rechazada.token) })
    ).json();
    expect(inventario.elementos.some((e) => e.productoId === idEnDineroReal)).toBe(false);
  });

  test('B5 — la vista: «Pagar», el formulario de §7.5 y «Mis compras»', async ({ page }) => {
    const enLaVista = await sesionDe(api, `tienda_ui_paga_${sufijo}`);
    await alCarrito(api, enLaVista, idEnDineroReal);
    await page.addInitScript(
      ([token, nombre, uid]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
        sessionStorage.setItem('nexus.rolActual', 'JUGADOR');
        sessionStorage.setItem('nexus.usuarioId', uid);
      },
      [enLaVista.token, enLaVista.apodo, enLaVista.claims.uid],
    );
    await page.goto(`${BORDE}${VISTA}`);

    const pagar = page.locator('#btn-pagar');
    await expect(pagar).toBeEnabled({ timeout: 20_000 });
    await pagar.click();

    const dialogo = page.getByRole('dialog', { name: 'Pagar tu compra' });
    await expect(dialogo.locator('.pago__total')).toContainText('45.000 COP');
    await dialogo.getByLabel('Nombre del titular de la tarjeta').fill(TARJETA_APROBADA.titular);
    await dialogo.getByLabel('Número de tarjeta').fill(TARJETA_APROBADA.numeroTarjeta);
    await dialogo.getByLabel('Fecha de vencimiento (MM/AA)').fill(TARJETA_APROBADA.vencimiento);
    await dialogo.getByLabel('Código de seguridad').fill(TARJETA_APROBADA.codigoSeguridad);

    const cobro = page.waitForResponse(
      (r) => r.url().includes('/api/v1/checkout') && r.request().method() === 'POST',
    );
    await dialogo.locator('[data-accion="confirmar-pago"]').click();
    const respuesta = await cobro;
    expect(respuesta.status()).toBe(201);
    expect(respuesta.request().headers()['idempotency-key']).toMatch(/^pago-/);

    await expect(dialogo.locator('.pago__resultado')).toContainText('Compra completada');
    // El número y el código no quedaron en el navegador (el número entero, con
    // o sin espacios: cuatro cifras sueltas pueden salir en el token).
    const guardado = await page.evaluate(() =>
      JSON.stringify({ ...sessionStorage, ...localStorage }),
    );
    expect(guardado).not.toContain('4242 4242 4242 4242');
    expect(guardado).not.toContain('4242424242424242');
    expect(guardado).not.toMatch(/numeroTarjeta|codigoSeguridad/);

    await dialogo.locator('[data-accion="ver-mis-compras"]').click();
    const compras = page.getByRole('dialog', { name: 'Mis compras' });
    await expect(compras.locator('.compra').first()).toContainText('Completada');
    await expect(page.locator('#cart-items')).toContainText('vacío');
  });
});
