// @ts-check
/**
 * Subastas por el borde, con el token real de ms-identidad — primer corte del
 * bloque de subastas (HU-SUB-011 listado, HU-SUB-001 publicar, HU-SUB-004 pujar).
 *
 * Lo que este corte demuestra es lo que en el host de dev daba 502 y, de
 * haber estado desplegado, habria dado 401: que `ms-subastas` esta detras del
 * mismo borde y acepta el mismo inicio de sesion que el resto de la
 * aplicacion. El listado es publico; publicar y pujar exigen el token; y un
 * token real ENTRA (la respuesta es la del negocio, no un 401 de firma).
 *
 * El segundo corte (FI-TRANSFER-1) es el bloque de abajo: publicar un objeto
 * REAL del inventario y llevarlo hasta el cambio de dueno. Lo que faltaba no
 * era la prueba, era el cableado — `tests/e2e/compose.yml` no declaraba
 * `INVENTARIO_MODO`, asi que ms-subastas hablaba con un doble en memoria y
 * ningun objeto se movia nunca — mas un producto con `_id` en forma de UUID en
 * la semilla, porque `PublicarSubastaRequest` exige `UUID productoId` y
 * "p-arma-e2e" se rechaza con 400.
 *
 * Se cierra por COMPRA INMEDIATA y no por vencimiento porque las duraciones
 * son 24H y 48H: esperar el cierre programado no cabe en una prueba. El camino
 * que se ejercita es el mismo —reservar credito, transferir, cobrar, soltar el
 * bloqueo— y el del vencimiento lo cubre CierreDeSubastasVencidasJobTest.
 *
 * Lo que NO cubre todavia: el estado degradado (inventario apagado a mitad de
 * una publicacion). Su sitio es R16.4, junto a los demas degradados con
 * backend real, y la inyeccion de fallos ya tiene patron en
 * degradacion.e2e.spec.js.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const VISTA = '/frontend/app-web/src/cuentas/subastas.html';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
// Credencial de servicio del banco de pruebas (tests/e2e/compose.yml, ADR-005).
const BANCO = { id: 'e2e-banco', secreto: 'e2e-secreto-del-banco-de-pruebas' };
// Producto con _id en forma de UUID que siembra sembrar.sh. Los otros dos
// ("p-heroe-e2e", "p-arma-e2e") no sirven: productoId es un UUID en el contrato.
const PRODUCTO_SUBASTABLE = 'dddddddd-0000-0000-0000-00000000000a';
// Vendedora y compradora propias de este bloque. No se reutiliza a la
// anfitriona ni al invitado porque sus creditos los gastan las pruebas de
// salas, y una subasta que falla por saldo no dice nada sobre la transferencia.
const VENDEDORA = process.env.E2E_VENDEDORA ?? 'vendedora_e2e';
const COMPRADORA = process.env.E2E_COMPRADORA ?? 'compradora_e2e';

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

test.describe('Subastas por el borde (HU-SUB-011 / HU-SUB-001 / HU-SUB-004)', () => {
  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let anfitriona;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('el listado de subastas activas es publico y responde por el borde (antes: 502)', async () => {
    const r = await api.get('/api/v1/subastas?page=0&size=4');
    expect(r.status(), await r.text()).toBe(200);
    const pagina = await r.json();
    expect(Array.isArray(pagina.contenido)).toBe(true);
    expect(pagina.tamanoPagina).toBe(4);
  });

  test('publicar y pujar exigen sesion: sin token es 401 con problem details', async () => {
    const publicar = await api.post('/api/v1/subastas', {
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'e2e-sin-token' },
      data: {
        elementoInventarioId: 'x',
        productoId: '00000000-0000-0000-0000-000000000001',
        duracion: '24H',
        precioInicial: 10,
      },
    });
    expect(publicar.status()).toBe(401);
    const pujar = await api.post('/api/v1/subastas/00000000-0000-0000-0000-000000000001/pujas', {
      headers: { 'Content-Type': 'application/json' },
      data: { monto: '10' },
    });
    expect(pujar.status()).toBe(401);
  });

  test('con el token real de ms-identidad la peticion entra: la respuesta es del negocio, no de la firma', async () => {
    // Antes del 21-sep ms-subastas validaba con una clave HS256 propia y este
    // mismo token (RS256, firmado por ms-identidad) daba 401. Ahora llega al
    // controlador: la subasta no existe, y eso es un 404 del negocio.
    const r = await api.get(
      '/api/v1/subastas/00000000-0000-0000-0000-000000000001/mi-participacion',
      {
        headers: conToken(anfitriona.token),
      },
    );
    expect(r.status(), await r.text()).not.toBe(401);
    expect([200, 404]).toContain(r.status());

    // Y las pujas del propio jugador: bandeja vacia, pero suya.
    const mias = await api.get('/api/v1/mis-pujas/resumen', {
      headers: conToken(anfitriona.token),
    });
    expect(mias.status(), await mias.text()).toBe(200);
  });

  test('la vista de subastas carga el listado con la cabecera de la aplicacion', async ({
    page,
  }) => {
    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [anfitriona.token, ANFITRION],
    );
    const listado = page.waitForResponse(
      (r) => r.url().includes('/api/v1/subastas') && r.request().method() === 'GET',
    );
    await page.goto(`${BORDE}${VISTA}`);
    expect((await listado).status()).toBe(200);
    await expect(page.locator('.cabecera [data-zona="apodo"]')).toHaveText(ANFITRION);
    await expect(page.locator('.cabecera [data-seccion="subasta"]')).toHaveAttribute(
      'aria-current',
      'page',
    );
  });
});

// ------------------------------------------------- ayudantes del segundo corte

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

/** Saldo inicial con la credencial del banco: un jugador no se acredita solo. */
async function acreditar(api, servicio, quien, monto, refId) {
  const r = await api.post(`${FINANZAS}/creditos/acreditar`, {
    headers: conToken(servicio),
    data: { uid: quien.claims.uid, monto, refId, concepto: 'semilla-subasta-e2e' },
  });
  expect(r.status(), `acreditar a ${quien.claims.uid}: ${await r.text()}`).toBe(200);
}

/**
 * Los elementos que el propio jugador ve en su vitrina, con SU token.
 *
 * Deliberadamente no se usa `GET /elementos/{id}`, que da el detalle con
 * propietarioUid: esa ruta es solo para ms-subastas (`soloSubastas` en
 * SeguridadConfig). Preguntando por la vitrina se comprueba lo que de verdad
 * importa —lo que el jugador ve— y no un endpoint que la interfaz no usa.
 */
async function vitrinaDe(api, quien) {
  const r = await api.get('/api/v1/inventario/elementos?pagina=0', {
    headers: conToken(quien.token),
  });
  expect(r.status(), `vitrina de ${quien.claims.uid}: ${await r.text()}`).toBe(200);
  return (await r.json()).elementos;
}

function buscar(elementos, elementoId) {
  return elementos.find((elemento) => elemento.id === elementoId);
}

test.describe('Transferencia de propiedad al ganar una subasta (HU-SUB-004)', () => {
  // En serie y con estado compartido a proposito: cada paso es el siguiente
  // eslabon del mismo camino, y partirlos en pruebas independientes obligaria
  // a repetir la publicacion cuatro veces.
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let vendedora;
  let compradora;
  let elementoId;
  let subastaId;
  const CLAVE_COMPRA = 'e2e-compra-inmediata-transferencia';

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    vendedora = await sesionDe(api, VENDEDORA);
    compradora = await sesionDe(api, COMPRADORA);
    const servicio = await tokenDeServicio(api);
    // Idempotente por refId: repetir la corrida no duplica saldo.
    await acreditar(api, servicio, compradora, 500, `semilla-subasta-${COMPRADORA}`);
    // La vendedora tambien necesita saldo: publicar cobra comision
    // (CalculadorComisionPublicacion: 1 credito a 24H, 3 a 48H, 0 para el
    // maestro de juego). Sin esto la publicacion falla al debitarla, y el fallo
    // no diria nada sobre la transferencia.
    await acreditar(api, servicio, vendedora, 100, `semilla-subasta-${VENDEDORA}`);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('la vendedora tiene el objeto y esta disponible', async () => {
    const creado = await api.post('/api/v1/inventario/elementos', {
      headers: conToken(vendedora.token),
      data: {
        productoId: PRODUCTO_SUBASTABLE,
        tipo: 'ARMA',
        nombrePropio: `Hacha de ${VENDEDORA} ${Date.now()}`,
      },
    });
    expect(creado.status(), `crear elemento: ${await creado.text()}`).toBe(201);
    elementoId = (await creado.json()).id;
    expect(elementoId).toBeTruthy();

    const mio = buscar(await vitrinaDe(api, vendedora), elementoId);
    expect(mio, 'el elemento recien creado tiene que estar en la vitrina').toBeTruthy();
    expect(mio.disponible).toBe(true);
    expect(mio.subastaId).toBeFalsy();
  });

  test('al publicarla el objeto queda bloqueado por esa subasta, no suelto', async () => {
    const publicar = await api.post('/api/v1/subastas', {
      headers: { ...conToken(vendedora.token), 'Idempotency-Key': `e2e-publicar-${Date.now()}` },
      data: {
        elementoInventarioId: elementoId,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 10,
        precioCompraInmediata: 20,
      },
    });
    expect(publicar.status(), `publicar: ${await publicar.text()}`).toBe(201);
    subastaId = (await publicar.json()).id;
    expect(subastaId, 'la publicacion tiene que devolver el id de la subasta').toBeTruthy();

    // Esta es la prueba de que INVENTARIO_MODO llego de verdad a http: con el
    // doble en memoria el bloqueo no se escribia y esto seguiria disponible.
    const bloqueado = buscar(await vitrinaDe(api, vendedora), elementoId);
    expect(bloqueado.disponible).toBe(false);
    expect(bloqueado.subastaId).toBe(subastaId);
  });

  test('la compradora paga y el objeto cambia de dueno de verdad', async () => {
    const compra = await api.post(`/api/v1/subastas/${subastaId}/compra-inmediata`, {
      headers: { ...conToken(compradora.token), 'Idempotency-Key': CLAVE_COMPRA },
      data: { confirmado: true },
    });
    expect(compra.status(), `compra inmediata: ${await compra.text()}`).toBe(201);

    // Sale del inventario de la vendedora. Antes de FI-TRANSFER-1 se quedaba
    // ahi: se cobraba y no se entregaba.
    const yaNoEsSuyo = buscar(await vitrinaDe(api, vendedora), elementoId);
    expect(yaNoEsSuyo, 'el objeto vendido no puede seguir en la vitrina de quien lo vendio')
      .toBeUndefined();

    // Y entra en el de la compradora, USABLE: el bloqueo viaja con el elemento
    // y lo suelta el motor de pujas cuando la venta ya es definitiva. Si
    // llegara bloqueado, la ganadora tendria un objeto pagado que no puede
    // equipar ni revender.
    const ahoraEsSuyo = buscar(await vitrinaDe(api, compradora), elementoId);
    expect(ahoraEsSuyo, 'la ganadora tiene que tener el objeto').toBeTruthy();
    expect(ahoraEsSuyo.disponible).toBe(true);
    expect(ahoraEsSuyo.subastaId).toBeFalsy();
  });

  test('la propiedad sobrevive a cerrar sesion y volver a entrar', async () => {
    // El servidor es la autoridad: con una sesion nueva, y por tanto sin nada
    // guardado en el navegador, el objeto sigue siendo de la ganadora.
    const deNuevo = await sesionDe(api, COMPRADORA);
    const suyo = buscar(await vitrinaDe(api, deNuevo), elementoId);
    expect(suyo, 'tras volver a iniciar sesion el objeto sigue siendo suyo').toBeTruthy();
    expect(suyo.disponible).toBe(true);
  });

  test('repetir la compra con la misma clave no duplica el objeto', async () => {
    // El cierre por vencimiento reintenta cada 30 s, asi que la segunda pasada
    // tiene que ser inofensiva. Se acepta 201 (idempotente) o un rechazo de
    // negocio; lo que NO se acepta es que el objeto acabe en los dos
    // inventarios, o dos veces en uno.
    const repetida = await api.post(`/api/v1/subastas/${subastaId}/compra-inmediata`, {
      headers: { ...conToken(compradora.token), 'Idempotency-Key': CLAVE_COMPRA },
      data: { confirmado: true },
    });
    expect([201, 409, 422]).toContain(repetida.status());

    const deLaCompradora = (await vitrinaDe(api, compradora))
      .filter((elemento) => elemento.id === elementoId);
    expect(deLaCompradora).toHaveLength(1);
    expect(buscar(await vitrinaDe(api, vendedora), elementoId)).toBeUndefined();
  });

  test('la ganadora ve el objeto en su inventario por la interfaz, no solo por la API', async ({
    page,
  }) => {
    // T14: el servidor es autoritativo. La vista no pinta un optimismo local;
    // lo que muestra sale de la misma llamada que acaba de comprobarse arriba.
    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [compradora.token, COMPRADORA],
    );
    const vitrina = page.waitForResponse(
      (r) => r.url().includes('/api/v1/inventario/elementos') && r.request().method() === 'GET',
    );
    await page.goto(`${BORDE}/frontend/app-web/src/contenido/inventario/inventario.html`);
    const respuesta = await vitrina;
    expect(respuesta.status()).toBe(200);
    const cuerpo = await respuesta.json();
    expect(cuerpo.elementos.some((elemento) => elemento.id === elementoId)).toBe(true);
  });
});
