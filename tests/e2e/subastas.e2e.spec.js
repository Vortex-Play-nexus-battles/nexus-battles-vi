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
 *
 * El tercer corte (B8) es el ultimo bloque: las reglas de 7.7 dichas por el
 * servidor, la ficha, la compra inmediata que tiene que superar el precio
 * minimo, la lista de seguimiento, la cancelacion con su penalizacion, el
 * canal STOMP por el borde (/api/v1/ws-subastas), que hasta B8 no tenia
 * location y caia en el 404 generico, y el cierre por compra inmediata
 * anunciado por ese canal.
 *
 * Desde B8 el incremento minimo sale de admin-parametros
 * (`subastas.incremento-minimo`). Desde D-43 (auditoria del 4-oct) vale 5
 * creditos por su migracion V5: el banco ya no lo fija a mano, solo espera a
 * que GET /subastas/reglas lo diga. El ultimo bloque comprueba la regla de
 * punta a punta: con 100 vigente, 104 se rechaza, 105 entra, y de dos pujas
 * iguales a la vez entra una sola.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { correosPara } from './ayudantes/correo.js';
import { sesionDe as sesionDelBanco } from './ayudantes/cuentas.js';

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
// D-43 — el incremento minimo que publica admin-parametros (migracion V5).
const INCREMENTO_MINIMO = 5;
// Sufijo de esta corrida, para las cuentas propias del bloque D-43.
const SUFIJO = Date.now().toString(36);

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

/**
 * D-43 — el incremento minimo ya viene configurado (5 creditos, migracion V5
 * de admin-parametros): no se fija a mano. Se espera a que GET /subastas/reglas
 * lo diga, porque ms-subastas lo cachea (SUBASTAS_PARAMETROS_CACHE_SEGUNDOS).
 */
async function esperarIncrementoMinimo(api) {
  await expect
    .poll(
      async () => {
        const reglas = await (await api.get('/api/v1/subastas/reglas')).json();
        return reglas.incrementoMinimoConfigurado ? Number(reglas.incrementoMinimo) : null;
      },
      { timeout: 15_000, message: 'el incremento minimo de admin-parametros' },
    )
    .toBe(INCREMENTO_MINIMO);
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
    await esperarIncrementoMinimo(api);
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
    // B4 (inventario 1.5.0): un jugador ya no se crea objetos con
    // POST /elementos (403). La propiedad llega por un canal valido: aqui, una
    // entrega con la credencial de servicio del banco, como la haria la
    // tienda o un cofre. La clave es de esta corrida: repetirla devolveria la
    // misma entrega (200) y no un objeto nuevo.
    const servicio = await tokenDeServicio(api);
    const corrida = Date.now();
    const entrega = await api.post('/api/v1/inventario/entregas', {
      headers: { ...conToken(servicio), 'Idempotency-Key': `e2e-subasta-${VENDEDORA}-${corrida}` },
      data: {
        uid: vendedora.claims.uid,
        origen: 'ADMINISTRACION',
        referencia: `e2e-subasta-${corrida}`,
        productos: [{ productoId: PRODUCTO_SUBASTABLE, cantidad: 1 }],
      },
    });
    expect(entrega.status(), `entregar el objeto: ${await entrega.text()}`).toBe(201);
    const entregado = (await entrega.json()).elementos[0];
    elementoId = entregado.id;
    expect(elementoId).toBeTruthy();
    expect(entregado.origen).toBe('ADMINISTRACION');

    // Y la puerta de atras sigue cerrada: la jugadora no se crea el objeto.
    const gratis = await api.post('/api/v1/inventario/elementos', {
      headers: conToken(vendedora.token),
      data: { productoId: PRODUCTO_SUBASTABLE, tipo: 'ARMA', nombrePropio: 'Hacha gratis' },
    });
    expect(gratis.status(), await gratis.text()).toBe(403);

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
    expect(
      yaNoEsSuyo,
      'el objeto vendido no puede seguir en la vitrina de quien lo vendio',
    ).toBeUndefined();

    // Y entra en el de la compradora, USABLE: el bloqueo viaja con el elemento
    // y lo suelta el motor de pujas cuando la venta ya es definitiva. Si
    // llegara bloqueado, la ganadora tendria un objeto pagado que no puede
    // equipar ni revender.
    const ahoraEsSuyo = buscar(await vitrinaDe(api, compradora), elementoId);
    expect(ahoraEsSuyo, 'la ganadora tiene que tener el objeto').toBeTruthy();
    expect(ahoraEsSuyo.disponible).toBe(true);
    expect(ahoraEsSuyo.subastaId).toBeFalsy();
  });

  test('RF-COR-005 / RF-NOT-003: la venta le llega por correo a la vendedora', async () => {
    // RFINAL-01: la compra inmediata encola COMPRA_INMEDIATA_EJECUTADA para la
    // vendedora con correo (TipoNotificacion), y el drenaje la manda al
    // servicio de correo real del banco con el contacto de ms-identidad. Su
    // cuenta es @nexus.test: la cola la entrega en Mailpit. Con CORREO_URL
    // vacía (el valor de antes en DEV) el aviso se quedaba solo en la bandeja.
    await expect
      .poll(
        async () =>
          (await correosPara(vendedora.email, { base: BORDE })).filter((m) =>
            String(m.Subject ?? '').startsWith('Compraron tu subasta de forma inmediata'),
          ).length,
        { timeout: 60_000, message: 'el correo de la venta no llegó al buzón de la vendedora' },
      )
      .toBeGreaterThan(0);
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

    const deLaCompradora = (await vitrinaDe(api, compradora)).filter(
      (elemento) => elemento.id === elementoId,
    );
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

// ----------------------------------------------------------------- tercer corte (B8)

/**
 * Abre el canal STOMP de subastas DESDE EL NAVEGADOR, por el borde, como lo hace
 * la vista. Devuelve la primera linea del primer frame (CONNECTED o ERROR).
 */
async function conectarCanal(page, token, destinos = []) {
  return page.evaluate(
    async ({ url, token, destinos }) =>
      new Promise((resolver) => {
        const ws = new WebSocket(url);
        window.__mensajesSubastas = [];
        const plazo = setTimeout(() => resolver('SIN RESPUESTA'), 10_000);
        ws.onopen = () => {
          const cabeceras = ['accept-version:1.2', 'heart-beat:0,0'];
          if (token) {
            cabeceras.push(`Authorization:Bearer ${token}`);
          }
          ws.send(`CONNECT\n${cabeceras.join('\n')}\n\n\0`);
        };
        ws.onmessage = (evento) => {
          const texto = String(evento.data);
          const comando = texto.split('\n')[0];
          if (comando === 'CONNECTED') {
            destinos.forEach((destino, i) => {
              ws.send(`SUBSCRIBE\nid:sub-${i}\ndestination:${destino}\n\n\0`);
            });
            window.__canalSubastas = ws;
            clearTimeout(plazo);
            resolver('CONNECTED');
          } else if (comando === 'MESSAGE') {
            window.__mensajesSubastas.push(texto);
          } else {
            clearTimeout(plazo);
            resolver(comando);
          }
        };
        ws.onerror = () => {
          clearTimeout(plazo);
          resolver('ERROR DE TRANSPORTE');
        };
        ws.onclose = () => {
          clearTimeout(plazo);
          resolver('CERRADO');
        };
      }),
    { url: `${BORDE.replace(/^http/, 'ws')}/api/v1/ws-subastas`, token, destinos },
  );
}

test.describe('Reglas, ficha, seguimiento, cancelación y canal en vivo (B8)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let vendedora;
  let compradora;
  let subastaId;
  let elementoId;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    await esperarIncrementoMinimo(api);
    vendedora = await sesionDe(api, VENDEDORA);
    compradora = await sesionDe(api, COMPRADORA);
    const servicio = await tokenDeServicio(api);
    await acreditar(api, servicio, compradora, 500, `semilla-subasta-b8-${COMPRADORA}`);
    await acreditar(api, servicio, vendedora, 100, `semilla-subasta-b8-${VENDEDORA}`);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  /**
   * B4 (inventario 1.5.0): una jugadora ya no se crea objetos con
   * `POST /elementos` (403). La vendedora recibe cada objeto que va a subastar
   * por una entrega con la credencial de servicio del banco, como la haria la
   * tienda o un cofre. La clave es de esta llamada: repetirla devolveria la
   * misma entrega y no un objeto nuevo.
   */
  async function entregarALaVendedora(motivo) {
    const servicio = await tokenDeServicio(api);
    const clave = `e2e-b8-${motivo}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const entrega = await api.post('/api/v1/inventario/entregas', {
      headers: { ...conToken(servicio), 'Idempotency-Key': clave },
      data: {
        uid: vendedora.claims.uid,
        origen: 'ADMINISTRACION',
        referencia: clave,
        productos: [{ productoId: PRODUCTO_SUBASTABLE, cantidad: 1 }],
      },
    });
    expect(entrega.status(), `entregar el objeto: ${await entrega.text()}`).toBe(201);
    return (await entrega.json()).elementos[0].id;
  }

  test('las reglas vigentes son publicas y vienen del servidor (Tabla 25 y admin-parametros)', async () => {
    const r = await api.get('/api/v1/subastas/reglas');
    expect(r.status(), await r.text()).toBe(200);
    const reglas = await r.json();
    expect(reglas.duraciones.map((d) => [d.codigo, Number(d.comision)])).toEqual([
      ['24H', 1],
      ['48H', 3],
    ]);
    expect(reglas.incrementoMinimoConfigurado).toBe(true);
    expect(Number(reglas.incrementoMinimo)).toBe(INCREMENTO_MINIMO);
    expect(reglas.penalizacionCancelacionPorcentaje).toBe(50);
    expect(reglas.cancelacionProhibidaUltimasHoras).toBe(6);
    expect(reglas.diasParaRecoger).toBe(7);
  });

  test('la compra inmediata tiene que superar el precio minimo (7.7.2)', async () => {
    elementoId = await entregarALaVendedora('lanza');

    const igual = await api.post('/api/v1/subastas', {
      headers: { ...conToken(vendedora.token), 'Idempotency-Key': `e2e-b8-igual-${Date.now()}` },
      data: {
        elementoInventarioId: elementoId,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 10,
        precioCompraInmediata: 10,
      },
    });
    expect(igual.status(), await igual.text()).toBe(422);
    expect((await igual.json()).motivo).toBe('COMPRA_INMEDIATA_NO_SUPERIOR');
  });

  test('publicada, su ficha es publica y la primera puja minima es el precio minimo', async () => {
    const publicar = await api.post('/api/v1/subastas', {
      headers: { ...conToken(vendedora.token), 'Idempotency-Key': `e2e-b8-publicar-${Date.now()}` },
      data: {
        elementoInventarioId: elementoId,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 10,
        precioCompraInmediata: 40,
      },
    });
    expect(publicar.status(), await publicar.text()).toBe(201);
    const publicada = await publicar.json();
    subastaId = publicada.id;
    expect(Number(publicada.incrementoMinimo)).toBe(INCREMENTO_MINIMO);

    const ficha = await api.get(`/api/v1/subastas/${subastaId}`);
    expect(ficha.status(), await ficha.text()).toBe(200);
    const detalle = await ficha.json();
    expect(detalle.estado).toBe('ACTIVA');
    expect(Number(detalle.pujaMinimaSiguiente)).toBe(10);
    expect(detalle.compraInmediataDisponible).toBe(true);
    expect(detalle.reputacionVendedor).toBeTruthy();

    // G5 (listado 1.2.0): ni la ficha ni el listado públicos llevan el uid de
    // la vendedora; si es suya lo dice el servidor con el token de quien mira.
    const uid = vendedora.claims.uid;
    const anonima = await (await api.get(`/api/v1/subastas/${subastaId}`)).text();
    expect(anonima).not.toContain(uid);
    expect(JSON.parse(anonima)).toMatchObject({ vendedorId: null, esPropia: false });
    const suya = await (
      await api.get(`/api/v1/subastas/${subastaId}`, { headers: conToken(vendedora.token) })
    ).json();
    expect(suya.esPropia).toBe(true);
    const listado = await (
      await api.get('/api/v1/subastas?page=0&size=50&ordenarPor=FECHA_PUBLICACION', {
        headers: conToken(vendedora.token),
      })
    ).text();
    expect(listado).not.toContain(uid);
    const fila = JSON.parse(listado).contenido.find((s) => s.id === subastaId);
    expect(fila).toMatchObject({ esPropia: true, vendedorId: null });
  });

  test('seguirla la pone en la lista de seguimiento de la compradora', async () => {
    const seguir = await api.put(`/api/v1/subastas/${subastaId}/seguimiento`, {
      headers: conToken(compradora.token),
    });
    expect(seguir.status(), await seguir.text()).toBe(204);
    const lista = await api.get('/api/v1/mis-subastas/seguimiento', {
      headers: conToken(compradora.token),
    });
    expect(lista.status()).toBe(200);
    expect((await lista.json()).some((s) => s.subastaId === subastaId)).toBe(true);
  });

  test('el canal de la subasta se abre por el borde y trae la puja al instante', async ({
    page,
  }) => {
    await page.goto(`${BORDE}/salud-borde`);
    // Un token roto no es un visitante: el CONNECT se rechaza.
    expect(await conectarCanal(page, 'no-es-un-jwt')).not.toBe('CONNECTED');
    // Con la sesion de la compradora: el listado y el canal de ESTA subasta.
    expect(
      await conectarCanal(page, compradora.token, [
        '/topic/subastas/listado',
        `/topic/subastas/${subastaId}`,
      ]),
    ).toBe('CONNECTED');
    await page.waitForFunction(() => window.__canalSubastas?.bufferedAmount === 0);

    const puja = await api.post(`/api/v1/subastas/${subastaId}/pujas`, {
      headers: { ...conToken(compradora.token), 'Idempotency-Key': `e2e-b8-puja-${Date.now()}` },
      data: { monto: '10' },
    });
    expect(puja.status(), await puja.text()).toBe(201);

    await page.waitForFunction(
      (id) =>
        window.__mensajesSubastas.filter((m) => m.includes(id)).length >= 2 &&
        window.__mensajesSubastas.some((m) => m.includes(`destination:/topic/subastas/${id}`)),
      subastaId,
      { timeout: 20_000 },
    );
  });

  test('con una puja ya no se puede cancelar; sin pujas, cancelar cobra la mitad de la comision', async () => {
    const conPuja = await api.post(`/api/v1/subastas/${subastaId}/cancelacion`, {
      headers: conToken(vendedora.token),
    });
    expect(conPuja.status(), await conPuja.text()).toBe(409);
    expect((await conPuja.json()).motivo).toBe('CANCELACION_CON_PUJAS');

    // Otra subasta, sin pujas, para cancelar de verdad.
    const otroElemento = await entregarALaVendedora('escudo');
    const publicar = await api.post('/api/v1/subastas', {
      headers: {
        ...conToken(vendedora.token),
        'Idempotency-Key': `e2e-b8-cancelable-${Date.now()}`,
      },
      data: {
        elementoInventarioId: otroElemento,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 10,
      },
    });
    expect(publicar.status(), await publicar.text()).toBe(201);
    const cancelable = (await publicar.json()).id;

    const ajena = await api.post(`/api/v1/subastas/${cancelable}/cancelacion`, {
      headers: conToken(compradora.token),
    });
    expect(ajena.status()).toBe(403);

    const cancelar = await api.post(`/api/v1/subastas/${cancelable}/cancelacion`, {
      headers: conToken(vendedora.token),
    });
    expect(cancelar.status(), await cancelar.text()).toBe(200);
    const cancelacion = await cancelar.json();
    expect(cancelacion.estado).toBe('CANCELADA');
    expect(Number(cancelacion.penalizacionCobrada)).toBe(0.5);

    // El objeto vuelve a estar disponible en su inventario.
    const suyo = buscar(await vitrinaDe(api, vendedora), otroElemento);
    expect(suyo, 'el objeto de la subasta cancelada vuelve a la vitrina').toBeTruthy();
    expect(suyo.disponible).toBe(true);

    // Y la penalizacion queda en su historial de subastas.
    const historial = await api.get('/api/v1/mis-subastas/historial', {
      headers: conToken(vendedora.token),
    });
    expect(historial.status()).toBe(200);
    const movimientos = (await historial.json()).movimientos;
    expect(
      movimientos.some(
        (m) => m.tipo === 'PENALIZACION' && m.subastaId === cancelable && Number(m.monto) === 0.5,
      ),
    ).toBe(true);
  });

  test('la compra inmediata cierra la subasta al instante y el canal lo anuncia (7.7.6)', async ({
    page,
  }) => {
    // Cierra ademas la subasta de este bloque: sin esto, cada corrida contra el
    // mismo banco dejaria una subasta ACTIVA mas de la vendedora, y a la
    // decima el tope de 10 publicaciones (7.7.10) rechazaria la siguiente.
    await page.goto(`${BORDE}/salud-borde`);
    expect(await conectarCanal(page, vendedora.token, [`/topic/subastas/${subastaId}`])).toBe(
      'CONNECTED',
    );
    await page.waitForFunction(() => window.__canalSubastas?.bufferedAmount === 0);

    const compra = await api.post(`/api/v1/subastas/${subastaId}/compra-inmediata`, {
      headers: { ...conToken(compradora.token), 'Idempotency-Key': `e2e-b8-compra-${Date.now()}` },
      data: { confirmado: true },
    });
    expect(compra.status(), await compra.text()).toBe(201);

    await page.waitForFunction(
      (id) =>
        window.__mensajesSubastas.some(
          (m) => m.includes(id) && m.includes('"estado":"ADJUDICADA"'),
        ),
      subastaId,
      { timeout: 20_000 },
    );
    const ficha = await (await api.get(`/api/v1/subastas/${subastaId}`)).json();
    expect(ficha.estado).toBe('ADJUDICADA');
    expect(ficha.compraInmediataDisponible).toBe(false);
  });
});

test.describe('D-43 — incremento mínimo de 5 créditos, de punta a punta', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let vendedor;
  let postores;
  let subastaId;

  /** Una puja con su propia clave de idempotencia, como la mandaria un cliente. */
  function pujar(quien, monto) {
    return api.post(`/api/v1/subastas/${subastaId}/pujas`, {
      headers: {
        ...conToken(quien.token),
        'Idempotency-Key': `e2e-d43-${quien.claims.uid}-${monto}-${Date.now()}`,
      },
      data: { monto: String(monto) },
    });
  }

  test.beforeAll(async () => {
    test.setTimeout(180_000);
    api = await apiRequest.newContext({ baseURL: BORDE });
    await esperarIncrementoMinimo(api);
    // Cuentas propias de esta corrida: un vendedor nuevo no choca con el tope
    // de 10 publicaciones activas de la vendedora compartida (7.7.10).
    vendedor = await sesionDe(api, `vendedor_d43_${SUFIJO}`);
    postores = [];
    for (let i = 1; i <= 5; i += 1) {
      postores.push(await sesionDe(api, `postor${i}_d43_${SUFIJO}`));
    }
    const servicio = await tokenDeServicio(api);
    await acreditar(api, servicio, vendedor, 50, `semilla-d43-${SUFIJO}-vendedor`);
    for (const [i, postor] of postores.entries()) {
      await acreditar(api, servicio, postor, 500, `semilla-d43-${SUFIJO}-postor${i + 1}`);
    }

    const clave = `e2e-d43-${SUFIJO}`;
    const entrega = await api.post('/api/v1/inventario/entregas', {
      headers: { ...conToken(servicio), 'Idempotency-Key': clave },
      data: {
        uid: vendedor.claims.uid,
        origen: 'ADMINISTRACION',
        referencia: clave,
        productos: [{ productoId: PRODUCTO_SUBASTABLE, cantidad: 1 }],
      },
    });
    expect(entrega.status(), `entregar el objeto: ${await entrega.text()}`).toBe(201);
    const elemento = (await entrega.json()).elementos[0].id;

    const publicar = await api.post('/api/v1/subastas', {
      headers: { ...conToken(vendedor.token), 'Idempotency-Key': `e2e-d43-publicar-${SUFIJO}` },
      data: {
        elementoInventarioId: elemento,
        productoId: PRODUCTO_SUBASTABLE,
        duracion: '24H',
        precioInicial: 100,
        precioCompraInmediata: 1000,
      },
    });
    expect(publicar.status(), await publicar.text()).toBe(201);
    const publicada = await publicar.json();
    subastaId = publicada.id;
    // El incremento con el que nace la subasta es el de admin-parametros.
    expect(Number(publicada.incrementoMinimo)).toBe(INCREMENTO_MINIMO);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('con 100 vigente, 104 se rechaza y 105 entra', async () => {
    const primera = await pujar(postores[0], 100);
    expect(primera.status(), await primera.text()).toBe(201);

    const corta = await pujar(postores[1], 104);
    expect(corta.status(), await corta.text()).toBe(409);
    expect((await corta.json()).motivo).toBe('OFERTA_INSUFICIENTE');

    const justa = await pujar(postores[2], 105);
    expect(justa.status(), await justa.text()).toBe(201);

    const ficha = await (await api.get(`/api/v1/subastas/${subastaId}`)).json();
    expect(Number(ficha.ofertaVigente)).toBe(105);
    expect(Number(ficha.pujaMinimaSiguiente)).toBe(110);
  });

  test('dos jugadores ven 105 y pujan 110 a la vez: entra una sola', async () => {
    const [una, otra] = await Promise.all([pujar(postores[3], 110), pujar(postores[4], 110)]);
    const estados = [una.status(), otra.status()].sort();
    expect(estados, `${await una.text()} | ${await otra.text()}`).toEqual([201, 409]);
    const rechazada = una.status() === 409 ? una : otra;
    expect((await rechazada.json()).motivo).toBe('OFERTA_INSUFICIENTE');

    const ficha = await (await api.get(`/api/v1/subastas/${subastaId}`)).json();
    expect(Number(ficha.ofertaVigente)).toBe(110);
    expect(Number(ficha.pujaMinimaSiguiente)).toBe(115);
  });

  test('la pantalla de publicar dice «Incremento mínimo: 5 créditos» y nada de «DECISIÓN PO»', async ({
    page,
  }) => {
    await page.addInitScript(
      ([token, nombre]) => {
        sessionStorage.setItem('nexus.token', token);
        sessionStorage.setItem('nexus.apodoActual', nombre);
      },
      [vendedor.token, vendedor.apodo],
    );
    await page.goto(`${BORDE}/frontend/app-web/src/cuentas/publicar-subasta.html`);
    const aviso = page.locator('[data-incremento-minimo]');
    await expect(aviso).toHaveText(`Incremento mínimo: ${INCREMENTO_MINIMO} créditos`, {
      timeout: 15_000,
    });
    await expect(page.locator('body')).not.toContainText('DECISIÓN PO');
  });
});
