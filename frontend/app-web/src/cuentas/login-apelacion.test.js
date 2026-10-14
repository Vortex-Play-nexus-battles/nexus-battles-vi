/**
 * SA-4 (§7.3.2) — el login con su HTML de verdad cuando la cuenta está
 * baneada: con la contraseña correcta, el 403 trae el código para apelar y
 * la vista ofrece «Apelar esta sanción», que abre la apelación ahí mismo,
 * sin sesión. La contraseña no se queda en el formulario y el código no sale
 * de la memoria de la página.
 */

import { readFileSync } from 'node:fs';

import { jest } from '@jest/globals';

const MARCADO = readFileSync(new URL('./login.html', import.meta.url), 'utf8');
const ERRORES = 'https://nexusbattles.upb.edu.co/errors/';
const CODIGO = 'q3V9Yb0n2xk7r8T1mW4cJ6pA5sD0fG2hL9zX3vB7nQe';

function respuesta(estado, cuerpo) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    text: async () => JSON.stringify(cuerpo),
  };
}

const fetchFalso = jest.fn(async (ruta) => {
  if (String(ruta).endsWith('/auth/login')) {
    return respuesta(403, {
      type: `${ERRORES}cuenta-baneada`,
      title: 'Cuenta baneada',
      status: 403,
      detail: 'Esta cuenta ha sido baneada permanentemente.',
      apelacion: { codigo: CODIGO, expiraEn: '2026-10-14T15:30:00Z' },
    });
  }
  if (String(ruta).endsWith('/auth/apelacion/consulta')) {
    return respuesta(200, {
      sancion: {
        id: 'aaaaaaaa-0000-4000-8000-000000000001',
        tipo: 'BANEO',
        causal: 'FRAUDE',
        motivo: 'Duplicó créditos',
        politica: null,
        emitidaEn: '2026-10-10T15:00:00Z',
        vigenteHasta: null,
      },
      apelableHasta: '2026-11-09T15:00:00Z',
      puedeApelar: true,
      motivoNoApelable: null,
      apelacion: null,
    });
  }
  return respuesta(404, {});
});

beforeAll(async () => {
  document.body.innerHTML = new DOMParser().parseFromString(MARCADO, 'text/html').body.innerHTML;
  const form = document.getElementById('formLogin');
  for (const nombre of ['email', 'password']) {
    Object.defineProperty(form, nombre, { get: () => form.elements.namedItem(nombre) });
  }
  globalThis.fetch = fetchFalso;
  await import('./login.js');
});

const tranquilo = () => new Promise((resolver) => setTimeout(resolver, 0));
const $ = (selector) => document.querySelector(selector);

test('la cuenta baneada ve su baneo y la salida para apelarlo; la contraseña ya no está', async () => {
  $('#formLogin').email.value = 'baneada@nexus.test';
  $('#formLogin').password.value = 'Secreta-SA4-2026!';
  $('#botonEnviar').click();
  await tranquilo();
  await tranquilo();

  const rechazo = $('[data-zona="rechazo"]');
  expect(rechazo.hidden).toBe(false);
  expect(rechazo.textContent).toContain('Esta cuenta está inhabilitada de forma definitiva');
  expect($('[data-accion="apelar-sancion"]')).not.toBeNull();
  expect($('#formLogin').password.value).toBe('');
});

test('«Apelar esta sanción» abre la apelación con el código del rechazo, sin sesión', async () => {
  $('[data-accion="apelar-sancion"]').click();
  await tranquilo();
  await tranquilo();

  const consulta = fetchFalso.mock.calls.find(([ruta]) =>
    String(ruta).endsWith('/apelacion/consulta'),
  );
  expect(consulta).toBeDefined();
  expect(JSON.parse(consulta[1].body)).toEqual({ codigo: CODIGO });
  expect(consulta[1].headers.Authorization).toBeUndefined();
  expect($('[data-zona="apelacion"]').hidden).toBe(false);
  expect($('#formLogin').hidden).toBe(true);
  expect($('[data-zona="sancion-apelada"]').textContent).toContain(
    'Intentos de fraude o manipulación del sistema',
  );
  expect(globalThis.sessionStorage.getItem('nexus.token')).toBeNull();
  expect(JSON.stringify({ ...globalThis.sessionStorage })).not.toContain(CODIGO);
});

test('volver a la entrada devuelve el formulario y el foco a la contraseña', () => {
  $('[data-accion="volver-desde-apelacion"]').click();

  expect($('[data-zona="apelacion"]').hidden).toBe(true);
  expect($('#formLogin').hidden).toBe(false);
  expect(document.activeElement).toBe($('#password'));
});
