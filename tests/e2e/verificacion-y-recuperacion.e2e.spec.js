/**
 * B1 · Verificación del correo y recuperación de la cuenta, de punta a punta.
 *
 * Contra los servicios de verdad del banco (`compose.yml`): la interfaz real
 * detrás del borde, ms-identidad con la cuenta pendiente de verificar
 * (identidad 2.0.0), el servicio de correo entregando en Mailpit, y la API de
 * Mailpit para leer lo que le llega a la persona. Nada simulado.
 *
 * Lo que se demuestra (7.4.12, 7.1.1 y el feedback del profesor: «se registra
 * un correo que no existe y se sigue usando la cuenta»):
 *
 *   1. una cuenta recién registrada NO entra: el login responde 403
 *      `cuenta-no-verificada`, y la vista lo explica con su salida;
 *   2. el código llega al buzón, el enlace del correo rellena la verificación
 *      y confirmarlo activa la cuenta; entonces sí entra, y el mismo código
 *      no sirve dos veces;
 *   3. pedir otro código dice lo mismo exista o no la cuenta;
 *   4. la recuperación dice lo mismo exista o no la cuenta; sin preguntas
 *      configuradas basta el código; la contraseña vieja deja de valer y el
 *      código no se reutiliza;
 *   5. con preguntas configuradas desde «Mi cuenta», la recuperación las
 *      exige: con respuestas incorrectas no cambia nada, con las correctas sí.
 *
 * Serie a propósito: es la historia de una sola cuenta.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

import { correosVistos, enlaceEnElEntorno, esperarCodigo } from './ayudantes/correo.js';
import {
  confirmarCodigo,
  iniciarSesion,
  pedirOtroCodigo,
  verificarDesdeLaVista,
} from './ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const CLAVE = 'Verifica-Tu-Correo-2026';
const CLAVE_RECUPERADA = 'Recuperada-Una-Vez-2026';
const CLAVE_FINAL = 'Recuperada-Con-Preguntas-2026';
const ACEPTA = 'application/problem+json, application/json, text/plain';
const MENSAJE_DE_SOLICITUD = 'Si existe una cuenta asociada, recibirás instrucciones.';

const EN_VERIFICAR = /\/verificar(?:[?#]|$)/;
const EN_LOGIN = /\/login(?:[?#]|$)/;
const EN_RESTABLECER = /\/restablecer(?:[?#]|$)/;
const EN_PREPARANDO_O_INICIO = /\/(?:preparando|inicio)(?:[?#]|$)/;

/**
 * ms-identidad espacia las solicitudes de recuperación de una misma cuenta
 * (`identidad.recuperacion.segundos-entre-solicitudes`, 60 s en DEV y en este
 * banco) y dentro de esa ventana responde lo mismo pero no envía nada. La
 * prueba 10 pide la segunda recuperación de la cuenta: espera a que pase la
 * ventana en vez de relajar la regla en el banco. Un segundo de margen.
 */
const VENTANA_ENTRE_SOLICITUDES_MS = 61_000;

const PREGUNTAS = [
  { texto: '¿Cómo se llamaba el primer héroe que equipaste?', respuesta: 'Guerrero Tanque' },
  { texto: '¿En qué ciudad jugaste tu primer torneo?', respuesta: 'Bucaramanga' },
];

function nuevaCuenta() {
  const marca = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4)}`;
  const apodo = `verif_${marca}`;
  return { apodo, email: `${apodo}@nexus.test` };
}

test.describe('B1 · verificación del correo y recuperación', () => {
  // Cada prueba espera correos: la entrega es asíncrona (correo 1.4.0).
  test.describe.configure({ mode: 'serial', timeout: 180_000 });

  const cuenta = nuevaCuenta();
  let api;
  let codigoDeVerificacion;
  let codigoDeRecuperacion;
  let ultimaSolicitudDeRecuperacion = 0;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: BORDE });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  test('1 · se registra por la vista y la cuenta nace pendiente: a «Confirma tu correo»', async ({
    page,
  }) => {
    await page.goto(`${BORDE}/registro`);
    await page.fill('#nombres', 'Verónica');
    await page.fill('#apellidos', 'De Prueba');
    await page.fill('#apodo', cuenta.apodo);
    await page.fill('#email', cuenta.email);
    await page.fill('#password', CLAVE);
    await page.fill('#confirmarPassword', CLAVE);
    await page.click('#botonEnviar');

    await page.waitForURL(EN_VERIFICAR, { timeout: 30_000 });
    // El correo viaja en la pestaña, nunca en la dirección.
    expect(page.url()).not.toContain(cuenta.apodo);
    expect(new URL(page.url()).searchParams.get('motivo')).toBe('registro');
    await expect(page.locator('[data-zona="introduccion"]')).toContainText(cuenta.email);
    await expect(page.locator('#email')).toHaveValue(cuenta.email);
    // El código acaba de salir: pedir otro espera su minuto, y el botón lo dice.
    const reenviar = page.locator('[data-accion="reenviar"]');
    await expect(reenviar).toBeDisabled();
    await expect(reenviar).toHaveText(/Reenviar código \(\d+ s\)/);
  });

  test('2 · sin confirmar el correo no entra: 403 cuenta-no-verificada, y la vista ofrece confirmarlo', async ({
    page,
  }) => {
    const login = await iniciarSesion(api, cuenta.email, CLAVE);
    expect(login.estado, login.texto).toBe(403);
    expect(login.tipo).toBe('cuenta-no-verificada');
    // Con una contraseña equivocada, el 401 de siempre: el estado de la
    // cuenta no se revela a quien no la conoce.
    const mala = await iniciarSesion(api, cuenta.email, 'No-Es-La-Clave-2026');
    expect(mala.estado).toBe(401);

    await page.goto(`${BORDE}/login`);
    await page.fill('#email', cuenta.email);
    await page.fill('#password', CLAVE);
    await page.click('#botonEnviar');
    const rechazo = page.locator('[data-zona="rechazo"]');
    await expect(rechazo).toContainText('Falta confirmar tu correo');
    const confirmar = rechazo.getByRole('link', { name: 'Confirmar mi correo' });
    await expect(confirmar).toBeFocused();
    await confirmar.click();
    await page.waitForURL(EN_VERIFICAR, { timeout: 20_000 });
    expect(new URL(page.url()).searchParams.get('motivo')).toBe('login');
    // El login dejó el correo en la pestaña para que la verificación lo traiga.
    await expect(page.locator('#email')).toHaveValue(cuenta.email);
  });

  test('3 · el enlace del correo rellena la verificación; confirmarla activa la cuenta y entra', async ({
    page,
  }) => {
    await page.goto(`${BORDE}/verificar`);
    const { codigo, enlace } = await verificarDesdeLaVista(page, {
      email: cuenta.email,
      base: BORDE,
      porElEnlace: true,
    });
    codigoDeVerificacion = codigo;
    expect(enlace, 'el correo de verificación trae su enlace (correo 1.4.0)').toBeTruthy();
    expect(new URL(enlace).pathname).toBe('/verificar');

    await expect(page).toHaveURL(EN_LOGIN);
    await expect(page.locator('#avisoMotivo')).toContainText('Tu correo quedó verificado');
    await expect(page.locator('#email')).toHaveValue(cuenta.email);
    await page.fill('#password', CLAVE);
    await page.click('#botonEnviar');
    // La primera entrada tras verificar pasa por «Preparando tu cuenta»: el
    // alta del jugador empieza al verificar.
    await page.waitForURL(/\/preparando(?:[?#]|$)/, { timeout: 30_000 });

    const login = await iniciarSesion(api, cuenta.email, CLAVE);
    expect(login.estado, login.texto).toBe(200);
  });

  test('4 · el mismo código de verificación no sirve dos veces', async () => {
    const otraVez = await confirmarCodigo(api, cuenta.email, codigoDeVerificacion);
    expect(otraVez.estado).toBe(400);
    expect(otraVez.tipo).toBe('codigo-invalido');
  });

  test('5 · pedir otro código responde lo mismo exista o no una cuenta pendiente', async () => {
    const deLaCuenta = await pedirOtroCodigo(api, cuenta.email);
    const deNadie = await pedirOtroCodigo(api, `nadie.${Date.now()}@nexus.test`);
    expect(deLaCuenta.estado).toBe(202);
    expect(deNadie.estado).toBe(202);
    expect(deNadie.cuerpo).toEqual(deLaCuenta.cuerpo);
  });

  test('6 · pedir la recuperación dice lo mismo exista o no la cuenta', async ({ page }) => {
    const noExiste = await api.post('/api/v1/auth/restablecer/solicitar', {
      headers: { Accept: ACEPTA },
      data: { email: `nadie.${Date.now()}@nexus.test` },
    });
    expect(noExiste.status()).toBe(200);

    await page.goto(`${BORDE}/frontend/app-web/src/cuentas/restablecer-solicitar.html`);
    const aviso = page.locator('[data-zona="aviso"]');
    const solicitar = async (email) => {
      await page.fill('#email', email);
      const [respuesta] = await Promise.all([
        page.waitForResponse((r) => r.url().includes('/api/v1/auth/restablecer/solicitar')),
        page.click('#botonEnviar'),
      ]);
      expect(respuesta.status()).toBe(200);
      await expect(aviso).toContainText(MENSAJE_DE_SOLICITUD);
      return (await aviso.textContent()).trim();
    };

    const conCuenta = await solicitar(cuenta.email);
    ultimaSolicitudDeRecuperacion = Date.now();
    // La interfaz dice exactamente lo mismo para un correo que no existe.
    const sinCuenta = await solicitar(`nadie.${Date.now()}@nexus.test`);
    expect(sinCuenta).toBe(conCuenta);
    // Y ofrece seguir con el código.
    await expect(page.locator('[data-zona="escribir-codigo"]')).toBeVisible();
  });

  test('7 · sin preguntas configuradas basta el código: la contraseña cambia y la vieja deja de valer', async ({
    page,
  }) => {
    const correo = await esperarCodigo(cuenta.email, { tipo: 'recuperacion', base: BORDE });
    codigoDeRecuperacion = correo.codigo;

    // Paso intermedio de la API: sin preguntas configuradas, lista vacía.
    const preguntas = await api.post('/api/v1/auth/restablecer/preguntas', {
      headers: { Accept: ACEPTA },
      data: { email: cuenta.email, codigo: codigoDeRecuperacion },
    });
    expect(preguntas.status(), await preguntas.text()).toBe(200);
    expect(await preguntas.json()).toEqual({ configuradas: false, preguntas: [] });

    // Por la vista, escribiendo el código como lo copiaría una persona.
    await page.goto(`${BORDE}/restablecer`);
    await page.fill('#email', cuenta.email);
    await page.locator('#codigo').evaluate((el, valor) => {
      el.value = valor.toLowerCase().replace(/(.{4})/, '$1-');
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }, codigoDeRecuperacion);
    await page.click('[data-accion="continuar"]');
    await expect(page.locator('#formClave')).toBeVisible();
    await expect(page.locator('[data-zona="preguntas"]')).toBeHidden();
    await page.fill('#nuevaPassword', CLAVE_RECUPERADA);
    await page.fill('#confirmarPassword', CLAVE_RECUPERADA);
    await page.click('[data-accion="guardar"]');

    await page.waitForURL(/[?&]motivo=restablecida/, { timeout: 30_000 });
    await expect(page.locator('#avisoMotivo')).toContainText('contraseña nueva');
    await expect(page.locator('#email')).toHaveValue(cuenta.email);

    expect((await iniciarSesion(api, cuenta.email, CLAVE_RECUPERADA)).estado).toBe(200);
    expect((await iniciarSesion(api, cuenta.email, CLAVE)).estado).toBe(401);
  });

  test('8 · el mismo código de recuperación no sirve dos veces', async () => {
    const otraVez = await api.post('/api/v1/auth/restablecer/confirmar', {
      headers: { Accept: ACEPTA },
      data: {
        email: cuenta.email,
        codigo: codigoDeRecuperacion,
        nuevaPassword: 'Otra-Vez-El-Mismo-2026',
      },
    });
    expect(otraVez.status()).toBe(400);
    expect((await otraVez.json()).type).toMatch(/\/codigo-invalido$/);
  });

  test('9 · configura dos preguntas propias desde «Mi cuenta»', async ({ page }) => {
    await page.goto(`${BORDE}/login`);
    await page.fill('#email', cuenta.email);
    await page.fill('#password', CLAVE_RECUPERADA);
    await page.click('#botonEnviar');
    await page.waitForURL(EN_PREPARANDO_O_INICIO, { timeout: 30_000 });

    await page.goto(`${BORDE}/cuenta`);
    await page.locator('#pestana-seguridad').click();
    const seccion = page.locator('[data-zona="preguntas-seguridad"]');
    await expect(seccion.locator('[data-zona="estado-preguntas"]')).toContainText(
      'Todavía no configuraste',
      { timeout: 20_000 },
    );
    await seccion.locator('[data-accion="configurar-preguntas"]').click();
    for (const [indice, { texto, respuesta }] of PREGUNTAS.entries()) {
      await page.fill(`#preguntaSeguridad${indice + 1}`, texto);
      await page.fill(`#respuestaSeguridad${indice + 1}`, respuesta);
    }
    await page.fill('#passwordActualPreguntas', CLAVE_RECUPERADA);
    await seccion.locator('[data-accion="guardar-preguntas"]').click();

    await expect(seccion.locator('[data-zona="aviso-preguntas"]')).toContainText(
      'quedaron guardadas',
    );
    await expect(seccion.locator('[data-zona="lista-preguntas"] li')).toHaveText(
      PREGUNTAS.map((p) => p.texto),
    );
    // Ni la contraseña ni las respuestas se quedan escritas.
    await expect(page.locator('#passwordActualPreguntas')).toHaveValue('');
  });

  test('10 · con preguntas, la recuperación las exige: mal contestadas no cambia nada; bien, sí', async ({
    page,
  }) => {
    // Segunda recuperación de la cuenta: fuera de la ventana de la prueba 6.
    const espera = ultimaSolicitudDeRecuperacion + VENTANA_ENTRE_SOLICITUDES_MS - Date.now();
    if (espera > 0) {
      await new Promise((resolver) => setTimeout(resolver, espera));
    }
    const vistos = await correosVistos(cuenta.email, { base: BORDE });
    const solicitud = await api.post('/api/v1/auth/restablecer/solicitar', {
      headers: { Accept: ACEPTA },
      data: { email: cuenta.email },
    });
    expect(solicitud.status()).toBe(200);
    const correo = await esperarCodigo(cuenta.email, {
      tipo: 'recuperacion',
      ignorar: vistos,
      base: BORDE,
    });

    // La API da las preguntas (nunca las respuestas) a cambio del código.
    const consulta = await api.post('/api/v1/auth/restablecer/preguntas', {
      headers: { Accept: ACEPTA },
      data: { email: cuenta.email, codigo: correo.codigo },
    });
    expect(consulta.status(), await consulta.text()).toBe(200);
    const { configuradas, preguntas } = await consulta.json();
    expect(configuradas).toBe(true);
    expect(preguntas.map((p) => p.texto)).toEqual(PREGUNTAS.map((p) => p.texto));
    expect(JSON.stringify(preguntas)).not.toContain(PREGUNTAS[0].respuesta);

    // Por el enlace del correo, como lo abriría una persona.
    await page.goto(enlaceEnElEntorno(correo.enlace ?? `${BORDE}/restablecer`, BORDE));
    await page.waitForURL(EN_RESTABLECER);
    if (!correo.enlace) {
      await page.fill('#email', cuenta.email);
      await page.fill('#codigo', correo.codigo);
    }
    await page.click('[data-accion="continuar"]');
    const campos = page.locator('[data-zona="lista-preguntas"] input');
    await expect(campos).toHaveCount(PREGUNTAS.length);
    await expect(page.locator('[data-zona="lista-preguntas"] label')).toHaveText(
      PREGUNTAS.map((p) => p.texto),
    );

    // Mal contestadas: el servicio lo rechaza y la contraseña no cambia.
    await campos.nth(0).fill('no es');
    await campos.nth(1).fill('tampoco');
    await page.fill('#nuevaPassword', CLAVE_FINAL);
    await page.fill('#confirmarPassword', CLAVE_FINAL);
    await page.click('[data-accion="guardar"]');
    await expect(page.locator('[data-zona="aviso"]')).toContainText('Alguna respuesta no coincide');
    await expect(campos.nth(0)).toBeFocused();
    expect((await iniciarSesion(api, cuenta.email, CLAVE_FINAL)).estado).toBe(401);

    // Bien contestadas (sin importar mayúsculas ni tildes): cambia.
    await campos.nth(0).fill(PREGUNTAS[0].respuesta.toUpperCase());
    await campos.nth(1).fill(PREGUNTAS[1].respuesta);
    await page.click('[data-accion="guardar"]');
    await page.waitForURL(/[?&]motivo=restablecida/, { timeout: 30_000 });

    expect((await iniciarSesion(api, cuenta.email, CLAVE_FINAL)).estado).toBe(200);
    expect((await iniciarSesion(api, cuenta.email, CLAVE_RECUPERADA)).estado).toBe(401);

    // Y ese código tampoco sirve dos veces, ni con las respuestas buenas.
    const otraVez = await api.post('/api/v1/auth/restablecer/confirmar', {
      headers: { Accept: ACEPTA },
      data: {
        email: cuenta.email,
        codigo: correo.codigo,
        respuestas: preguntas.map((p, i) => ({
          preguntaId: p.id,
          respuesta: PREGUNTAS[i].respuesta,
        })),
        nuevaPassword: 'Otra-Vez-El-Mismo-2026',
      },
    });
    expect(otraVez.status()).toBe(400);
  });
});
