/**
 * El interceptor avisa cuando un servicio rechaza el token de la sesión — R17.
 *
 * Solo avisa: quien decide si la sesión se acabó es el vigilante, que se lo
 * pregunta al emisor. Aquí se comprueba que el aviso sale cuando toca y,
 * sobre todo, que NO sale cuando no toca (un login con la contraseña mal
 * escrita no es una sesión caducada).
 */

import { jest } from '@jest/globals';

import { CLAVES } from '../sesion.js';
import { EVENTO_RECHAZO } from '../vigilante-sesion.js';
import { fetchWithHttpErrorInterceptor } from './http-error.interceptor.js';

function respuesta(status) {
  return Promise.resolve({
    ok: false,
    status,
    clone() {
      return this;
    },
    json: () => Promise.resolve({ detail: 'rechazado', status }),
  });
}

let avisos;
const escuchar = (evento) => avisos.push(evento.detail);

beforeEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = '';
  avisos = [];
  window.addEventListener(EVENTO_RECHAZO, escuchar);
});

afterEach(() => {
  window.removeEventListener(EVENTO_RECHAZO, escuchar);
});

describe('aviso de credencial rechazada', () => {
  test.each([401, 403])(
    'un %i a una petición con el token de la sesión se avisa',
    async (status) => {
      sessionStorage.setItem(CLAVES.token, 'el-token');
      globalThis.fetch = jest.fn(() => respuesta(status));

      await fetchWithHttpErrorInterceptor('/api/v1/salas');

      expect(avisos).toEqual([{ estado: status, url: '/api/v1/salas' }]);
    },
  );

  test('sin sesión no hay nada que avisar', async () => {
    globalThis.fetch = jest.fn(() => respuesta(401));
    await fetchWithHttpErrorInterceptor('/api/v1/salas');
    expect(avisos).toEqual([]);
  });

  test('si la petición llevaba OTRA credencial (la puso quien llama), no es asunto de la sesión', async () => {
    sessionStorage.setItem(CLAVES.token, 'el-token');
    globalThis.fetch = jest.fn(() => respuesta(401));
    await fetchWithHttpErrorInterceptor('/api/v1/salas', {
      headers: { Authorization: 'Bearer otro-token' },
    });
    expect(avisos).toEqual([]);
  });

  test.each([
    '/api/v1/auth/login',
    '/api/v1/auth/registro',
    '/api/v1/auth/restablecer/confirmar',
    '/api/v1/auth/logout',
  ])('las llamadas de entrada (%s) no hablan de la sesión en curso', async (url) => {
    sessionStorage.setItem(CLAVES.token, 'el-token');
    globalThis.fetch = jest.fn(() => respuesta(401));
    await fetchWithHttpErrorInterceptor(url, { method: 'POST' });
    expect(avisos).toEqual([]);
  });

  test('otros errores (404, 500) no se confunden con un rechazo del token', async () => {
    sessionStorage.setItem(CLAVES.token, 'el-token');
    for (const status of [404, 500, 502]) {
      globalThis.fetch = jest.fn(() => respuesta(status));
      await fetchWithHttpErrorInterceptor('/api/v1/salas');
    }
    expect(avisos).toEqual([]);
  });

  test('funciona también con una instancia de Headers', async () => {
    sessionStorage.setItem(CLAVES.token, 'el-token');
    globalThis.fetch = jest.fn(() => respuesta(401));
    await fetchWithHttpErrorInterceptor('/api/v1/salas', {
      headers: new Headers({ Accept: 'application/json' }),
    });
    expect(avisos).toHaveLength(1);
  });
});
