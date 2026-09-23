// @ts-check
/**
 * Registro de transacciones en moneda real — HU-PAG-002 (#536, RF-PAG-002),
 * de punta a punta contra el libro real.
 *
 * Por que existe esta prueba. HU-PAG-002 tenia el codigo completo, sus
 * pruebas de unidad e integracion en verde y el pipeline en verde, y aun asi
 * no se podia ENSENAR: en dev da 502 porque ms-finanzas no cabe en el host
 * (infrastructure/despliegue/CAPACIDAD.md), y en el banco E2E, que si lo
 * levanta, nadie recorria sus criterios. "Terminada" sin nada que mostrar es
 * justo lo que este archivo viene a arreglar: mientras ms-finanzas no quepa
 * en AWS, esta es la demostracion de la HU.
 *
 * Se recorren los cuatro criterios con el servicio de verdad y su Postgres:
 *
 *   CA-01  se registra el asiento con todos sus atributos (referencia, monto,
 *          moneda, usuario, concepto, resultado, comprobante) y queda asociado
 *          al usuario.
 *   CA-02  el usuario lo consulta desde su historial y lo ve; el historial de
 *          otro jugador NO lo ve.
 *   CA-03  una transaccion en estado indeterminado queda registrada y marcada
 *          para conciliacion manual; y reenviar la misma referencia se rechaza
 *          con problem details (regla 4) sin dejar estado a medias.
 *   CA-04  el asiento sigue ahi y es consultable despues, con los mismos datos.
 *
 * Quien puede que. `POST /transacciones` es ROLE_SERVICIO (lo llama la
 * pasarela, no el navegador) y `/transacciones/mi-historial` es del usuario
 * autenticado no-servicio: las dos caras se comprueban aqui, porque un
 * registro que el dueno no puede leer no cumple CA-02.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = process.env.E2E_FINANZAS ?? 'http://localhost:8093/api/v1';
const CLAVE = 'Contrasena-E2E-2026';
// Credencial de servicio del banco de pruebas (tests/e2e/compose.yml, ADR-005).
const BANCO = { id: 'e2e-banco', secreto: 'e2e-secreto-del-banco-de-pruebas' };

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Pagadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  const cuerpo = await login.json();
  return { ...cuerpo, claims: cuerpoDelToken(cuerpo.token) };
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

/** El historial del propio usuario, pagina a pagina hasta encontrar la referencia. */
async function historialDe(api, quien, tamano = 100) {
  const r = await api.get(`${FINANZAS}/transacciones/mi-historial?page=0&size=${tamano}`, {
    headers: conToken(quien.token),
  });
  expect(r.status(), `historial de ${quien.claims.uid}: ${await r.text()}`).toBe(200);
  return (await r.json()).content;
}

async function registrarTransaccion(api, servicio, datos) {
  return api.post(`${FINANZAS}/transacciones`, { headers: conToken(servicio), data: datos });
}

test.describe('Registro de transacciones en moneda real (HU-PAG-002)', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {import('@playwright/test').APIRequestContext} */
  let api;
  let pagadora;
  let ajena;
  let servicio;
  // Una marca NUEVA en cada arranque del bloque, no una constante de modulo.
  // En serie con reintentos (playwright.e2e.config.js: retries 1 en CI),
  // Playwright vuelve a correr el bloque entero desde beforeAll: con una marca
  // fija, el POST de CA-01 encontraria su propia referencia del intento
  // anterior y responderia 409 en vez de 201 -- la prueba fallaria por su
  // propia huella, no por el servicio. refId es unico a proposito (es lo que
  // sostiene CA-03), asi que la marca se calcula aqui.
  let marca;
  let refCompra;
  let refDudosa;

  test.beforeAll(async () => {
    marca = `${Date.now().toString(36)}${Math.floor(Math.random() * 36 ** 2).toString(36)}`;
    refCompra = `pago-e2e-${marca}`;
    refDudosa = `pago-e2e-indeterminado-${marca}`;
    api = await apiRequest.newContext({ baseURL: BORDE });
    pagadora = await sesionDe(api, `pagadora_e2e_${marca}`);
    ajena = await sesionDe(api, `ajena_e2e_${marca}`);
    servicio = await tokenDeServicio(api);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('CA-01 · el asiento se persiste con todos sus atributos y queda asociado al usuario', async () => {
    const respuesta = await registrarTransaccion(api, servicio, {
      refId: refCompra,
      uidUsuario: pagadora.claims.uid,
      monto: '49900.00',
      moneda: 'cop',
      concepto: 'compra-paquete-creditos',
      resultado: 'APROBADO',
      comprobanteUrl: `https://comprobantes.nexus.test/${refCompra}.pdf`,
      pasarelaRefExterna: `psp-${marca}`,
    });

    expect(respuesta.status(), await respuesta.text()).toBe(201);
    const asiento = await respuesta.json();

    expect(asiento.refId).toBe(refCompra);
    expect(Number(asiento.monto)).toBe(49900);
    // La ficha pide la moneda; el servicio la normaliza a mayusculas.
    expect(asiento.moneda).toBe('COP');
    expect(asiento.concepto).toBe('compra-paquete-creditos');
    expect(asiento.resultado).toBe('APROBADO');
    expect(asiento.comprobanteUrl).toContain(`${refCompra}.pdf`);
    expect(asiento.id, 'el asiento se persiste con identidad propia').toBeTruthy();
    expect(asiento.creado, 'queda fechado').toBeTruthy();
  });

  test('CA-02 · la duena lo ve en su historial, y otra jugadora no', async () => {
    const mio = await historialDe(api, pagadora);
    const encontrado = mio.find((t) => t.refId === refCompra);
    expect(encontrado, 'la transaccion no aparece en el historial de su duena').toBeTruthy();
    expect(Number(encontrado.monto)).toBe(49900);
    expect(encontrado.moneda).toBe('COP');
    expect(encontrado.resultado).toBe('APROBADO');

    // El registro es del usuario, no del sistema: nadie mas lo lista.
    const deOtra = await historialDe(api, ajena);
    expect(
      deOtra.some((t) => t.refId === refCompra),
      'el historial de otra jugadora no debe incluir transacciones ajenas',
    ).toBe(false);
  });

  test('CA-03 · estado indeterminado queda marcado, y la referencia repetida se rechaza sin dejar nada a medias', async () => {
    // Una transaccion que la pasarela deja en el aire se registra igual, con
    // su resultado, para que exista y se pueda conciliar a mano: lo que no
    // puede pasar es que se pierda.
    const dudosa = await registrarTransaccion(api, servicio, {
      refId: refDudosa,
      uidUsuario: pagadora.claims.uid,
      monto: '15000.00',
      moneda: 'COP',
      concepto: 'compra-cofre',
      resultado: 'INDETERMINADO',
      comprobanteUrl: null,
      pasarelaRefExterna: `psp-dudoso-${marca}`,
    });
    expect(dudosa.status(), await dudosa.text()).toBe(201);
    expect((await dudosa.json()).resultado).toBe('INDETERMINADO');

    const antes = await historialDe(api, pagadora);
    const cuantasDudosas = antes.filter((t) => t.refId === refDudosa).length;
    expect(cuantasDudosas, 'la indeterminada queda registrada una vez').toBe(1);

    // Reenviar la MISMA referencia (reintento de la pasarela) se rechaza con
    // problem details, no con un 500 ni con un duplicado silencioso.
    const repetida = await registrarTransaccion(api, servicio, {
      refId: refDudosa,
      uidUsuario: pagadora.claims.uid,
      monto: '99999.00',
      moneda: 'COP',
      concepto: 'intento-duplicado',
      resultado: 'APROBADO',
      comprobanteUrl: null,
      pasarelaRefExterna: `psp-dudoso-${marca}`,
    });
    expect(repetida.status(), await repetida.text()).toBe(409);
    const problema = await repetida.json();
    expect(problema.type).toMatch(/transaccion-ya-registrada$/);
    expect(problema.refId).toBe(refDudosa);

    // Y nada cambio: sigue habiendo una sola, con su monto y su estado.
    const despues = await historialDe(api, pagadora);
    const dudosas = despues.filter((t) => t.refId === refDudosa);
    expect(dudosas.length, 'el rechazo no debe crear un segundo asiento').toBe(1);
    expect(Number(dudosas[0].monto), 'el rechazo no debe pisar el monto original').toBe(15000);
    expect(dudosas[0].resultado).toBe('INDETERMINADO');
  });

  test('CA-04 · el registro es permanente: se vuelve a consultar y sigue igual', async () => {
    const historial = await historialDe(api, pagadora);
    const compra = historial.find((t) => t.refId === refCompra);
    const dudosa = historial.find((t) => t.refId === refDudosa);

    expect(compra, 'la compra sigue trazada').toBeTruthy();
    expect(dudosa, 'la indeterminada sigue trazada').toBeTruthy();
    expect(Number(compra.monto)).toBe(49900);
    expect(compra.resultado).toBe('APROBADO');
    expect(dudosa.resultado).toBe('INDETERMINADO');

    // Un token de servicio NO es un usuario: el historial personal no es suyo.
    // (Regla de la cadena de seguridad de ms-finanzas; aqui se comprueba que
    // sigue puesta, porque CA-04 habla del registro del usuario.)
    const conServicio = await api.get(`${FINANZAS}/transacciones/mi-historial`, {
      headers: conToken(servicio),
    });
    expect([401, 403]).toContain(conServicio.status());
  });
});
