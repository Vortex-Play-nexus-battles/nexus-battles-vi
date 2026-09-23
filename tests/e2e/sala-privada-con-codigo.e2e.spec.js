/**
 * RF-JUE-002 · Una sala privada se puede usar — FI-R4.
 *
 * Con el navegador de verdad, contra los servicios de verdad: la anfitriona
 * crea una sala privada, ve su codigo en la sala de espera, y el invitado
 * necesita ese codigo para entrar. Los cuatro pasos que pedia el programa, en
 * orden: A crea, B sin codigo es rechazado, B con codigo equivocado es
 * rechazado, B con el codigo correcto entra.
 *
 * ## Por que esta prueba no existia
 *
 * El mecanismo del servidor esta completo desde la migracion V5: columna
 * `codigo_invitacion`, generacion con `SecureRandom` sobre un alfabeto sin O,
 * 0, I ni 1, y comparacion que tolera mayusculas, espacios y guiones. Lo que
 * faltaba estaba en el frontend: `ingresarASala` hacia `POST` **sin cuerpo**,
 * asi que el agregado recibia `null` y rechazaba con 403 a todo el mundo. Una
 * sala privada era, por construccion, una sala a la que no podia entrar nadie
 * salvo su anfitrion, y ninguna prueba lo notaba porque ninguna intentaba
 * entrar a una.
 *
 * El codigo tampoco llegaba a ninguna pantalla: `GET /salas/{id}` lo devuelve
 * solo al anfitrion y el frontend no lo leia en ningun sitio.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const ANFITRION = process.env.E2E_ANFITRION ?? 'anfitriona_e2e';
const INVITADO = process.env.E2E_INVITADO ?? 'invitado_e2e';
const CLAVE = 'Contrasena-E2E-2026';
const SALA = '/frontend/app-web/src/plataforma/salas-partidas/sala-batalla.html';
const LISTADO = '/frontend/app-web/src/plataforma/salas-partidas/batallas.html';

async function sesionDe(api, apodo) {
  const email = `${apodo}@nexus.test`;
  const registro = await api.post('/api/v1/auth/registro', {
    multipart: { nombres: 'Jugadora', apellidos: 'De Prueba', email, password: CLAVE, apodo },
  });
  expect([200, 201, 400, 409]).toContain(registro.status());
  const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
  expect(login.status(), `login de ${apodo}: ${await login.text()}`).toBe(200);
  return login.json();
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

async function conSesion(page, jugador, apodo) {
  await page.addInitScript(
    ([token, nombre]) => {
      sessionStorage.setItem('nexus.token', token);
      sessionStorage.setItem('nexus.apodoActual', nombre);
    },
    [jugador.token, apodo],
  );
}

test.describe('Sala privada con codigo de invitacion (RF-JUE-002)', () => {
  test.describe.configure({ mode: 'serial' });

  let api;
  let anfitriona;
  let invitado;
  let sala;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
    anfitriona = await sesionDe(api, ANFITRION);
    invitado = await sesionDe(api, INVITADO);
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('A crea una sala privada y el servicio le da un codigo', async () => {
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: {
        maximoParticipantes: 4,
        modalidad: 'HASTA_SEIS',
        recompensaCreditos: 0,
        privada: true,
      },
    });
    expect(creada.status(), `crear sala privada: ${await creada.text()}`).toBe(201);
    sala = await creada.json();

    expect(sala.privada).toBe(true);
    expect(sala.estado).toBe('PRIVADA');
    // El codigo viaja en la respuesta de creacion porque quien crea es el
    // anfitrion (`SalaResponse.paraElAnfitrion`).
    expect(sala.codigoInvitacion, 'la sala privada nace con codigo').toBeTruthy();
    // Ocho caracteres y un guion, sin O, 0, I ni 1: los que se confunden al
    // dictarlos por voz o al copiarlos de una captura.
    expect(sala.codigoInvitacion).toMatch(/^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$/);
  });

  test('a A se le ensena el codigo en la sala de espera, y puede copiarlo', async ({ page }) => {
    await conSesion(page, anfitriona, ANFITRION);
    await page.goto(`${SALA}?sala=${sala.id}`);

    const zona = page.locator('[data-zona="invitacion"]');
    await expect(zona).toBeVisible();
    await expect(page.locator('[data-zona="codigo-invitacion"]')).toHaveText(sala.codigoInvitacion);

    // El boton copia de verdad. Sin permiso de portapapeles el acuse diria que
    // hay que copiarlo a mano, que tambien es la verdad; aqui se concede.
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.locator('[data-accion="copiar-codigo"]').click();
    await expect(page.locator('[data-zona="acuse-copia"]')).toContainText(/copiado/i);
    const copiado = await page.evaluate(() => navigator.clipboard.readText());
    expect(copiado).toBe(sala.codigoInvitacion);
  });

  test('a B, que no es el anfitrion, el servicio NO le manda el codigo', async () => {
    // Es la mitad que hace que el codigo sirva de algo: si viajara a todo el
    // mundo, «privada» seria una etiqueta decorativa.
    const vista = await api.get(`/api/v1/salas/${sala.id}`, {
      headers: conToken(invitado.token),
    });
    expect(vista.status()).toBe(200);
    const cuerpo = await vista.json();
    expect(cuerpo.codigoInvitacion).toBeUndefined();
  });

  test('B sin codigo es rechazado, y la vista le pide el codigo', async ({ page }) => {
    await conSesion(page, invitado, INVITADO);
    await page.goto(`${LISTADO}?sala=${sala.id}`);

    // El enlace sin codigo intenta entrar y recibe el 403 del dominio.
    const formulario = page.locator('[data-zona="pedir-codigo"]');
    await expect(formulario).toBeVisible();
    // Y el listado sigue en pie: no es un callejon sin salida.
    await expect(page.locator('[data-zona="salas"]')).toBeVisible();
  });

  test('B con un codigo equivocado sigue fuera, y se le dice por que', async ({ page }) => {
    await conSesion(page, invitado, INVITADO);

    // Se cuentan los intentos de ingreso con su cuerpo: sin esto, un
    // formulario que no llega a enviarse pasaria la prueba —la ocupacion sigue
    // en uno y el aviso sigue puesto— y el fallo quedaria escondido. La primera
    // version de esta prueba tenia justo ese agujero, y lo destapo CI.
    const envios = [];
    page.on('request', (peticion) => {
      if (peticion.url().includes('/participantes') && peticion.method() === 'POST') {
        envios.push(peticion.postData() ?? '');
      }
    });

    await page.goto(`${LISTADO}?sala=${sala.id}`);

    const formulario = page.locator('[data-zona="pedir-codigo"]');
    await expect(formulario).toBeVisible();

    await page.locator('[name="codigoInvitacion"]').fill('ZZZZ-9999');
    await formulario.locator('button[type="submit"]').click();

    // El codigo llego al servicio de verdad.
    await expect
      .poll(() => envios.filter((cuerpo) => cuerpo.includes('ZZZZ-9999')).length, {
        timeout: 20_000,
      })
      .toBeGreaterThan(0);

    // Sigue pidiendo el codigo, y ahora dice que el que escribio no vale: el
    // mensaje cambia para que no haya duda de si se envio.
    await expect(formulario).toBeVisible();
    await expect(page.locator('[data-zona="aviso-codigo"]')).toContainText(/no vale/i);
    await expect(page).not.toHaveURL(/sala-batalla\.html/);

    const dentro = await api.get(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect((await dentro.json()).ocupacion, 'nadie entro con el codigo malo').toBe(1);
  });

  test('B con el codigo correcto entra a la sala', async ({ page }) => {
    await conSesion(page, invitado, INVITADO);
    await page.goto(`${LISTADO}?sala=${sala.id}`);

    const formulario = page.locator('[data-zona="pedir-codigo"]');
    await expect(formulario).toBeVisible();

    // En minusculas y sin guion, para comprobar que la normalizacion del
    // servidor (`Sala.normalizar`) es la que manda y el cliente no la duplica.
    await page
      .locator('[name="codigoInvitacion"]')
      .fill(sala.codigoInvitacion.replace('-', '').toLowerCase());
    await formulario.locator('button[type="submit"]').click();

    await page.waitForURL(/sala-batalla\.html/, { timeout: 20_000 });

    const dentro = await api.get(`/api/v1/salas/${sala.id}`, {
      headers: conToken(anfitriona.token),
    });
    const cuerpo = await dentro.json();
    expect(cuerpo.ocupacion, 'el invitado entro').toBe(2);
  });

  test('el enlace que copia A entra solo, sin teclear nada', async ({ page }) => {
    // Es para lo que existe el enlace: se pega en un chat y quien lo abre esta
    // dentro. Se prueba con una sala nueva porque el invitado ya esta en la
    // anterior y el servicio rechaza entrar dos veces.
    const creada = await api.post('/api/v1/salas', {
      headers: conToken(anfitriona.token),
      data: {
        maximoParticipantes: 4,
        modalidad: 'HASTA_SEIS',
        recompensaCreditos: 0,
        privada: true,
      },
    });
    expect(creada.status()).toBe(201);
    const otra = await creada.json();

    await conSesion(page, invitado, INVITADO);
    await page.goto(
      `${LISTADO}?sala=${otra.id}&codigo=${encodeURIComponent(otra.codigoInvitacion)}`,
    );

    await page.waitForURL(/sala-batalla\.html/, { timeout: 20_000 });
    await expect(page.locator('[data-zona="pedir-codigo"]')).toBeHidden();

    const dentro = await api.get(`/api/v1/salas/${otra.id}`, {
      headers: conToken(anfitriona.token),
    });
    expect((await dentro.json()).ocupacion).toBe(2);
  });
});
