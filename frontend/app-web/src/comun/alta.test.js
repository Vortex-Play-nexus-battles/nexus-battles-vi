/**
 * El alta del jugador desde la interfaz — R17.
 */

import { jest } from '@jest/globals';

import {
  ESTADOS_ALTA,
  FalloDelAlta,
  consultarAlta,
  destinoTrasEntrar,
  reintentarAlta,
} from './alta.js';

const BASE = 'http://localhost:8099/frontend/app-web/src/comun/alta.js';
const INICIO = 'http://localhost:8099/frontend/app-web/src/cuentas/index.html';
const PREPARANDO = 'http://localhost:8099/frontend/app-web/src/cuentas/preparando.html';

function respuesta(status, cuerpo = {}) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(cuerpo),
  });
}

describe('consultarAlta', () => {
  test('pide el estado sin caché y devuelve lo que dice el servidor', async () => {
    const alta = { estado: ESTADOS_ALTA.EN_PROCESO, listo: false, pasos: [] };
    const fetchImpl = jest.fn(() => respuesta(200, alta));

    await expect(consultarAlta({ fetchImpl })).resolves.toEqual(alta);
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/auth/onboarding', {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
  });

  test.each([401, 403])('un %i es que la sesión ya no vale', async (status) => {
    await expect(consultarAlta({ fetchImpl: () => respuesta(status) })).rejects.toEqual(
      expect.objectContaining({ tipo: 'sin-sesion', estado: status }),
    );
  });

  test('un 5xx o la red caída NO son una sesión caducada', async () => {
    await expect(consultarAlta({ fetchImpl: () => respuesta(502) })).rejects.toEqual(
      expect.objectContaining({ tipo: 'no-disponible', estado: 502 }),
    );
    const sinRed = consultarAlta({ fetchImpl: () => Promise.reject(new TypeError('red')) });
    await expect(sinRed).rejects.toBeInstanceOf(FalloDelAlta);
    await expect(sinRed).rejects.toEqual(expect.objectContaining({ tipo: 'no-disponible' }));
  });
});

describe('reintentarAlta', () => {
  test('pide otro intento con un POST y devuelve el estado', async () => {
    const fetchImpl = jest.fn(() => respuesta(202, { estado: ESTADOS_ALTA.EN_PROCESO }));
    await expect(reintentarAlta({ fetchImpl })).resolves.toEqual({
      estado: ESTADOS_ALTA.EN_PROCESO,
    });
    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/auth/onboarding/reintentos', {
      method: 'POST',
      headers: { Accept: 'application/json' },
    });
  });

  test('traduce los fallos igual que la consulta', async () => {
    await expect(reintentarAlta({ fetchImpl: () => respuesta(401) })).rejects.toEqual(
      expect.objectContaining({ tipo: 'sin-sesion' }),
    );
    await expect(
      reintentarAlta({ fetchImpl: () => Promise.reject(new Error('red')) }),
    ).rejects.toEqual(expect.objectContaining({ tipo: 'no-disponible' }));
  });
});

describe('destinoTrasEntrar', () => {
  test('una cuenta con el alta sin terminar pasa por la preparación, con la vuelta', () => {
    const url = new URL(
      destinoTrasEntrar({ onboardingListo: false }, '/frontend/app-web/src/tienda.html?x=1', BASE),
    );
    expect(`${url.origin}${url.pathname}`).toBe(PREPARANDO);
    expect(url.searchParams.get('volver')).toBe('/frontend/app-web/src/tienda.html?x=1');
  });

  test('sin vuelta, la preparación lleva al inicio al terminar', () => {
    const url = new URL(destinoTrasEntrar({ onboardingListo: false }, null, BASE));
    expect(`${url.origin}${url.pathname}`).toBe(PREPARANDO);
    expect(url.searchParams.get('volver')).toBe('/frontend/app-web/src/cuentas/index.html');
  });

  test('una cuenta lista va directa a donde iba, o a su inicio', () => {
    expect(destinoTrasEntrar({ onboardingListo: true }, null, BASE)).toBe(INICIO);
    expect(destinoTrasEntrar({ onboardingListo: true }, '/x.html', BASE)).toBe(
      'http://localhost:8099/x.html',
    );
  });

  test('una respuesta sin el campo (servidor anterior a R17) no manda a preparar nada', () => {
    expect(destinoTrasEntrar({}, null, BASE)).toBe(INICIO);
    expect(destinoTrasEntrar(null, null, BASE)).toBe(INICIO);
  });
});
