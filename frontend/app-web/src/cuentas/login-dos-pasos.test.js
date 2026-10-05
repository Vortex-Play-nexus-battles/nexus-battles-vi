/**
 * HU-AUT-007 — el login de verdad (`login.html` + `login.js`) ante las
 * respuestas del contrato 2.2.0, con `fetch` falso:
 *
 *   - un 403 con desafío lleva al segundo paso, sin enseñarlo como rechazo y
 *     sin dejar la contraseña escrita;
 *   - el código viaja en el cuerpo de un POST a /auth/login/segundo-factor,
 *     sin sesión;
 *   - si el desafío caducó, vuelve al primer paso y lo dice;
 *   - un 403 SIN desafío (cuenta suspendida) sigue siendo el rechazo de
 *     siempre, y el 503 del segundo factor tiene su propio mensaje.
 *
 * Lo que jsdom no hace (navegar tras entrar) lo prueba Chromium en
 * tests/e2e/segundo-factor.e2e.spec.js.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

const MARCADO = readFileSync(new URL('./login.html', import.meta.url), 'utf8');
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const DESAFIO = 'kP3v-9xQ_desafio-opaco-de-prueba';

function respuesta(estado, cuerpo) {
  return Promise.resolve({
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => JSON.stringify(cuerpo),
  });
}

const fetchFalso = jest.fn();

beforeAll(async () => {
  document.body.innerHTML = new DOMParser().parseFromString(MARCADO, 'text/html').body.innerHTML;
  // jsdom no implementa el acceso por nombre de HTMLFormElement
  // (`formulario.email`), que login.js usa y el navegador sí tiene.
  const form = document.getElementById('formLogin');
  for (const nombre of ['email', 'password']) {
    Object.defineProperty(form, nombre, { get: () => form.elements.namedItem(nombre) });
  }
  globalThis.fetch = fetchFalso;
  await import('./login.js');
});

beforeEach(() => {
  fetchFalso.mockReset();
});

const $ = (selector) => document.querySelector(selector);
const tranquilo = () => new Promise((resolver) => setTimeout(resolver, 0));

async function entrarConContrasena() {
  $('#formLogin').email.value = 'admin@nexus.test';
  $('#formLogin').password.value = 'Secreta-2FA-2026!';
  $('#botonEnviar').click();
  await tranquilo();
  await tranquilo();
}

test('403 segundo-factor-requerido con desafío: segundo paso, no un rechazo', async () => {
  fetchFalso.mockImplementationOnce(() =>
    respuesta(403, {
      type: `${ERRORES}segundo-factor-requerido`,
      title: 'Falta el segundo factor',
      status: 403,
      detail: 'Escribe el código de tu aplicación de autenticación.',
      desafio: DESAFIO,
      expiraEn: '2026-10-05T15:05:00Z',
    }),
  );
  await entrarConContrasena();

  expect(fetchFalso).toHaveBeenCalledTimes(1);
  expect($('[data-zona="segundo-paso"]').hidden).toBe(false);
  expect($('#formLogin').hidden).toBe(true);
  expect($('[data-zona="rechazo"]').hidden).toBe(true);
  expect($('#formLogin').password.value).toBe('');
  expect(document.activeElement).toBe($('#codigoSegundoPaso'));
});

test('el código va en el cuerpo de un POST sin sesión; si el desafío caducó, vuelve a la contraseña', async () => {
  fetchFalso.mockImplementationOnce(() =>
    respuesta(401, {
      type: `${ERRORES}desafio-invalido`,
      title: 'La verificación caducó',
      status: 401,
      detail: 'Tu verificación caducó o ya no es válida.',
    }),
  );
  $('#codigoSegundoPaso').value = '287082';
  $('[data-accion="verificar-segundo-paso"]').click();
  await tranquilo();
  await tranquilo();

  expect(fetchFalso).toHaveBeenCalledTimes(1);
  const [ruta, opciones] = fetchFalso.mock.calls[0];
  expect(String(ruta)).toMatch(/\/api\/v1\/auth\/login\/segundo-factor$/);
  expect(String(ruta)).not.toContain('287082');
  expect(opciones.method).toBe('POST');
  expect(opciones.headers.Authorization).toBeUndefined();
  expect(JSON.parse(opciones.body)).toEqual({ desafio: DESAFIO, codigo: '287082' });

  expect($('[data-zona="segundo-paso"]').hidden).toBe(true);
  expect($('#formLogin').hidden).toBe(false);
  expect($('[data-zona="rechazo"]').textContent).toMatch(/Tu verificación caducó/);
  expect($('#botonEnviar').disabled).toBe(false);
  expect(globalThis.sessionStorage.length).toBe(0);
});

test('regresión: un 403 de cuenta suspendida (sin desafío) sigue siendo el rechazo de siempre', async () => {
  fetchFalso.mockImplementationOnce(() =>
    respuesta(403, {
      type: `${ERRORES}cuenta-suspendida`,
      title: 'Cuenta suspendida',
      status: 403,
      detail: 'Cuenta suspendida.',
      suspendidoHasta: '2099-10-05T15:00:00Z',
    }),
  );
  await entrarConContrasena();
  expect($('[data-zona="segundo-paso"]').hidden).toBe(true);
  expect($('[data-zona="rechazo"]').textContent).toMatch(/Tu cuenta está suspendida/);
});

test('503 segundo-factor-no-disponible: su propio mensaje, sin segundo paso', async () => {
  fetchFalso.mockImplementationOnce(() =>
    respuesta(503, {
      type: `${ERRORES}segundo-factor-no-disponible`,
      title: 'Segundo factor no disponible',
      status: 503,
    }),
  );
  await entrarConContrasena();
  expect($('[data-zona="segundo-paso"]').hidden).toBe(true);
  expect($('[data-zona="enrolamiento-obligatorio"]').hidden).toBe(true);
  expect($('[data-zona="rechazo"]').textContent).toMatch(
    /No podemos comprobar tu verificación en dos pasos ahora mismo/,
  );
});

test('403 segundo-factor-enrolamiento-requerido: guía la activación antes de entrar', async () => {
  fetchFalso.mockImplementationOnce(() =>
    respuesta(403, {
      type: `${ERRORES}segundo-factor-enrolamiento-requerido`,
      title: 'Hay que activar el segundo factor',
      status: 403,
      desafio: DESAFIO,
      expiraEn: '2026-10-05T15:05:00Z',
    }),
  );
  await entrarConContrasena();
  expect($('[data-zona="enrolamiento-obligatorio"]').hidden).toBe(false);
  expect($('[data-zona="segundo-paso"]').hidden).toBe(true);
  expect($('#formLogin').hidden).toBe(true);

  fetchFalso.mockImplementationOnce(() =>
    respuesta(200, {
      secreto: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
      uriOtpauth:
        'otpauth://totp/Nexus%20Battles%20VI:admin%40nexus.test?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
      emisor: 'Nexus Battles VI',
      cuenta: 'admin@nexus.test',
    }),
  );
  $('[data-accion="generar-clave"]').click();
  await tranquilo();
  await tranquilo();
  const [ruta, opciones] = fetchFalso.mock.calls[1];
  expect(String(ruta)).toMatch(/\/api\/v1\/auth\/login\/segundo-factor\/enrolamiento$/);
  expect(JSON.parse(opciones.body)).toEqual({ desafio: DESAFIO });
  expect($('[data-zona="enrolamiento-obligatorio"] [data-zona="secreto"]').textContent).toMatch(
    /^JBSW/,
  );
});
