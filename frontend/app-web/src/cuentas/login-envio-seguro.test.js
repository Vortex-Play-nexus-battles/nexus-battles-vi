/**
 * G1 (4-oct) — el login con su HTML de verdad: el botón nace apagado,
 * `login.js` lo enciende cuando ya escucha `submit`, dos envíos seguidos son
 * UNA petición, y la contraseña va en el cuerpo de un POST, nunca en una
 * dirección.
 *
 * Lo que jsdom no reproduce (Enter en un campo, JavaScript desactivado, el
 * guion que tarda en llegar) lo prueba Chromium en
 * tests/e2e/login-sin-envio-nativo.e2e.spec.js.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

const MARCADO = readFileSync(new URL('./login.html', import.meta.url), 'utf8');

/** Cuerpo de una respuesta como la de `fetch`, lo justo para `cuerpoDe`. */
function respuesta(estado, cuerpo) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => JSON.stringify(cuerpo),
  };
}

const pendientes = [];
const fetchFalso = jest.fn(
  () =>
    new Promise((resolver) => {
      pendientes.push(resolver);
    }),
);

let apagadoAntesDelGuion = null;

beforeAll(async () => {
  document.body.innerHTML = new DOMParser().parseFromString(MARCADO, 'text/html').body.innerHTML;
  apagadoAntesDelGuion = document.getElementById('botonEnviar').disabled;
  // jsdom no implementa el acceso por nombre de HTMLFormElement
  // (`formulario.email`), que login.js usa y el navegador sí tiene.
  const form = document.getElementById('formLogin');
  for (const nombre of ['email', 'password']) {
    Object.defineProperty(form, nombre, { get: () => form.elements.namedItem(nombre) });
  }
  globalThis.fetch = fetchFalso;
  await import('./login.js');
});

const formulario = () => document.getElementById('formLogin');
const boton = () => document.getElementById('botonEnviar');
const tranquilo = () => new Promise((resolver) => setTimeout(resolver, 0));

test('el HTML trae el botón apagado y login.js lo enciende cuando ya escucha submit', () => {
  expect(apagadoAntesDelGuion).toBe(true);
  expect(formulario().getAttribute('method')).toBe('post');
  expect(boton().disabled).toBe(false);
  expect(boton().hasAttribute('data-espera-guion')).toBe(false);
  expect(formulario().hasAttribute('data-listo')).toBe(true);
});

test('doble clic y un tercer envío mientras el primero está en vuelo: una sola petición', async () => {
  formulario().email.value = 'ana@nexus.test';
  formulario().password.value = 'Secreta-G1-2026!';

  boton().click();
  boton().click();
  // Un envío que llegara de todos modos (Enter con el botón ya apagado no
  // envía; requestSubmit sí dispara `submit`): lo frena la propia vista.
  formulario().requestSubmit();
  await tranquilo();

  expect(fetchFalso).toHaveBeenCalledTimes(1);
  const [ruta, opciones] = fetchFalso.mock.calls[0];
  expect(String(ruta)).toMatch(/\/auth\/login$/);
  expect(String(ruta)).not.toMatch(/Secreta|password/);
  expect(opciones.method).toBe('POST');
  expect(JSON.parse(opciones.body)).toEqual({
    email: 'ana@nexus.test',
    password: 'Secreta-G1-2026!',
  });
  expect(boton().disabled).toBe(true);
});

test('si el servidor rechaza, el botón vuelve y se puede reintentar', async () => {
  pendientes.shift()(
    respuesta(401, {
      type: 'https://nexusbattles.upb.edu.co/errors/credenciales-invalidas',
      title: 'Credenciales inválidas',
      status: 401,
    }),
  );
  await tranquilo();
  await tranquilo();

  expect(boton().disabled).toBe(false);

  boton().click();
  await tranquilo();
  expect(fetchFalso).toHaveBeenCalledTimes(2);
});
